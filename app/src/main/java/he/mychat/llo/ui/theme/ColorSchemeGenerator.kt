package he.mychat.llo.ui.theme

import android.graphics.Color

object ColorSchemeGenerator {

    fun generate(seed: Int, dark: Boolean): MyScheme {
        val hsv = FloatArray(3)
        Color.colorToHSV(seed, hsv)
        val h = hsv[0]
        var s = hsv[1]
        if (s < 0.25f) s = 0.38f
        return if (dark) darkScheme(h, s) else lightScheme(h, s)
    }

    private fun hsl(hDeg: Float, s: Float, l: Float): Int {
        val h = ((hDeg % 360f) + 360f) % 360f
        val sf = s.coerceIn(0f, 1f)
        val lf = l.coerceIn(0f, 1f)
        val c = (1f - Math.abs(2f * lf - 1f)) * sf
        val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
        val m = lf - c / 2f
        val r: Float
        val g: Float
        val b: Float
        when {
            h < 60f -> { r = c; g = x; b = 0f }
            h < 120f -> { r = x; g = c; b = 0f }
            h < 180f -> { r = 0f; g = c; b = x }
            h < 240f -> { r = 0f; g = x; b = c }
            h < 300f -> { r = x; g = 0f; b = c }
            else -> { r = c; g = 0f; b = x }
        }
        return Color.rgb(
            ((r + m) * 255f + 0.5f).toInt().coerceIn(0, 255),
            ((g + m) * 255f + 0.5f).toInt().coerceIn(0, 255),
            ((b + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        )
    }

    private fun lightScheme(h: Float, s: Float) = MyScheme(
        primary = hsl(h, s * 0.9f, 0.42f),
        onPrimary = hsl(h, 0.3f, 0.99f),
        primaryContainer = hsl(h, s * 0.85f, 0.90f),
        onPrimaryContainer = hsl(h, s * 0.9f, 0.12f),
        secondaryContainer = hsl(h, s * 0.35f, 0.91f),
        onSecondaryContainer = hsl(h, s * 0.5f, 0.16f),
        surface = hsl(h, s * 0.06f, 0.985f),
        onSurface = hsl(h, s * 0.15f, 0.10f),
        surfaceVariant = hsl(h, s * 0.18f, 0.93f),
        onSurfaceVariant = hsl(h, s * 0.15f, 0.34f),
        surfaceContainer = hsl(h, s * 0.09f, 0.955f),
        surfaceContainerHigh = hsl(h, s * 0.11f, 0.925f),
        surfaceContainerLow = hsl(h, s * 0.07f, 0.972f),
        outline = hsl(h, s * 0.12f, 0.50f),
        outlineVariant = hsl(h, s * 0.14f, 0.82f),
        bubbleUser = hsl(h, s * 0.85f, 0.90f),
        onBubbleUser = hsl(h, s * 0.9f, 0.13f),
        bubbleAi = hsl(h, s * 0.10f, 0.945f),
        onBubbleAi = hsl(h, s * 0.15f, 0.13f),
        isDark = false
    )

    private fun darkScheme(h: Float, s: Float) = MyScheme(
        primary = hsl(h, s * 0.65f, 0.80f),
        onPrimary = hsl(h, s * 0.9f, 0.20f),
        primaryContainer = hsl(h, s * 0.7f, 0.30f),
        onPrimaryContainer = hsl(h, s * 0.8f, 0.90f),
        secondaryContainer = hsl(h, s * 0.30f, 0.30f),
        onSecondaryContainer = hsl(h, s * 0.35f, 0.90f),
        surface = hsl(h, s * 0.10f, 0.075f),
        onSurface = hsl(h, s * 0.10f, 0.91f),
        surfaceVariant = hsl(h, s * 0.14f, 0.30f),
        onSurfaceVariant = hsl(h, s * 0.12f, 0.80f),
        surfaceContainer = hsl(h, s * 0.11f, 0.12f),
        surfaceContainerHigh = hsl(h, s * 0.12f, 0.16f),
        surfaceContainerLow = hsl(h, s * 0.10f, 0.10f),
        outline = hsl(h, s * 0.10f, 0.60f),
        outlineVariant = hsl(h, s * 0.13f, 0.30f),
        bubbleUser = hsl(h, s * 0.7f, 0.30f),
        onBubbleUser = hsl(h, s * 0.8f, 0.92f),
        bubbleAi = hsl(h, s * 0.12f, 0.17f),
        onBubbleAi = hsl(h, s * 0.12f, 0.90f),
        isDark = true
    )
}