package he.mychat.llo.data

import org.json.JSONObject

data class AppSettings(
    var userName: String = "我",
    var userAvatar: String = "我",
    var userAvatarPath: String = "",
    var apiBaseUrl: String = "",
    var apiKey: String = "",
    var model: String = "",
    var enableThinking: Boolean = false,
    var debugMode: Boolean = false,
    var hideFromRecents: Boolean = false,
    var notifyEnabled: Boolean = true,
    var sendWithFormat: Boolean = true,
    var receiveWithFormat: Boolean = true,
    var historyRounds: Int = 10,
    var seedColor: Int = 0xFF6750A4.toInt(),
    var darkMode: Int = 0
) {
    fun toJson(): String = JSONObject().apply {
        put("userName", userName)
        put("userAvatar", userAvatar)
        put("userAvatarPath", userAvatarPath)
        put("apiBaseUrl", apiBaseUrl)
        put("apiKey", apiKey)
        put("model", model)
        put("enableThinking", enableThinking)
        put("debugMode", debugMode)
        put("hideFromRecents", hideFromRecents)
        put("notifyEnabled", notifyEnabled)
        put("sendWithFormat", sendWithFormat)
        put("receiveWithFormat", receiveWithFormat)
        put("historyRounds", historyRounds)
        put("seedColor", seedColor)
        put("darkMode", darkMode)
    }.toString()

    companion object {
        fun fromJson(obj: JSONObject): AppSettings = AppSettings(
            userName = obj.optString("userName", "我"),
            userAvatar = obj.optString("userAvatar", "我"),
            userAvatarPath = obj.optString("userAvatarPath", ""),
            apiBaseUrl = obj.optString("apiBaseUrl", ""),
            apiKey = obj.optString("apiKey", ""),
            model = obj.optString("model", ""),
            enableThinking = obj.optBoolean("enableThinking", false),
            debugMode = obj.optBoolean("debugMode", false),
            hideFromRecents = obj.optBoolean("hideFromRecents", false),
            notifyEnabled = obj.optBoolean("notifyEnabled", true),
            sendWithFormat = obj.optBoolean(
                "sendWithFormat",
                obj.optBoolean("formatCheckEnabled", true)
            ),
            receiveWithFormat = obj.optBoolean(
                "receiveWithFormat",
                obj.optBoolean("formatCheckEnabled", true)
            ),
            historyRounds = obj.optInt("historyRounds", 10),
            seedColor = obj.optInt("seedColor", 0xFF6750A4.toInt()),
            darkMode = obj.optInt("darkMode", 0)
        )
    }
}