package he.mychat.llo.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import he.mychat.llo.data.SettingsRepository

object ThemeEngine {

    fun isDark(context: Context, mode: Int): Boolean = when (mode) {
        1 -> false
        2 -> true
        else -> (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun scheme(context: Context): MyScheme {
        val s = SettingsRepository.get(context)
        return ColorSchemeGenerator.generate(s.seedColor, isDark(context, s.darkMode))
    }

    /**
     * 递归把 MD3 配色应用到视图树。
     * MaterialButton 根据 tag 决定用哪套颜色：
     *   "filled" -> primary / onPrimary
     *   "tonal"  -> secondaryContainer / onSecondaryContainer
     *   "text"   -> 透明背景 + primary 前景
     *   "icon"   -> 透明背景 + onSurface 图标
     *   其它     -> 按 filled 处理
     */
    fun tint(root: View, scheme: MyScheme) {
        when (root) {
            is MaterialButton -> {
                when (root.tag) {
                    "filled" -> {
                        root.backgroundTintList = ColorStateList.valueOf(scheme.primary)
                        root.setTextColor(scheme.onPrimary)
                        root.iconTint = ColorStateList.valueOf(scheme.onPrimary)
                    }
                    "tonal" -> {
                        root.backgroundTintList = ColorStateList.valueOf(scheme.secondaryContainer)
                        root.setTextColor(scheme.onSecondaryContainer)
                        root.iconTint = ColorStateList.valueOf(scheme.onSecondaryContainer)
                    }
                    "text" -> {
                        root.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                        root.setTextColor(scheme.primary)
                        root.iconTint = ColorStateList.valueOf(scheme.primary)
                    }
                    "icon" -> {
                        root.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                        root.iconTint = ColorStateList.valueOf(scheme.onSurface)
                    }
                    else -> {
                        root.backgroundTintList = ColorStateList.valueOf(scheme.primary)
                        root.setTextColor(scheme.onPrimary)
                        root.iconTint = ColorStateList.valueOf(scheme.onPrimary)
                    }
                }
            }
            is MaterialCardView -> {
                root.setCardBackgroundColor(scheme.surfaceContainerLow)
                root.strokeColor = scheme.outlineVariant
            }
            is FloatingActionButton -> {
                root.backgroundTintList = ColorStateList.valueOf(scheme.primaryContainer)
                root.imageTintList = ColorStateList.valueOf(scheme.onPrimaryContainer)
            }
            is TextInputLayout -> {
                root.boxStrokeColor = scheme.outline
                root.hintTextColor = ColorStateList.valueOf(scheme.onSurfaceVariant)
                root.defaultHintTextColor = ColorStateList.valueOf(scheme.onSurfaceVariant)
            }
            is EditText -> {
                root.setTextColor(scheme.onSurface)
                root.setHintTextColor(scheme.onSurfaceVariant)
            }
            is RecyclerView -> {
                root.setBackgroundColor(scheme.surface)
            }
            is ImageButton -> {
                root.imageTintList = ColorStateList.valueOf(scheme.onSurface)
            }
            is ImageView -> {
                // 交给调用方
            }
            is TextView -> {
                if (root.tag == null) {
                    root.setTextColor(scheme.onSurface)
                }
            }
            else -> {
                // 容器 / 装饰 View 不做处理
            }
        }

        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                tint(root.getChildAt(i), scheme)
            }
        }
    }
}