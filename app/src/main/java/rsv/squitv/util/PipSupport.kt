package rsv.squitv.util

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Process

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.findActivity() else null
    else -> null
}

fun Context.supportsMediaPip(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
    !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
    (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION

fun Context.isMediaPipAllowed(): Boolean {
    if (!supportsMediaPip()) return false
    val appOps = getSystemService(AppOpsManager::class.java) ?: return false
    return try {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: SecurityException) {
        false
    }
}
