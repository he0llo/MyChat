package he.mychat.llo

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import he.mychat.llo.data.DefaultPrompts
import he.mychat.llo.data.SettingsRepository
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.net.Notifier

class MyChatApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AppLifecycle.register(this)

        val settings = SettingsRepository.get(this)
        AppCompatDelegate.setDefaultNightMode(
            when (settings.darkMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        Notifier.ensureChannel(this)

        ensureDefaultTemplate()
    }

    private fun ensureDefaultTemplate() {
        val prefs = getSharedPreferences("mychat_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("default_template_created", false)) return

        if (TemplateRepository.list(this).isEmpty()) {
            TemplateRepository.create(
                this,
                name = "李心妍",
                avatar = "妍",
                prompt = DefaultPrompts.AI_GIRLFRIEND
            )
        }
        prefs.edit().putBoolean("default_template_created", true).apply()
    }
}