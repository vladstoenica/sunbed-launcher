package app.lawnchair.blur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.PreferenceManager
import kotlin.math.roundToInt

/**
 * Decides whether the app drawer background blur is on, and which backend draws it.
 *
 * - Devices with AOSP cross-window blur (Pixel etc.) use the compositor path in
 *   `BaseDepthController`, driven by `CrossWindowBlurListeners`.
 * - Samsung One UI devices, which lack AOSP cross-window blur, use [SamsungBlur] on
 *   [SamsungBackdropBlurView].
 *
 * Both respect the user's Lawnchair toggle. The Samsung path also mirrors what
 * `CrossWindowBlurListeners` does for AOSP: it turns off with One UI's
 * "Reduce transparency and blur" and with battery saver.
 */
object DrawerBlur {
    // One UI: Accessibility > Visibility enhancements > Reduce transparency and blur.
    private const val SAMSUNG_REDUCE_TRANSPARENCY = "accessibility_reduce_transparency"

    // The drawer tint over the blur is this much more opaque than the drawer opacity setting.
    private const val OPACITY_SCALE_OVER_BLUR = 1.3f

    private var initialized = false
    private var samsungSystemAllowed = false
    private val listeners = mutableListOf<Runnable>()

    @JvmStatic
    fun isUserEnabled(context: Context): Boolean = PreferenceManager.getInstance(context).drawerBlur.get()

    /** Whether the Samsung backend should draw the drawer blur right now. */
    @JvmStatic
    @MainThread
    fun isSamsungBlurEnabled(context: Context): Boolean {
        if (!SamsungBlur.isAvailable) return false
        ensureInitialized(context.applicationContext)
        return samsungSystemAllowed && isUserEnabled(context)
    }

    /**
     * Whether the drawer should use the blurred style.
     *
     * @param crossWindowBlurEnabled the AOSP cross-window blur state.
     */
    @JvmStatic
    @MainThread
    fun isBlurEnabled(context: Context, crossWindowBlurEnabled: Boolean): Boolean = isUserEnabled(context) && (crossWindowBlurEnabled || isSamsungBlurEnabled(context))

    /** Makes the drawer tint [OPACITY_SCALE_OVER_BLUR] times more opaque, for use over the blur. */
    @JvmStatic
    @ColorInt
    fun boostOpacityOverBlur(@ColorInt color: Int): Int {
        val alpha = (Color.alpha(color) * OPACITY_SCALE_OVER_BLUR).roundToInt().coerceAtMost(255)
        return ColorUtils.setAlphaComponent(color, alpha)
    }

    /** Called on the main thread when the Samsung blur system state changes. */
    @JvmStatic
    @MainThread
    fun addSamsungStateListener(listener: Runnable) {
        listeners.add(listener)
    }

    @JvmStatic
    @MainThread
    fun removeSamsungStateListener(listener: Runnable) {
        listeners.remove(listener)
    }

    private fun ensureInitialized(appContext: Context) {
        if (initialized) return
        initialized = true
        val handler = Handler(Looper.getMainLooper())
        val update = { updateSamsungSystemState(appContext) }
        appContext.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(SAMSUNG_REDUCE_TRANSPARENCY),
            false,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = update()
            },
        )
        ContextCompat.registerReceiver(
            appContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = update()
            },
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            null,
            handler,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        samsungSystemAllowed = computeSamsungSystemAllowed(appContext)
    }

    private fun updateSamsungSystemState(appContext: Context) {
        val allowed = computeSamsungSystemAllowed(appContext)
        if (allowed == samsungSystemAllowed) return
        samsungSystemAllowed = allowed
        listeners.toList().forEach { it.run() }
    }

    private fun computeSamsungSystemAllowed(context: Context): Boolean {
        val reduceTransparency = Settings.Global.getInt(context.contentResolver, SAMSUNG_REDUCE_TRANSPARENCY, 0) != 0
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        return !reduceTransparency && !powerSave
    }
}
