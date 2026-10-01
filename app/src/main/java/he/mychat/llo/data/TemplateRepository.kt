package he.mychat.llo.data

import android.content.Context
import java.util.UUID

object TemplateRepository {

    fun list(context: Context): List<ChatTemplate> {
        val result = mutableListOf<ChatTemplate>()
        StorageManager.listTemplateFolders(context).forEach { folder ->
            StorageManager.readTemplate(context, folder)?.let { result.add(it) }
        }
        return result.sortedBy { it.name }
    }

    fun find(context: Context, folder: String): ChatTemplate? =
        StorageManager.readTemplate(context, folder)

    fun create(context: Context, name: String, avatar: String, prompt: String): ChatTemplate {
        val folder = StorageManager.uniqueFolder(context, sanitize(name.ifBlank { "chat" }))
        val t = ChatTemplate(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "未命名" },
            avatar = avatar,
            prompt = prompt,
            folder = folder
        )
        StorageManager.writeTemplate(context, t)
        StorageManager.writeMessages(context, folder, emptyList())
        return t
    }

    fun update(context: Context, template: ChatTemplate) {
        StorageManager.writeTemplate(context, template)
    }

    fun delete(context: Context, template: ChatTemplate) {
        StorageManager.deleteTemplate(context, template)
    }

    fun loadMessages(context: Context, folder: String): MutableList<ChatMessage> =
        StorageManager.readMessages(context, folder)

    fun saveMessages(context: Context, folder: String, messages: List<ChatMessage>) =
        StorageManager.writeMessages(context, folder, messages)

    fun lastMessage(context: Context, folder: String): ChatMessage? =
        StorageManager.readMessages(context, folder).lastOrNull()

    fun appendNote(context: Context, folder: String, note: String) {
        StorageManager.appendNote(context, folder, note)
    }

    fun readNotes(context: Context, folder: String): List<String> {
        return StorageManager.readNotes(context, folder)
    }

    fun clearMessages(context: Context, folder: String) {
        StorageManager.clearMessages(context, folder)
    }

    fun writeNotes(context: Context, folder: String, notes: List<String>) {
        StorageManager.writeNotes(context, folder, notes)
    }

    private fun sanitize(name: String): String {
        val sb = StringBuilder()
        name.forEach { c ->
            sb.append(
                if (c.isLetterOrDigit() || c == '_' || c == '-') c else '_'
            )
        }
        val s = sb.toString().trim('_')
        return if (s.isEmpty()) "chat" else s.take(24)
    }
}