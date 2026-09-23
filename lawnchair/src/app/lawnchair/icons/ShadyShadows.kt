package app.lawnchair.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.MainThread
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.views.ShadowInfo
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * The "Shady" shadow style for home screen and dock icons and labels: a soft drop shadow under the
 * icon, and a stronger version of the default label shadow.
 *
 * The icon bitmap is usually a hardware bitmap, so its blurred alpha mask is built once per bitmap
 * and cached; each frame then only draws that small mask under the icon.
 */
object ShadyShadows {
    /** Blur radius, as a fraction of the icon size. */
    private const val BLUR_FACTOR = 0.035f

    /** Downward offset, as a fraction of the icon size. */
    private const val OFFSET_Y_FACTOR = 0.025f

    private const val SHADOW_ALPHA = 0x26 // 15%

    /** How much stronger the Shady label shadow is than the default one. */
    private const val LABEL_BLUR_SCALE = 2f
    private const val LABEL_ALPHA_SCALE = 0.9f

    @JvmStatic
    fun isEnabled(context: Context): Boolean = PreferenceManager2.getInstance(context).homeShadyShadows.firstCached()

    /** The Shady label shadow, based on the theme's default [base] shadow. */
    @JvmStatic
    fun labelShadowInfo(base: ShadowInfo): ShadowInfo = base.copy(
        ambientShadowBlur = base.ambientShadowBlur * LABEL_BLUR_SCALE,
        ambientShadowColor = scaleAlpha(base.ambientShadowColor),
        keyShadowBlur = base.keyShadowBlur * LABEL_BLUR_SCALE,
        keyShadowOffsetX = base.keyShadowOffsetX * LABEL_BLUR_SCALE,
        keyShadowOffsetY = base.keyShadowOffsetY * LABEL_BLUR_SCALE,
        keyShadowColor = scaleAlpha(base.keyShadowColor),
    )

    // A transparent shadow (e.g. for dark labels) stays transparent.
    private fun scaleAlpha(color: Int): Int = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * LABEL_ALPHA_SCALE).roundToInt().coerceAtMost(255))

    private class Mask(val bitmap: Bitmap, val offsetX: Int, val offsetY: Int)

    private val masks = WeakHashMap<Bitmap, Mask>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        color = Color.BLACK
        alpha = SHADOW_ALPHA
    }
    private val dst = RectF()

    /**
     * Draws the shadow for [icon], which is drawn into [bounds] at [scale] around its center.
     */
    @JvmStatic
    @MainThread
    fun draw(canvas: Canvas, icon: Bitmap, bounds: Rect, scale: Float) {
        val mask = masks.getOrPut(icon) { createMask(icon) ?: return }
        val k = bounds.width() / icon.width.toFloat()
        val left = bounds.left + mask.offsetX * k
        val top = bounds.top + mask.offsetY * k + bounds.height() * OFFSET_Y_FACTOR
        dst.set(left, top, left + mask.bitmap.width * k, top + mask.bitmap.height * k)
        if (scale != 1f) {
            val count = canvas.save()
            canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.drawBitmap(mask.bitmap, null, dst, paint)
            canvas.restoreToCount(count)
        } else {
            canvas.drawBitmap(mask.bitmap, null, dst, paint)
        }
    }

    private fun createMask(icon: Bitmap): Mask? {
        val source = if (icon.config == Bitmap.Config.HARDWARE) {
            icon.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            icon
        }
        val blurPaint = Paint().apply {
            maskFilter = BlurMaskFilter(source.width * BLUR_FACTOR, BlurMaskFilter.Blur.NORMAL)
        }
        val offset = IntArray(2)
        val alpha = source.extractAlpha(blurPaint, offset)
        if (source !== icon) source.recycle()
        return Mask(alpha, offset[0], offset[1])
    }
}
