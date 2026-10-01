package he.mychat.llo

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.transition.MaterialSharedAxis
import he.mychat.llo.data.SettingsRepository
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.net.Notifier
import he.mychat.llo.ui.ChatFragment
import he.mychat.llo.ui.ChatListFragment
import he.mychat.llo.ui.theme.ThemeEngine

class MainActivity : AppCompatActivity(), TopBarHost {

    private lateinit var btnLeft: MaterialButton
    private lateinit var btnRight: MaterialButton
    private lateinit var txtTitle: TextView
    private lateinit var txtSubtitle: TextView
    private var leftAction: (() -> Unit)? = null
    private var rightAction: (() -> Unit)? = null

    private val durationMs = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        btnLeft = findViewById(R.id.btn_left)
        btnRight = findViewById(R.id.btn_right)
        txtTitle = findViewById(R.id.txt_title)
        txtSubtitle = findViewById(R.id.txt_subtitle)

        btnLeft.setOnClickListener { leftAction?.invoke() }
        btnRight.setOnClickListener { rightAction?.invoke() }

        val topBar = findViewById<LinearLayout>(R.id.top_bar)
        val basePadTop = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(
                v.paddingLeft,
                basePadTop + sb.top,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(topBar)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            val targetFolder = intent?.getStringExtra(Notifier.EXTRA_OPEN_FOLDER)
            val target = targetFolder?.takeIf {
                it.isNotBlank() && TemplateRepository.find(this, it) != null
            }
            val initial = if (target != null) {
                ChatFragment.newInstance(target)
            } else {
                ChatListFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, initial)
                .commit()
        }

        ensureStorage()
        ensureNotificationPermission()
        applyScheme()
        applyHideFromRecents()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val folder = intent?.getStringExtra(Notifier.EXTRA_OPEN_FOLDER) ?: return
        if (folder.isBlank()) return
        if (TemplateRepository.find(this, folder) == null) return

        supportFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        navigate(ChatFragment.newInstance(folder))
    }

    override fun onResume() {
        super.onResume()
        applyScheme()
        applyHideFromRecents()
    }

    fun applyHideFromRecents() {
        val settings = SettingsRepository.get(this)
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { task ->
                task.setExcludeFromRecents(settings.hideFromRecents)
            }
        } catch (_: Exception) {
        }
    }

    private fun applyScheme() {
        val scheme = ThemeEngine.scheme(this)
        findViewById<android.view.View>(R.id.root).setBackgroundColor(scheme.surface)
        findViewById<android.view.View>(R.id.top_bar).setBackgroundColor(scheme.primaryContainer)
        findViewById<android.view.View>(R.id.divider).setBackgroundColor(scheme.outlineVariant)

        txtTitle.setTextColor(scheme.onPrimaryContainer)
        txtSubtitle.setTextColor(scheme.onPrimaryContainer)

        btnLeft.backgroundTintList = ColorStateList.valueOf(scheme.primary)
        btnLeft.iconTint = ColorStateList.valueOf(scheme.onPrimary)
        btnRight.backgroundTintList = ColorStateList.valueOf(scheme.primary)
        btnRight.iconTint = ColorStateList.valueOf(scheme.onPrimary)

        val settings = SettingsRepository.get(this)
        val isDark = ThemeEngine.isDark(this, settings.darkMode)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark

        val current = supportFragmentManager.findFragmentById(R.id.container)
        current?.view?.let { ThemeEngine.tint(it, scheme) }
    }

    override fun configureTopBar(
        title: CharSequence,
        showLeft: Boolean,
        leftIcon: Int,
        onLeft: (() -> Unit)?,
        showRight: Boolean,
        rightIcon: Int,
        onRight: (() -> Unit)?
    ) {
        txtTitle.text = title
        setSubtitle(null)

        if (showLeft) {
            btnLeft.visibility = android.view.View.VISIBLE
            btnLeft.setIconResource(leftIcon)
            leftAction = onLeft ?: { onBackPressedDispatcher.onBackPressed() }
        } else {
            btnLeft.visibility = android.view.View.INVISIBLE
            leftAction = null
        }

        if (showRight) {
            btnRight.visibility = android.view.View.VISIBLE
            btnRight.setIconResource(rightIcon)
            rightAction = onRight
        } else {
            btnRight.visibility = android.view.View.INVISIBLE
            rightAction = null
        }

        applyScheme()
    }

    override fun setSubtitle(text: CharSequence?) {
        if (text.isNullOrBlank()) {
            txtSubtitle.visibility = android.view.View.GONE
            txtSubtitle.text = ""
        } else {
            txtSubtitle.text = text
            txtSubtitle.visibility = android.view.View.VISIBLE
        }
    }

    fun navigate(fragment: Fragment) {
        val current = supportFragmentManager.findFragmentById(R.id.container)

        fragment.enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true).apply {
            duration = durationMs
        }
        fragment.returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false).apply {
            duration = durationMs
        }

        current?.apply {
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true).apply {
                duration = durationMs
            }
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false).apply {
                duration = durationMs
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun ensureStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = "package:$packageName".toUri()
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {
                    }
                }
            }
        } else {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    1001
                )
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val settings = SettingsRepository.get(this)
        if (!settings.notifyEnabled) return

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1002
            )
        }
    }
    /**
     * 清空返回栈并回到聊天列表（用于删除好友后）。
     */
    fun resetToHome() {
        supportFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, ChatListFragment())
            .commit()
    }
}