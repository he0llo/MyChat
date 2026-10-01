package he.mychat.llo.data

import org.json.JSONObject

data class ChatMessage(
    val role: String,
    val content: String,
    val time: Long = System.currentTimeMillis(),
    /** 仅内存：临时消息（坏回复预览等），不写磁盘 */
    val transient: Boolean = false,
    /** 持久化：发送状态 */
    val status: Int = STATUS_NORMAL
) {
    fun toJson(): String = JSONObject().apply {
        put("role", role)
        put("content", content)
        put("time", time)
        put("status", status)
    }.toString()

    companion object {
        const val STATUS_NORMAL = 0
        const val STATUS_FAILED = 1
        const val STATUS_PENDING = 2

        fun fromJson(obj: JSONObject): ChatMessage = ChatMessage(
            role = obj.optString("role", "user"),
            content = obj.optString("content", ""),
            time = obj.optLong("time", System.currentTimeMillis()),
            status = obj.optInt("status", STATUS_NORMAL)
        )
    }
}