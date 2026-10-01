package he.mychat.llo.net

import android.content.Context
import he.mychat.llo.data.AppSettings
import he.mychat.llo.data.ChatMessage
import he.mychat.llo.data.ChatTemplate
import he.mychat.llo.data.SettingsRepository
import he.mychat.llo.data.StorageManager
import he.mychat.llo.data.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Calendar

object ChatSender {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val messagesMap = mutableMapOf<String, MutableList<ChatMessage>>()
    private val sendingMap = mutableMapOf<String, Boolean>()
    private val pendingQueues = mutableMapOf<String, ArrayDeque<Long>>()
    private val debugMap = mutableMapOf<String, MutableMap<Long, String>>()

    private val unreadMap = mutableMapOf<String, Int>()

    @Volatile
    var activeFolder: String? = null
        private set

    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    // ============ Active folder ============

    fun setActiveFolder(folder: String?) {
        val prev = activeFolder
        activeFolder = folder
        if (folder != null && (unreadMap[folder] ?: 0) != 0) {
            unreadMap[folder] = 0
            broadcast(folder)
        }
        if (prev != null && prev != folder) {
            broadcast(prev)
        }
    }

    fun getUnread(folder: String): Int = unreadMap[folder] ?: 0

    // ============ 加载 / 查询 ============

    fun loadMessages(context: Context, folder: String): MutableList<ChatMessage> {
        messagesMap[folder]?.let { return it }

        val loaded = StorageManager.readMessages(context, folder)
            .filter { it.role == "user" || it.role == "assistant" }
            .map { m ->
                if (m.role == "user" && m.status == ChatMessage.STATUS_PENDING)
                    m.copy(status = ChatMessage.STATUS_FAILED)
                else m
            }
            .toMutableList()
        messagesMap[folder] = loaded
        StorageManager.writeMessages(context, folder, loaded)
        return loaded
    }

    fun isSending(folder: String): Boolean = sendingMap[folder] == true

    fun debugPayload(folder: String, time: Long): String? =
        debugMap[folder]?.get(time)

    fun clearCache(folder: String) {
        messagesMap.remove(folder)
        sendingMap.remove(folder)
        pendingQueues.remove(folder)
        debugMap.remove(folder)
        unreadMap[folder] = 0
    }

    fun replaceMessages(context: Context, folder: String, list: MutableList<ChatMessage>) {
        messagesMap[folder] = list
        persist(context, folder)
        broadcast(folder)
    }

    // ============ 发送 / 重发 ============

    /**
     * 发送：内部把纯文本包装成 time:...\ndata:...（受 sendWithFormat 控制），
     * 存储和发送给 AI 的都是包装后的原文。
     */
    fun send(context: Context, template: ChatTemplate, text: String) {
        val app = context.applicationContext
        val folder = template.folder
        val list = loadMessages(app, folder)

        val settings = SettingsRepository.get(app)
        val storedContent = if (settings.sendWithFormat) {
            "time:${formatNow()}\ndata:$text"
        } else {
            text
        }

        val userMsg = ChatMessage("user", storedContent, status = ChatMessage.STATUS_PENDING)
        list.add(userMsg)
        persist(app, folder)
        broadcast(folder)

        if (sendingMap[folder] == true) {
            pendingQueues.getOrPut(folder) { ArrayDeque() }.addLast(userMsg.time)
            return
        }
        startSend(app, template, userMsg.time)
    }

    /**
     * 重发：复用存储中的原 content，不再重新包装时间。
     */
    fun retry(context: Context, template: ChatTemplate, time: Long) {
        val app = context.applicationContext
        val folder = template.folder
        val list = loadMessages(app, folder)
        val idx = list.indexOfFirst { it.time == time && it.role == "user" }
        if (idx < 0) return
        if (list[idx].status != ChatMessage.STATUS_FAILED) return

        list[idx] = list[idx].copy(status = ChatMessage.STATUS_PENDING)
        debugMap[folder]?.remove(time)
        persist(app, folder)
        broadcast(folder)

        if (sendingMap[folder] == true) {
            pendingQueues.getOrPut(folder) { ArrayDeque() }.addLast(time)
            return
        }
        startSend(app, template, time)
    }

    // ============ 内部流程 ============

    private fun startSend(context: Context, template: ChatTemplate, userTime: Long) {
        val folder = template.folder
        sendingMap[folder] = true
        broadcast(folder)

        scope.launch {
            try {
                performSend(context, template, userTime)
            } catch (_: Exception) {
                setStatus(context, folder, userTime, ChatMessage.STATUS_FAILED)
                notifyFailure(context, template)
            } finally {
                sendingMap[folder] = false
                broadcast(folder)

                val next = pendingQueues[folder]?.removeFirstOrNull()
                if (next != null) {
                    startSend(context, template, next)
                }
            }
        }
    }

    private suspend fun performSend(
        context: Context,
        template: ChatTemplate,
        userTime: Long
    ) {
        val folder = template.folder
        val settings = SettingsRepository.get(context)
        val list = loadMessages(context, folder)

        val payload = buildPayload(context, template, userTime, settings)

        if (settings.debugMode) {
            val reqText = payload.joinToString("\n\n") { it.content }
            debugMap.getOrPut(folder) { mutableMapOf() }[userTime] = reqText
            broadcast(folder)
        }

        if (settings.debugMode) {
            // 调试模式：非流式，原文一条气泡
            val result = AiClient.chat(
                settings.apiBaseUrl, settings.apiKey, settings.model,
                settings.enableThinking, payload
            )
            val parsed = AiParser.parse(result.content)

            if (settings.receiveWithFormat && !parsed.hasAllTags) {
                list.add(ChatMessage("assistant", result.content, transient = true))
                broadcast(folder)
                setStatus(context, folder, userTime, ChatMessage.STATUS_FAILED)
                notifyFailure(context, template)
                return
            }

            if (parsed.save.isNotBlank()) {
                TemplateRepository.appendNote(
                    context, folder, "${nowStamp()}:${parsed.save.trim()}"
                )
            }

            appendAssistantMessage(context, template, result.content)
            setStatus(context, folder, userTime, ChatMessage.STATUS_NORMAL)
        } else {
            handleStream(context, template, userTime, settings, payload)
        }
    }

    private suspend fun handleStream(
        context: Context,
        template: ChatTemplate,
        userTime: Long,
        settings: AppSettings,
        payload: List<ChatMessage>
    ) {
        val folder = template.folder
        val state = StreamState()
        val lineBuffer = StringBuilder()
        val raw = StringBuilder()
        val emittedTimes = mutableListOf<Long>()
        var firstBubbleDelivered = false

        suspend fun emitBubble(text: String) {
            if (text.isEmpty()) return
            if (!firstBubbleDelivered) {
                firstBubbleDelivered = true
                if (state.waitSeconds > 0) delay(state.waitSeconds * 1000L)
            }
            val list = messagesMap[folder] ?: mutableListOf()
            val msg = ChatMessage("assistant", text)
            list.add(msg)
            emittedTimes.add(msg.time)
            persist(context, folder)

            val isViewing = activeFolder == folder
            if (!isViewing) {
                unreadMap[folder] = (unreadMap[folder] ?: 0) + 1
            }
            broadcast(folder)
            maybeNotify(context, template, text, isViewing)
        }

        try {
            AiClient.chatStreamFlow(
                settings.apiBaseUrl, settings.apiKey, settings.model,
                settings.enableThinking, payload
            ).collect { chunk ->
                raw.append(chunk)
                lineBuffer.append(chunk)
                while (true) {
                    val (line, consumed) = extractOneLine(lineBuffer)
                    if (consumed == 0) break
                    lineBuffer.delete(0, consumed)
                    for (text in handleStreamLine(line, state)) {
                        emitBubble(text)
                    }
                }
            }
            if (lineBuffer.isNotEmpty()) {
                val tail = lineBuffer.toString()
                lineBuffer.setLength(0)
                for (text in handleStreamLine(tail, state)) {
                    emitBubble(text)
                }
            }
        } catch (e: Exception) {
            val list = messagesMap[folder] ?: mutableListOf()
            val hadAnyEmitted = emittedTimes.isNotEmpty()
            list.removeAll { emittedTimes.contains(it.time) }
            persist(context, folder)
            broadcast(folder)
            setStatus(context, folder, userTime, ChatMessage.STATUS_FAILED)
            if (!hadAnyEmitted) notifyFailure(context, template)
            throw e
        }

        val parsed = AiParser.parse(raw.toString())
        if (settings.receiveWithFormat && !parsed.hasAllTags) {
            val list = messagesMap[folder] ?: mutableListOf()
            val hadAnyEmitted = emittedTimes.isNotEmpty()
            list.removeAll { emittedTimes.contains(it.time) }
            persist(context, folder)
            broadcast(folder)
            setStatus(context, folder, userTime, ChatMessage.STATUS_FAILED)
            if (!hadAnyEmitted) notifyFailure(context, template)
            return
        }

        val save = state.saveBuffer.toString().trim()
        if (save.isNotBlank()) {
            TemplateRepository.appendNote(context, folder, "${nowStamp()}:$save")
        }

        // 把流式临时气泡合并为一条完整原文
        val list = messagesMap[folder] ?: mutableListOf()
        list.removeAll { emittedTimes.contains(it.time) }

        val rawText = raw.toString()
        if (rawText.isNotBlank()) {
            list.add(ChatMessage("assistant", rawText))
        }
        persist(context, folder)
        broadcast(folder)

        setStatus(context, folder, userTime, ChatMessage.STATUS_NORMAL)
    }

    private fun appendAssistantMessage(
        context: Context,
        template: ChatTemplate,
        text: String
    ): ChatMessage {
        val folder = template.folder
        val list = messagesMap[folder] ?: mutableListOf()
        val msg = ChatMessage("assistant", text)
        list.add(msg)
        persist(context, folder)

        val isViewing = activeFolder == folder
        if (!isViewing) {
            unreadMap[folder] = (unreadMap[folder] ?: 0) + 1
        }

        broadcast(folder)
        maybeNotify(context, template, text, isViewing)
        return msg
    }

    // ============ 状态 / 存储 / 通知 ============

    private fun setStatus(context: Context, folder: String, userTime: Long, status: Int) {
        val list = messagesMap[folder] ?: return
        val idx = list.indexOfFirst { it.time == userTime && it.role == "user" }
        if (idx >= 0) list[idx] = list[idx].copy(status = status)
        persist(context, folder)
        broadcast(folder)
    }

    private fun persist(context: Context, folder: String) {
        val list = messagesMap[folder] ?: return
        StorageManager.writeMessages(context, folder, list.toList())
    }

    private fun broadcast(folder: String) {
        _changes.tryEmit(folder)
    }

    private fun maybeNotify(
        context: Context,
        template: ChatTemplate,
        text: String,
        isViewingThisChat: Boolean
    ) {
        if (text.isBlank()) return
        if (isViewingThisChat) return
        if (!notificationsAllowed(context)) return

        Notifier.notifyMessage(
            context = context,
            folder = template.folder,
            title = template.name,
            content = text,
            avatarText = template.avatar
        )
    }

    private fun notifyFailure(context: Context, template: ChatTemplate) {
        if (activeFolder == template.folder) return
        if (!notificationsAllowed(context)) return

        Notifier.notifyMessage(
            context = context,
            folder = template.folder,
            title = template.name,
            content = "网络错误，消息发送失败",
            avatarText = template.avatar
        )
    }

    private fun notificationsAllowed(context: Context): Boolean {
        val settings = SettingsRepository.get(context)
        if (!settings.notifyEnabled) return false
        if (!androidx.core.app.NotificationManagerCompat
                .from(context)
                .areNotificationsEnabled()
        ) return false
        return true
    }

    // ============ 构建 payload ============

    /**
     * 构建发送给 AI 的消息列表：
     *   1. system 提示词 + 记忆
     *   2. 前 N 轮对话（N = settings.historyRounds，默认 10）
     *      每轮 = 1 条 user + 1 条 assistant，内容为完整原文
     *   3. 当前用户消息（原文）
     */
    private fun buildPayload(
        context: Context,
        template: ChatTemplate,
        currentUserTime: Long,
        settings: AppSettings
    ): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()

        val notes = TemplateRepository.readNotes(context, template.folder)
        val promptWithMemory = if (notes.isEmpty()) {
            template.prompt
        } else {
            template.prompt + "\n\n【已记住的信息】\n" + notes.joinToString("\n")
        }
        result.add(ChatMessage("system", promptWithMemory))

        val list = messagesMap[template.folder] ?: mutableListOf()
        val idx = list.indexOfFirst { it.time == currentUserTime && it.role == "user" }
        val before = if (idx >= 0) list.subList(0, idx) else emptyList()

        val real = before.filter {
            (it.role == "user" || it.role == "assistant") &&
                    !it.transient && it.status != ChatMessage.STATUS_FAILED
        }

        val rounds = settings.historyRounds.coerceAtLeast(1)
        val startIndex = computeRoundStartIndex(real, rounds)
        val recent = real.subList(startIndex, real.size)
        result.addAll(recent)

        val userMsg = list.firstOrNull { it.time == currentUserTime && it.role == "user" }
            ?: return result
        result.add(ChatMessage("user", userMsg.content))
        return result
    }

    /**
     * 从 real 列表里倒着数出 rounds 个 user 消息的位置作为起点。
     */
    private fun computeRoundStartIndex(
        real: List<ChatMessage>,
        rounds: Int
    ): Int {
        if (real.isEmpty()) return 0

        var userCount = 0
        var i = real.size - 1
        while (i >= 0) {
            if (real[i].role == "user") {
                userCount++
                if (userCount >= rounds) return i
            }
            i--
        }
        return 0
    }

    // ============ 流式辅助 ============

    private class StreamState {
        var waitSeconds: Int = 0
        var inData: Boolean = false
        var inSave: Boolean = false
        val saveBuffer = StringBuilder()
    }

    /**
     * 从 buffer 头部提取一行（遇到真实 \n 或字面 \n）。
     * 返回 (line, consumed)；未找到 → ("", 0)。
     */
    private fun extractOneLine(buffer: StringBuilder): Pair<String, Int> {
        val text = buffer.toString()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') return text.substring(0, i) to (i + 1)
            if (c == '\\') {
                if (i + 1 >= text.length) return "" to 0
                if (text[i + 1] == 'n') return text.substring(0, i) to (i + 2)
                i += 2
                continue
            }
            i += 1
        }
        return "" to 0
    }

    /**
     * 处理一行。返回本行需要 emit 的所有气泡。
     *
     * 一行里可能同时出现内容 + 新标签：
     *   "躺床上呢 save:xxx"  →  emit "躺床上呢"，save 累积 "xxx"
     *   "没事呀 data:yyy"    →  emit "没事呀"，切到 data 模式
     *   "wait:2 data:xxx"    →  记录 wait=2，切到 data 模式
     * 用 while 循环处理所有可能。
     */
    private fun handleStreamLine(line: String, state: StreamState): List<String> {
        val results = mutableListOf<String>()
        var work = line.trim()

        while (work.isNotEmpty()) {
            val lower = work.lowercase()

            var earliestIdx = -1
            var earliestTag = ""
            for (tag in listOf("wait:", "data:", "save:")) {
                val idx = lower.indexOf(tag)
                if (idx >= 0 && (earliestIdx < 0 || idx < earliestIdx)) {
                    earliestIdx = idx
                    earliestTag = tag
                }
            }

            if (earliestIdx < 0) {
                if (state.inSave) {
                    state.saveBuffer.append(work).append("\n")
                } else if (state.inData && work.isNotEmpty()) {
                    results.add(work)
                }
                break
            }

            val prefix = work.substring(0, earliestIdx).trim()
            val afterTag = work.substring(earliestIdx + earliestTag.length).trim()

            if (prefix.isNotEmpty()) {
                if (state.inSave) {
                    state.saveBuffer.append(prefix).append(" ")
                } else if (state.inData) {
                    results.add(prefix)
                }
            }

            when (earliestTag) {
                "wait:" -> {
                    state.inData = false
                    state.inSave = false
                    val v = afterTag.takeWhile { it.isDigit() }
                    state.waitSeconds = v.toIntOrNull() ?: 0
                    work = afterTag.substring(v.length).trim()
                }
                "data:" -> {
                    state.inData = true
                    state.inSave = false
                    work = afterTag
                }
                "save:" -> {
                    state.inData = false
                    state.inSave = true
                    work = afterTag
                }
            }
        }

        return results
    }

    private fun formatNow(): String {
        val c = Calendar.getInstance()
        return "%d/%d.%d/%02d:%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
        )
    }

    private fun nowStamp(): String = formatNow()
}