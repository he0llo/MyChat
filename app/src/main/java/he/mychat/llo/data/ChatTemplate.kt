package he.mychat.llo.data

import org.json.JSONObject

data class ChatTemplate(
    val id: String,
    val name: String,
    val avatar: String,
    val avatarPath: String = "",
    val prompt: String,
    val folder: String,
    val enabled: Boolean = true
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("avatar", avatar)
        put("avatarPath", avatarPath)
        put("prompt", prompt)
        put("folder", folder)
        put("enabled", enabled)
    }.toString()

    companion object {
        fun fromJson(obj: JSONObject, folder: String): ChatTemplate = ChatTemplate(
            id = obj.optString("id"),
            name = obj.optString("name", "未命名"),
            avatar = obj.optString("avatar", ""),
            avatarPath = obj.optString("avatarPath", ""),
            prompt = obj.optString("prompt", ""),
            folder = folder,
            enabled = obj.optBoolean("enabled", true)
        )
    }
}