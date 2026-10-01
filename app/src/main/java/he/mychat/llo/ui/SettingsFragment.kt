package he.mychat.llo.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import he.mychat.llo.MainActivity
import he.mychat.llo.R
import he.mychat.llo.TopBarHost
import he.mychat.llo.data.SettingsRepository
import he.mychat.llo.data.StorageManager
import he.mychat.llo.data.TemplateRepository
import he.mychat.llo.net.Notifier
import he.mychat.llo.ui.theme.ThemeEngine

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var avatar: AvatarView
    private lateinit var name: TextView
    private lateinit var apiSummary: TextView
    private lateinit var templateCount: TextView
    private lateinit var switchNotify: MaterialSwitch
    private lateinit var switchSendFormat: MaterialSwitch
    private lateinit var switchReceiveFormat: MaterialSwitch
    private lateinit var switchDebug: MaterialSwitch
    private lateinit var switchHideRecents: MaterialSwitch
    private lateinit var notifySystemSummary: TextView
    private lateinit var historySummary: TextView
    private lateinit var colorDot: View
    private lateinit var darkValue: TextView
    private lateinit var storagePath: TextView

    private val palette = intArrayOf(
        0xFF6750A4.toInt(), 0xFF00639B.toInt(), 0xFFB3261E.toInt(),
        0xFF386A20.toInt(), 0xFF7D5260.toInt(), 0xFF8E4585.toInt(),
        0xFFE8750A.toInt(), 0xFF006A6A.toInt(), 0xFF4A6363.toInt(),
        0xFF7A5700.toInt(), 0xFF984061.toInt(), 0xFF4F5B92.toInt()
    )

    private var dialogPreview: AvatarView? = null
    private var pendingUserAvatarPath: String? = null

    private val pickUserAvatar = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val path = StorageManager.importAvatar(requireContext(), uri, "user")
        if (path.isNullOrBlank()) {
            Toast.makeText(requireContext(), "导入图片失败", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        pendingUserAvatarPath = path
        val scheme = ThemeEngine.scheme(requireContext())
        dialogPreview?.bind("", path, scheme.primaryContainer, scheme.onPrimaryContainer)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        avatar = view.findViewById(R.id.avatar)
        name = view.findViewById(R.id.name)
        apiSummary = view.findViewById(R.id.api_summary)
        templateCount = view.findViewById(R.id.template_count)
        switchNotify = view.findViewById(R.id.switch_notify)
        switchSendFormat = view.findViewById(R.id.switch_send_format)
        switchReceiveFormat = view.findViewById(R.id.switch_receive_format)
        switchDebug = view.findViewById(R.id.switch_debug)
        switchHideRecents = view.findViewById(R.id.switch_hide_recents)
        notifySystemSummary = view.findViewById(R.id.notify_system_summary)
        historySummary = view.findViewById(R.id.history_summary)
        colorDot = view.findViewById(R.id.color_dot)
        darkValue = view.findViewById(R.id.dark_value)
        storagePath = view.findViewById(R.id.storage_path)

        (activity as? TopBarHost)?.configureTopBar(
            title = "设置",
            showLeft = true
        )

        view.findViewById<LinearLayout>(R.id.row_profile).setOnClickListener { editProfile() }
        view.findViewById<LinearLayout>(R.id.row_api).setOnClickListener {
            (activity as? MainActivity)?.navigate(ApiSettingsFragment())
        }
        view.findViewById<LinearLayout>(R.id.row_templates).setOnClickListener {
            (activity as? MainActivity)?.navigate(TemplateListFragment())
        }
        view.findViewById<LinearLayout>(R.id.row_color).setOnClickListener { pickColor() }
        view.findViewById<LinearLayout>(R.id.row_dark).setOnClickListener { pickDark() }

        view.findViewById<LinearLayout>(R.id.row_notify).setOnClickListener {
            switchNotify.isChecked = !switchNotify.isChecked
        }
        view.findViewById<LinearLayout>(R.id.row_notify_system).setOnClickListener {
            Notifier.openSystemNotificationSettings(requireContext())
        }
        view.findViewById<LinearLayout>(R.id.row_send_format).setOnClickListener {
            switchSendFormat.isChecked = !switchSendFormat.isChecked
        }
        view.findViewById<LinearLayout>(R.id.row_receive_format).setOnClickListener {
            switchReceiveFormat.isChecked = !switchReceiveFormat.isChecked
        }
        view.findViewById<LinearLayout>(R.id.row_history).setOnClickListener {
            pickHistoryRounds()
        }
        view.findViewById<LinearLayout>(R.id.row_debug).setOnClickListener {
            switchDebug.isChecked = !switchDebug.isChecked
        }
        view.findViewById<LinearLayout>(R.id.row_hide_recents).setOnClickListener {
            switchHideRecents.isChecked = !switchHideRecents.isChecked
        }
        view.findViewById<LinearLayout>(R.id.row_about).setOnClickListener {
            (activity as? MainActivity)?.navigate(AboutFragment())
        }

        bindNotifyListener()
        bindSendFormatListener()
        bindReceiveFormatListener()
        bindDebugListener()
        bindHideRecentsListener()

        storagePath.text = "存储位置：${StorageManager.rootDir(requireContext()).absolutePath}"
        storagePath.setTextColor(ThemeEngine.scheme(requireContext()).onSurfaceVariant)

        ThemeEngine.tint(view, ThemeEngine.scheme(requireContext()))
    }

    override fun onResume() {
        super.onResume()
        refresh()
        view?.let { ThemeEngine.tint(it, ThemeEngine.scheme(requireContext())) }
    }

    // ==================== 开关绑定 ====================

    private fun bindNotifyListener() {
        switchNotify.setOnCheckedChangeListener { _, checked ->
            val s = SettingsRepository.get(requireContext())
            if (s.notifyEnabled != checked) {
                s.notifyEnabled = checked
                SettingsRepository.save(requireContext(), s)
            }
        }
    }

    private fun bindSendFormatListener() {
        switchSendFormat.setOnCheckedChangeListener { _, checked ->
            val s = SettingsRepository.get(requireContext())
            if (s.sendWithFormat != checked) {
                s.sendWithFormat = checked
                SettingsRepository.save(requireContext(), s)
            }
        }
    }

    private fun bindReceiveFormatListener() {
        switchReceiveFormat.setOnCheckedChangeListener { _, checked ->
            val s = SettingsRepository.get(requireContext())
            if (s.receiveWithFormat != checked) {
                s.receiveWithFormat = checked
                SettingsRepository.save(requireContext(), s)
            }
        }
    }

    private fun bindDebugListener() {
        switchDebug.setOnCheckedChangeListener { _, checked ->
            val s = SettingsRepository.get(requireContext())
            if (s.debugMode != checked) {
                s.debugMode = checked
                SettingsRepository.save(requireContext(), s)
            }
        }
    }

    private fun bindHideRecentsListener() {
        switchHideRecents.setOnCheckedChangeListener { _, checked ->
            val s = SettingsRepository.get(requireContext())
            if (s.hideFromRecents != checked) {
                s.hideFromRecents = checked
                SettingsRepository.save(requireContext(), s)
                (activity as? MainActivity)?.applyHideFromRecents()
            }
        }
    }

    // ==================== 刷新 ====================

    private fun refresh() {
        val ctx = requireContext()
        val settings = SettingsRepository.get(ctx)
        val scheme = ThemeEngine.scheme(ctx)

        avatar.bind(
            settings.userAvatar.ifBlank { settings.userName },
            settings.userAvatarPath,
            scheme.primaryContainer,
            scheme.onPrimaryContainer
        )
        name.text = settings.userName
        name.setTextColor(scheme.onSurface)

        apiSummary.text = when {
            settings.apiBaseUrl.isBlank() -> "未配置"
            settings.model.isBlank() -> settings.apiBaseUrl
            else -> "${settings.model}\n${settings.apiBaseUrl}"
        }
        apiSummary.setTextColor(scheme.onSurfaceVariant)

        templateCount.text = TemplateRepository.list(ctx).size.toString()
        templateCount.setTextColor(scheme.onSurfaceVariant)

        switchNotify.setOnCheckedChangeListener(null)
        switchNotify.isChecked = settings.notifyEnabled
        bindNotifyListener()

        val enabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        notifySystemSummary.text = if (enabled) {
            "系统通知已开启"
        } else {
            "系统通知已关闭，点击前往开启"
        }
        notifySystemSummary.setTextColor(scheme.onSurfaceVariant)

        switchSendFormat.setOnCheckedChangeListener(null)
        switchSendFormat.isChecked = settings.sendWithFormat
        bindSendFormatListener()

        switchReceiveFormat.setOnCheckedChangeListener(null)
        switchReceiveFormat.isChecked = settings.receiveWithFormat
        bindReceiveFormatListener()

        historySummary.text = "${settings.historyRounds} 轮（${settings.historyRounds * 2} 条）"
        historySummary.setTextColor(scheme.onSurfaceVariant)

        switchDebug.setOnCheckedChangeListener(null)
        switchDebug.isChecked = settings.debugMode
        bindDebugListener()

        switchHideRecents.setOnCheckedChangeListener(null)
        switchHideRecents.isChecked = settings.hideFromRecents
        bindHideRecentsListener()

        colorDot.background = UiUtils.circle(settings.seedColor)

        darkValue.text = when (settings.darkMode) {
            1 -> "浅色"
            2 -> "深色"
            else -> "跟随系统"
        }
        darkValue.setTextColor(scheme.onSurfaceVariant)
    }

    // ==================== 个人资料 ====================

    private fun editProfile() {
        val ctx = requireContext()
        val settings = SettingsRepository.get(ctx)
        val scheme = ThemeEngine.scheme(ctx)

        val dialogView = LayoutInflater.from(ctx)
            .inflate(R.layout.dialog_profile, null)

        val preview = dialogView.findViewById<AvatarView>(R.id.avatar_preview)
        val btnPick = dialogView.findViewById<MaterialButton>(R.id.btn_pick)
        val nameInput = dialogView.findViewById<EditText>(R.id.input_name)
        val avatarInput = dialogView.findViewById<EditText>(R.id.input_avatar)

        pendingUserAvatarPath = settings.userAvatarPath
        dialogPreview = preview

        preview.bind(
            settings.userAvatar.ifBlank { settings.userName },
            settings.userAvatarPath,
            scheme.primaryContainer,
            scheme.onPrimaryContainer
        )
        nameInput.setText(settings.userName)
        avatarInput.setText(settings.userAvatar)

        btnPick.setOnClickListener {
            pickUserAvatar.launch("image/*")
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("修改资料")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                settings.userName = nameInput.text.toString().ifBlank { "我" }
                settings.userAvatar = avatarInput.text.toString().ifBlank { "我" }
                settings.userAvatarPath = pendingUserAvatarPath ?: ""
                SettingsRepository.save(ctx, settings)
                dialogPreview = null
                refresh()
            }
            .setNegativeButton("取消") { _, _ ->
                dialogPreview = null
                pendingUserAvatarPath = null
            }
            .setNeutralButton("清除图片") { _, _ ->
                pendingUserAvatarPath = ""
                preview.bind(
                    avatarInput.text.toString().ifBlank { settings.userName },
                    null,
                    scheme.primaryContainer,
                    scheme.onPrimaryContainer
                )
            }
            .show()
    }

    // ==================== 主题色 ====================

    private fun pickColor() {
        val ctx = requireContext()
        val settings = SettingsRepository.get(ctx)

        val container = LayoutInflater.from(ctx)
            .inflate(R.layout.dialog_color_picker, null) as android.widget.FrameLayout
        val grid = container.findViewById<GridLayout>(R.id.grid)

        val sizePx = UiUtils.dp(ctx, 56)
        val gapPx = UiUtils.dp(ctx, 8)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("选择主题色")
            .setView(container)
            .setNegativeButton("取消", null)
            .create()

        palette.forEach { color ->
            val dot = View(ctx)
            val lp = GridLayout.LayoutParams()
            lp.width = sizePx
            lp.height = sizePx
            lp.setMargins(gapPx, gapPx, gapPx, gapPx)
            dot.layoutParams = lp

            val selected = color == settings.seedColor
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (selected) {
                    setStroke(UiUtils.dp(ctx, 4), 0xFF1D1B20.toInt())
                } else {
                    setStroke(UiUtils.dp(ctx, 1), 0x33000000)
                }
            }

            dot.setOnClickListener {
                settings.seedColor = color
                SettingsRepository.save(ctx, settings)
                dialog.dismiss()
                requireActivity().recreate()
            }

            grid.addView(dot)
        }

        dialog.show()
    }

    // ==================== 深色模式 ====================

    private fun pickDark() {
        val settings = SettingsRepository.get(requireContext())
        val labels = arrayOf("跟随系统", "浅色", "深色")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("深色模式")
            .setSingleChoiceItems(labels, settings.darkMode) { dialog, which ->
                settings.darkMode = which
                SettingsRepository.save(requireContext(), settings)
                AppCompatDelegate.setDefaultNightMode(
                    when (which) {
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 历史对话数量 ====================

    private fun pickHistoryRounds() {
        val ctx = requireContext()
        val settings = SettingsRepository.get(ctx)

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiUtils.dp(ctx, 20),
                UiUtils.dp(ctx, 8),
                UiUtils.dp(ctx, 20),
                0
            )
        }
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "输入轮数（1-100）"
            setText(settings.historyRounds.toString())
            setSelection(text.length)
        }
        container.addView(input)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("历史对话数量")
            .setMessage("每轮 = 用户 1 条 + AI 1 条，例如 10 轮 = 20 条消息。")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val n = input.text.toString().trim().toIntOrNull() ?: settings.historyRounds
                val safe = n.coerceIn(1, 100)
                settings.historyRounds = safe
                SettingsRepository.save(ctx, settings)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}