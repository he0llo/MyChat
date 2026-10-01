package he.mychat.llo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.File

/**
 * 支持两种模式的头像控件：
 *  1. imagePath 非空且文件存在 -> 显示圆形图片
 *  2. 否则显示彩色圆底 + 单字
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    private val label = TextView(context).apply {
        gravity = Gravity.CENTER
    }

    init {
        addView(
            image,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            label,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun bind(
        text: String,
        imagePath: String?,
        bgColor: Int,
        fgColor: Int
    ) {
        background = UiUtils.circle(bgColor)

        val drawable = loadCircle(imagePath)
        if (drawable != null) {
            image.setImageDrawable(drawable)
            image.visibility = VISIBLE
            label.text = ""
            label.visibility = GONE
        } else {
            image.setImageDrawable(null)
            image.visibility = GONE
            label.visibility = VISIBLE
            label.text = if (text.isBlank()) "友" else text.trim().take(1)
            label.setTextColor(fgColor)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val minSide = if (w < h) w else h
        label.textSize = (minSide * 0.40f) / resources.displayMetrics.density
    }

    private fun loadCircle(path: String?): Drawable? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (!f.exists()) return null
        return try {
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
            RoundedBitmapDrawableFactory.create(resources, bmp).apply {
                isCircular = true
            }
        } catch (_: Exception) {
            null
        }
    }
}