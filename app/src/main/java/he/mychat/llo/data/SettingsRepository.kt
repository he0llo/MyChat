package he.mychat.llo.data

import android.content.Context

object SettingsRepository {

    private var cached: AppSettings? = null

    fun get(context: Context): AppSettings {
        cached?.let { return it }
        val s = StorageManager.readSettings(context)
        cached = s
        return s
    }

    fun save(context: Context, settings: AppSettings) {
        cached = settings
        StorageManager.writeSettings(context, settings)
    }

    fun invalidate() {
        cached = null
    }
}