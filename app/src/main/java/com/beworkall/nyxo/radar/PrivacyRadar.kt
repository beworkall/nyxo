package com.beworkall.nyxo.radar

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.beworkall.nyxo.data.AppEntry

/** Where the user goes to take a permission away. */
enum class Fix { APP, ACCESSIBILITY, NOTIFICATIONS, SECURITY }

data class Finding(val title: String, val detail: String, val high: Boolean, val fix: Fix)

enum class Risk { HIGH, MEDIUM, CLEAN }

data class RadarResult(
    val pkg: String,
    val label: String,
    /** False for apps that have powerful access but no launcher icon. */
    val hasIcon: Boolean,
    val findings: List<Finding>,
) {
    val risk: Risk
        get() = when {
            findings.any { it.high } -> Risk.HIGH
            findings.isNotEmpty() -> Risk.MEDIUM
            else -> Risk.CLEAN
        }
}

/**
 * Reads what every app is currently allowed to do, using only on-device APIs.
 * Nothing is stored or sent anywhere.
 */
object PrivacyRadar {

    fun scan(context: Context, apps: List<AppEntry>): Map<String, RadarResult> {
        val pm = context.packageManager
        val cr = context.contentResolver
        val accessibility = owners(runCatching {
            Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull())
        val listeners = owners(runCatching {
            Settings.Secure.getString(cr, "enabled_notification_listeners")
        }.getOrNull())
        val admins: Set<String> = runCatching {
            context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins?.map { it.packageName }?.toSet()
        }.getOrNull() ?: emptySet()
        val appOps = context.getSystemService(AppOpsManager::class.java)

        val labels = HashMap<String, String>()
        for (a in apps) if (!labels.containsKey(a.pkg)) labels[a.pkg] = a.label

        val all = (labels.keys + accessibility + listeners + admins) - context.packageName
        val out = HashMap<String, RadarResult>()
        for (pkg in all) {
            val label = labels[pkg] ?: labelOf(pm, pkg)
            val findings = findingsFor(pm, appOps, pkg, pkg in accessibility, pkg in listeners, pkg in admins)
            out[pkg] = RadarResult(pkg, label, labels.containsKey(pkg), findings)
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun labelOf(pm: PackageManager, pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: pkg

    @Suppress("DEPRECATION")
    private fun findingsFor(
        pm: PackageManager,
        appOps: AppOpsManager?,
        pkg: String,
        a11y: Boolean,
        listener: Boolean,
        admin: Boolean,
    ): List<Finding> {
        val out = ArrayList<Finding>()
        if (a11y) out.add(Finding(
            "Can see and control your screen",
            "An accessibility service from this app is turned on. It can read everything shown on screen, " +
                "including messages and what you type, and it can tap and type for you.",
            true, Fix.ACCESSIBILITY))
        if (listener) out.add(Finding(
            "Reads all your notifications",
            "It can read every notification as it arrives: messages, one-time login codes and alerts from all your apps.",
            true, Fix.NOTIFICATIONS))
        if (admin) out.add(Finding(
            "Device administrator",
            "It can lock the phone, change the screen lock or erase data, and it is harder to uninstall.",
            true, Fix.SECURITY))

        val info: PackageInfo? = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS) }.getOrNull()
        val granted = HashSet<String>()
        val requested = info?.requestedPermissions
        val flags = info?.requestedPermissionsFlags
        if (requested != null && flags != null) {
            for (i in requested.indices) {
                if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    granted.add(requested[i])
                }
            }
        }

        if (Manifest.permission.CAMERA in granted) out.add(Finding(
            "Camera", "It can take photos and record video while it is running.", false, Fix.APP))
        if (Manifest.permission.RECORD_AUDIO in granted) out.add(Finding(
            "Microphone", "It can record audio while it is running.", false, Fix.APP))
        if (Manifest.permission.ACCESS_BACKGROUND_LOCATION in granted) {
            out.add(Finding("Location, even in the background",
                "It can see where you are at any time, even when you are not using it.", false, Fix.APP))
        } else if (Manifest.permission.ACCESS_FINE_LOCATION in granted ||
            Manifest.permission.ACCESS_COARSE_LOCATION in granted) {
            out.add(Finding("Location", "It can see where you are while you use it.", false, Fix.APP))
        }
        if (Manifest.permission.READ_SMS in granted || Manifest.permission.RECEIVE_SMS in granted) out.add(Finding(
            "Reads your SMS", "It can read text messages, including one-time login codes.", false, Fix.APP))
        if (Manifest.permission.READ_CALL_LOG in granted) out.add(Finding(
            "Reads your call history", "It can see who you called and when.", false, Fix.APP))

        val appFlags = info?.applicationInfo?.flags ?: 0
        val preinstalled = (appFlags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        if (info != null && !preinstalled) {
            val installer = runCatching {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
                else pm.getInstallerPackageName(pkg)
            }.getOrNull()
            if (installer == null || installer !in TRUSTED_STORES) out.add(Finding(
                NOT_FROM_STORE,
                "It was installed from a file or another app, not from Google Play, Galaxy Store or F-Droid. " +
                    "Most scam apps arrive this way. Fine if you installed it on purpose.",
                false, Fix.APP))
        }

        val uid = info?.applicationInfo?.uid
        if (uid != null && appOps != null) {
            val mode = runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)
                } else {
                    appOps.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)
                }
            }.getOrNull()
            if (mode == AppOpsManager.MODE_ALLOWED) out.add(Finding(
                "Draws over other apps",
                "It can show windows on top of other apps, which can be used to cover or imitate what you see.",
                false, Fix.APP))
        }
        return out
    }

    const val NOT_FROM_STORE = "Not from an app store"

    private val TRUSTED_STORES = setOf(
        "com.android.vending",               // Google Play
        "com.sec.android.app.samsungapps",   // Galaxy Store
        "org.fdroid.fdroid",                 // F-Droid
        "org.fdroid.basic",
        "com.aurora.store",                  // Aurora (Play mirror)
        "com.amazon.venezia",                // Amazon Appstore
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "com.xiaomi.mipicks",
        "com.heytap.market",                 // OPPO / OnePlus
        "com.oppo.market",
        "com.bbk.appstore",                  // vivo
        "com.google.android.feedback",       // Play internal
    )

    /** "pkg/cls:pkg2/cls2" -> {pkg, pkg2} */
    private fun owners(raw: String?): Set<String> =
        (raw ?: "").split(':')
            .mapNotNull { part -> part.substringBefore('/').trim().takeIf { it.isNotEmpty() } }
            .toSet()
}
