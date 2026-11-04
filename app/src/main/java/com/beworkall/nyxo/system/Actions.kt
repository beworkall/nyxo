package com.beworkall.nyxo.system

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.SettingsActivity
import com.beworkall.nyxo.radar.Fix
import java.io.File

object Actions {

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    fun start(context: Context, intent: Intent, failMessage: String = "Can't open that"): Boolean {
        val ok = tryStart(context, intent)
        if (!ok) toast(context, failMessage)
        return ok
    }

    /** Opens an app. With a source view and rect, the app zooms out of its icon. */
    fun launch(context: Context, app: AppEntry, sourceView: View? = null, source: Rect? = null): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (sourceView != null && source != null && source.width() > 0) {
            intent.sourceBounds = source
            val options = ActivityOptions.makeClipRevealAnimation(
                sourceView, source.left, source.top, source.width(), source.height(),
            ).toBundle()
            try {
                context.startActivity(intent, options)
                return true
            } catch (e: Exception) {
                // fall through to a plain launch
            }
        }
        return start(context, intent, "${app.label} can't be opened")
    }

    /** Opens Android's home app picker, so you can go back to One UI Home any time. */
    fun switchHomeApp(context: Context) {
        if (tryStart(context, Intent(Settings.ACTION_HOME_SETTINGS))) return
        start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    fun openNyxoSettings(context: Context) {
        start(context, Intent(context, SettingsActivity::class.java))
    }

    /** Hands a wallpaper file to the system "Set as wallpaper" screen. Needs no permission. */
    fun setAsPhoneWallpaper(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_ATTACH_DATA)
            .setDataAndType(uri, "image/*")
            .putExtra("mimeType", "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(intent, "Set as wallpaper"), "No app can set wallpapers here")
    }

    fun appInfo(context: Context, pkg: String): Boolean =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))

    /** Opens the app's system page, where the user taps Uninstall themselves. */
    fun uninstall(context: Context, pkg: String): Boolean = appInfo(context, pkg)

    fun openFix(context: Context, pkg: String, fix: Fix): Boolean = when (fix) {
        Fix.ACCESSIBILITY -> start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Fix.NOTIFICATIONS -> start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        Fix.SECURITY -> start(context, Intent(Settings.ACTION_SECURITY_SETTINGS))
        Fix.APP -> appInfo(context, pkg)
    }

    fun fixLabel(fix: Fix): String = when (fix) {
        Fix.ACCESSIBILITY -> "Accessibility settings"
        Fix.NOTIFICATIONS -> "Notification access settings"
        Fix.SECURITY -> "Device admin settings"
        Fix.APP -> "Manage permissions"
    }

    fun alarms(context: Context) = start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun calendar(context: Context) =
        start(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))

    fun url(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /** Hands the query to your browser. Nyxo itself never touches the network. */
    fun web(context: Context, query: String) {
        if (tryStart(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))) return
        url(context, "https://duckduckgo.com/?q=" + Uri.encode(query))
    }

    @Suppress("DEPRECATION")
    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == context.packageName
    }

    @Suppress("DEPRECATION")
    fun requestDefaultHome(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 41)
                    return
                } catch (e: Exception) {
                    // fall through to the system home picker
                }
            }
        }
        start(activity, Intent(Settings.ACTION_HOME_SETTINGS))
    }

    /** Fingerprint, face or screen lock. If the phone has no screen lock, it simply continues. */
    @Suppress("DEPRECATION")
    fun authenticate(activity: Activity, title: String, onSuccess: () -> Unit) {
        val km = activity.getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) {
            onSuccess()
            return
        }
        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle("Confirm it's you")
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else if (Build.VERSION.SDK_INT == 29) {
            builder.setDeviceCredentialAllowed(true)
        } else {
            builder.setNegativeButton("Cancel", executor) { _, _ -> }
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                ) {
                    toast(activity, errString?.toString() ?: "Couldn't confirm it's you")
                }
            }
        }
        try {
            builder.build().authenticate(CancellationSignal(), executor, callback)
        } catch (e: Exception) {
            toast(activity, "Couldn't open the lock prompt")
        }
    }
}
