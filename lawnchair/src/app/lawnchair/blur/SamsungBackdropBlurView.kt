package app.lawnchair.blur

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.android.launcher3.Insettable
import kotlin.math.roundToInt

/**
 * Full-screen view below the scrim that carries Samsung's window blur for the app drawer.
 *
 * Samsung's blur covers our own content under this view in proportion to the view's alpha, while
 * the blurred wallpaper uses the full radius. So both follow the blur amount: home screen icons
 * fade out as the wallpaper behind them blurs in. The scrim above still draws the drawer tint.
 *
 * The view is GONE whenever the amount is 0, because even a 0 radius blur covers the content.
 *
 * Don't override [hasOverlappingRendering] to false: without an alpha layer, Samsung's blur
 * ignores the view alpha and covers the home screen as soon as the swipe starts.
 */
class SamsungBackdropBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs),
    Insettable {

    private var appliedRadius = -1

    init {
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * @param amount blur progress in [0, 1].
     * @param maxRadius blur radius in px at full progress.
     */
    fun setBlurAmount(amount: Float, maxRadius: Int) {
        if (amount <= 0f) {
            clear()
            return
        }
        // Quantize so a swipe doesn't rebuild the blur info for every sub-pixel change.
        val radius = ((amount * maxRadius) / RADIUS_STEP_PX).roundToInt() * RADIUS_STEP_PX
        if (radius != appliedRadius) {
            if (!SamsungBlur.apply(this, radius, Color.TRANSPARENT)) {
                clear()
                return
            }
            appliedRadius = radius
        }
        alpha = amount
        visibility = VISIBLE
    }

    fun clear() {
        visibility = GONE
        appliedRadius = -1
    }

    // Like the scrim, cover the whole window, including behind the system bars.
    override fun setInsets(insets: Rect) = Unit

    companion object {
        private const val RADIUS_STEP_PX = 2
    }
}
