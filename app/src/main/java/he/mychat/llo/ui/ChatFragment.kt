package he.mychat.llo.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import he.mychat.llo.MainActivity
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.AppSettings
import he.mychat.llo.data.ChatMessage
import he.mychat.llo.data.ChatTemplate
import he.mychat.llo.data.SettingsRepository
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.net.AiParser
import he.mychat.llo.net.ChatSender
import he.mychat.llo.ui.theme.ThemeEngine
import kotlinx.coroutines.launch
import java.util.Calendar

class ChatFragment : Fragment(R.layout.fragment_chat) {

    companion object {
        private const val ARG_FOLDER = "folder"
        private const val GAP_MS = 5 * 60 * 1000L

        fun newInstance(folder: String): ChatFragment {
            val f = ChatFragment()
            f.arguments = Bundle().apply { putString(ARG_FOLDER, folder) }
            return f
        }
    }

    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var inputBar: View
    private lateinit var adapter: MessageAdapter

    private lateinit var template: ChatTemplate
    private var inputBarBasePadBottom = 0
    private var lastDebugMode = false

    private var selectionMode = false
    private val selectedKeys = mutableSetOf<String>()
    private var selectionBackCallback: OnBackPressedCallback? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val folder = requireArguments().getString(ARG_FOLDER) ?: return
        template = TemplateRepository.find(requireContext(), folder) ?: run {
            Toast.makeText(requireContext(), "模板不存在", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        if (!template.enabled) {
            Toast.makeText(requireContext(), "该好友已停用", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        val scheme = ThemeEngine.scheme(requireContext())
        view.setBackgroundColor(scheme.surface)

        val settings = SettingsRepository.get(requireContext())
        lastDebugMode = settings.debugMode

        recycler = view.findViewById(R.id.recycler)
        input = view.findViewById(R.id.input)
        btnSend = view.findViewById(R.id.btn_send)
        inputBar = view.findViewById(R.id.input_bar)

        adapter = MessageAdapter(
            scheme = scheme,
            userAvatarText = settings.userAvatar.ifBlank { settings.userName },
            userAvatarPath = settings.userAvatarPath.ifBlank { null },
            aiAvatarText = template.avatar.ifBlank { template.name },
            aiAvatarPath = template.avatarPath.ifBlank { null },
            onRetry = { msg -> retryMessage(msg) },
            onLongPress = { item -> enterSelection(item) },
            onClick = { item -> toggleSelection(item) }
        )

        val lm = LinearLayoutManager(requireContext())
        lm.stackFromEnd = true
        recycler.layoutManager = lm
        recycler.adapter = adapter

        input.setTextColor(scheme.onSurface)
        input.setHintTextColor(scheme.onSurfaceVariant)

        ChatSender.loadMessages(requireContext(), template.folder)
        refresh()

        btnSend.setOnClickListener { send() }

        parentFragmentManager.setFragmentResultListener(
            FriendInfoFragment.RESULT_CHAT_CLEARED,
            viewLifecycleOwner
        ) { _, bundle ->
            val clearedFolder = bundle.getString("folder")
            if (clearedFolder == template.folder) {
                ChatSender.clearCache(template.folder)
                ChatSender.loadMessages(requireContext(), template.folder)
                refresh()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            ChatSender.changes.collect { f ->
                if (f == template.folder) {
                    refresh()
                    setTyping(ChatSender.isSending(template.folder))
                }
            }
        }

        inputBarBasePadBottom = inputBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottom = maxOf(ime, nav)
            inputBar.setPadding(
                inputBar.paddingLeft,
                inputBar.paddingTop,
                inputBar.paddingRight,
                inputBarBasePadBottom + bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)

        val cb = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                exitSelection()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, cb)
        selectionBackCallback = cb

        setupNormalTopBar()
        ThemeEngine.tint(view, scheme)
    }

    override fun onResume() {
        super.onResume()
        ChatSender.setActiveFolder(template.folder)
        val settings = SettingsRepository.get(requireContext())
        if (lastDebugMode != settings.debugMode) refresh()
        lastDebugMode = settings.debugMode
        setTyping(ChatSender.isSending(template.folder))
        refresh()
    }

    override fun onPause() {
        super.onPause()
        ChatSender.setActiveFolder(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? TopBarHost)?.setSubtitle(null)
        selectionBackCallback = null
        selectionMode = false
        selectedKeys.clear()
    }

    // ==================== 顶栏 ====================

    private fun setupNormalTopBar() {
        (activity as? TopBarHost)?.configureTopBar(
            title = template.name,
            showLeft = true,
            showRight = true,
            rightIcon = R.drawable.ic_person,
            onRight = {
                (activity as? MainActivity)?.navigate(
                    FriendInfoFragment.newInstance(template.folder)
                )
            }
        )
    }

    private fun setupSelectionTopBar() {
        (activity as? TopBarHost)?.configureTopBar(
            title = "已选 ${selectedKeys.size} 项",
            showLeft = true,
            leftIcon = R.drawable.ic_close,
            onLeft = { exitSelection() },
            showRight = true,
            rightIcon = R.drawable.ic_delete,
            onRight = { confirmDeleteSelected() }
        )
    }

    private fun setTyping(show: Boolean) {
        (activity as? TopBarHost)?.setSubtitle(if (show) "对方正在输入中" else null)
    }

    // ==================== 选择模式 ====================

    private fun enterSelection(item: MessageAdapter.DisplayItem) {
        if (selectionMode) return
        if (item.selectionKey.isEmpty()) return
        selectionMode = true
        selectedKeys.clear()
        selectedKeys.add(item.selectionKey)
        selectionBackCallback?.isEnabled = true
        setupSelectionTopBar()
        refresh()
    }

    private fun toggleSelection(item: MessageAdapter.DisplayItem) {
        if (!selectionMode) return
        if (item.selectionKey.isEmpty()) return

        if (selectedKeys.contains(item.selectionKey)) {
            selectedKeys.remove(item.selectionKey)
        } else {
            selectedKeys.add(item.selectionKey)
        }

        if (selectedKeys.isEmpty()) exitSelection()
        else {
            setupSelectionTopBar()
            refresh()
        }
    }

    private fun exitSelection() {
        if (!selectionMode) return
        selectionMode = false
        selectedKeys.clear()
        selectionBackCallback?.isEnabled = false
        setupNormalTopBar()
        refresh()
    }

    private fun confirmDeleteSelected() {
        if (selectedKeys.isEmpty()) {
            exitSelection()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除消息")
            .setMessage("已选 ${selectedKeys.size} 条消息，确定删除吗？")
            .setPositiveButton("删除") { _, _ -> deleteSelected() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteSelected() {
        val list = ChatSender.loadMessages(requireContext(), template.folder)
        val remaining = mutableListOf<ChatMessage>()

        list.forEach { msg ->
            val keysForMsg = keysOfMessage(msg)
            val selectedInThis = keysForMsg.filter { selectedKeys.contains(it) }

            when {
                selectedInThis.isEmpty() -> remaining.add(msg)

                selectedInThis.size == keysForMsg.size -> {
                    // 该消息全部气泡被选中 → 整条删除
                }

                else -> {
                    // 部分选中 → 重建 content
                    val rebuilt = rebuildPartialMessage(msg)
                    if (rebuilt != null) remaining.add(rebuilt)
                }
            }
        }

        ChatSender.replaceMessages(requireContext(), template.folder, remaining)
        exitSelection()
    }

    /** 计算该 ChatMessage 在 UI 上对应的所有 selectionKey */
    private fun keysOfMessage(msg: ChatMessage): List<String> {
        if (msg.role == "user") return listOf("msg-${msg.time}")

        val settings = SettingsRepository.get(requireContext())
        return if (settings.debugMode || !settings.receiveWithFormat) {
            listOf("msg-${msg.time}-bubble-0")
        } else {
            val parsed = AiParser.parse(msg.content)
            val bubbles = parsed.dataMessages.ifEmpty { listOf(msg.content) }
            bubbles.indices.map { "msg-${msg.time}-bubble-$it" }
        }
    }

    /**
     * 部分选中时：只保留未选中的 data 气泡，重建 content。
     * wait / save 段如果还有剩余 data 就保留，否则整体丢弃。
     */
    private fun rebuildPartialMessage(msg: ChatMessage): ChatMessage? {
        if (msg.role == "user") return msg

        val settings = SettingsRepository.get(requireContext())
        if (settings.debugMode || !settings.receiveWithFormat) return msg

        val parsed = AiParser.parse(msg.content)
        val bubbles = parsed.dataMessages.ifEmpty { listOf(msg.content) }

        val kept = bubbles.filterIndexed { idx, _ ->
            !selectedKeys.contains("msg-${msg.time}-bubble-$idx")
        }
        if (kept.isEmpty()) return null

        val sb = StringBuilder()
        if (parsed.sleepSeconds > 0) sb.append("wait:").append(parsed.sleepSeconds).append("\n")
        kept.forEach { sb.append("data:").append(it).append("\n") }
        if (parsed.save.isNotBlank()) sb.append("save:").append(parsed.save)
        // 去掉末尾可能的换行
        while (sb.isNotEmpty() && sb.last() == '\n') sb.deleteCharAt(sb.length - 1)

        return msg.copy(content = sb.toString())
    }

    // ==================== 渲染 ====================

    private fun refresh() {
        val settings = SettingsRepository.get(requireContext())
        val list = ChatSender.loadMessages(requireContext(), template.folder)
        val items = buildDisplayItems(settings, list)
        adapter.submit(items, selectionMode, selectedKeys)
        scrollToBottom()
    }

    private fun buildDisplayItems(
        settings: AppSettings,
        list: List<ChatMessage>
    ): List<MessageAdapter.DisplayItem> {
        val items = mutableListOf<MessageAdapter.DisplayItem>()
        var lastTime = 0L

        list.forEach { m ->
            when (m.role) {
                "user" -> {
                    if (lastTime == 0L || m.time - lastTime > GAP_MS) {
                        items.add(
                            MessageAdapter.DisplayItem(
                                MessageAdapter.TYPE_TIME,
                                timeText = formatTime(m.time)
                            )
                        )
                    }
                    val text = if (settings.debugMode) {
                        ChatSender.debugPayload(template.folder, m.time) ?: m.content
                    } else {
                        extractUserText(m.content)
                    }
                    items.add(
                        MessageAdapter.DisplayItem(
                            MessageAdapter.TYPE_USER,
                            text = text,
                            failed = m.status == ChatMessage.STATUS_FAILED,
                            source = m,
                            selectionKey = "msg-${m.time}"
                        )
                    )
                    lastTime = m.time
                }
                "assistant" -> {
                    if (settings.debugMode || !settings.receiveWithFormat) {
                        if (lastTime == 0L || m.time - lastTime > GAP_MS) {
                            items.add(
                                MessageAdapter.DisplayItem(
                                    MessageAdapter.TYPE_TIME,
                                    timeText = formatTime(m.time)
                                )
                            )
                        }
                        items.add(
                            MessageAdapter.DisplayItem(
                                MessageAdapter.TYPE_AI,
                                text = m.content,
                                source = m,
                                selectionKey = "msg-${m.time}-bubble-0"
                            )
                        )
                    } else {
                        val parsed = AiParser.parse(m.content)
                        val bubbles = parsed.dataMessages.ifEmpty { listOf(m.content) }
                        bubbles.forEachIndexed { idx, text ->
                            if (idx == 0 && (lastTime == 0L || m.time - lastTime > GAP_MS)) {
                                items.add(
                                    MessageAdapter.DisplayItem(
                                        MessageAdapter.TYPE_TIME,
                                        timeText = formatTime(m.time)
                                    )
                                )
                            }
                            items.add(
                                MessageAdapter.DisplayItem(
                                    MessageAdapter.TYPE_AI,
                                    text = text,
                                    source = m,
                                    selectionKey = "msg-${m.time}-bubble-$idx"
                                )
                            )
                        }
                    }
                    lastTime = m.time
                }
            }
        }
        return items
    }

    private fun extractUserText(content: String): String {
        val idx = content.indexOf("data:")
        if (idx < 0) return content
        return content.substring(idx + 5).trimStart()
    }

    private fun scrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) {
            recycler.scrollToPosition(count - 1)
        }
    }

    // ==================== 发送 ====================

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        val settings = SettingsRepository.get(requireContext())
        if (settings.apiBaseUrl.isBlank()) {
            Toast.makeText(requireContext(), "请先在设置里填写 API 地址", Toast.LENGTH_SHORT).show()
            return
        }
        if (settings.apiKey.isBlank()) {
            Toast.makeText(requireContext(), "请先在设置里填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        if (settings.model.isBlank()) {
            Toast.makeText(requireContext(), "请先在设置里填写模型名称", Toast.LENGTH_SHORT).show()
            return
        }

        input.setText("")
        setTyping(true)
        ChatSender.send(requireContext(), template, text)
    }

    private fun retryMessage(msg: ChatMessage) {
        setTyping(true)
        ChatSender.retry(requireContext(), template, msg.time)
    }

    // ==================== 时间 ====================

    private fun formatTime(ms: Long): String {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply { timeInMillis = ms }

        val hh = "%02d".format(t.get(Calendar.HOUR_OF_DAY))
        val mm = "%02d".format(t.get(Calendar.MINUTE))

        val sameYear = now.get(Calendar.YEAR) == t.get(Calendar.YEAR)
        val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)

        if (sameDay) return "$hh:$mm"

        val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = y.get(Calendar.YEAR) == t.get(Calendar.YEAR) &&
                y.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) return "昨天 $hh:$mm"

        return if (sameYear) {
            "${t.get(Calendar.MONTH) + 1}月${t.get(Calendar.DAY_OF_MONTH)}日 $hh:$mm"
        } else {
            "${t.get(Calendar.YEAR)}年${t.get(Calendar.MONTH) + 1}月${t.get(Calendar.DAY_OF_MONTH)}日 $hh:$mm"
        }
    }
}