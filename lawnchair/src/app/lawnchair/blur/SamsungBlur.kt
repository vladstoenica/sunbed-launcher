package app.lawnchair.blur

import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Wrapper around Samsung One UI's `View.semSetBlurInfo` API.
 *
 * One UI does not implement AOSP cross-window blur (`ro.surface_flinger.supports_background_blur`
 * is unset), but ships its own compositor blur. `SemBlurInfo` in [SemBlurInfo.BLUR_MODE_WINDOW]
 * blurs whatever is behind the window (for the launcher: the wallpaper) inside the bounds of the
 * view it is set on. The view's alpha controls how much of our own window content underneath the
 * view gets covered; the blur radius itself is independent of alpha.
 *
 * The methods used here are exposed as SDK APIs in Samsung's hidden-API list, so no bypass is
 * needed. Everything goes through reflection; any failure permanently disables this path.
 */
object SamsungBlur {
    private const val TAG = "SamsungBlur"

    private class Api(
        val modeWindow: Int,
        val builderCtor: Constructor<*>,
        val setRadius: Method,
        val setBackgroundColor: Method,
        val build: Method,
        val setBlurInfo: Method,
    )

    @Volatile
    private var failed = false

    private val api: Api? by lazy {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return@lazy null
        try {
            val infoCls = Class.forName("android.view.SemBlurInfo")
            val builderCls = Class.forName("android.view.SemBlurInfo\$Builder")
            Api(
                modeWindow = infoCls.getField("BLUR_MODE_WINDOW").getInt(null),
                builderCtor = builderCls.getConstructor(Int::class.javaPrimitiveType),
                setRadius = builderCls.getMethod("setRadius", Int::class.javaPrimitiveType),
                setBackgroundColor = builderCls.getMethod("setBackgroundColor", Int::class.javaPrimitiveType),
                build = builderCls.getMethod("build"),
                setBlurInfo = View::class.java.getMethod("semSetBlurInfo", infoCls),
            )
        } catch (t: Throwable) {
            Log.i(TAG, "SemBlurInfo not available", t)
            null
        }
    }

    /** Whether the Samsung blur API exists on this device and has not failed. */
    val isAvailable: Boolean
        get() = !failed && api != null

    /**
     * Blurs the content behind the window within [view]'s bounds.
     *
     * @return false if the call failed; the API is then disabled for the rest of the process.
     */
    fun apply(view: View, radius: Int, @ColorInt tint: Int): Boolean {
        val api = api ?: return false
        if (failed) return false
        return try {
            val builder = api.builderCtor.newInstance(api.modeWindow)
            api.setRadius.invoke(builder, radius)
            api.setBackgroundColor.invoke(builder, tint)
            api.setBlurInfo.invoke(view, api.build.invoke(builder))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "semSetBlurInfo failed, disabling Samsung blur", t)
            failed = true
            false
        }
    }
}
