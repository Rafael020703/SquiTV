package rsv.squitv.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import rsv.squitv.R
import timber.log.Timber
import java.io.File

object PackageInstallerHelper {
    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openUnknownAppSourcesSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return try {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        } catch (e: Exception) {
            Timber.w(e, "Não foi possível abrir a permissão de instalação")
            false
        }
    }

    @Suppress("DEPRECATION")
    fun validationError(context: Context, apkFile: File, expectedVersion: String? = null): String? {
        if (!apkFile.isFile || apkFile.length() <= 0) return context.getString(R.string.updater_missing_file)
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else PackageManager.GET_SIGNATURES
            val manager = context.packageManager
            val candidate = manager.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: return context.getString(R.string.updater_invalid_apk)
            val installed = manager.getPackageInfo(context.packageName, flags)
            when {
                candidate.packageName != context.packageName -> context.getString(R.string.updater_wrong_package)
                expectedVersion != null && VersionComparator.compareVersions(candidate.versionName.orEmpty(), expectedVersion) != 0 ->
                    context.getString(R.string.updater_wrong_version)
                versionCode(candidate) <= versionCode(installed) -> context.getString(R.string.updater_old_apk)
                !hasCompatibleSigners(installed, candidate) -> context.getString(R.string.updater_wrong_signature)
                else -> null
            }
        } catch (e: Exception) {
            Timber.w(e, "Não foi possível validar o APK")
            context.getString(R.string.updater_invalid_apk)
        }
    }

    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    @Suppress("DEPRECATION")
    private fun hasCompatibleSigners(installed: PackageInfo, candidate: PackageInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val current = installed.signingInfo ?: return false
            val incoming = candidate.signingInfo ?: return false
            val currentSigners = current.apkContentsSigners?.toSet().orEmpty()
            val incomingSigners = incoming.apkContentsSigners?.toSet().orEmpty()
            if (currentSigners.isEmpty() || incomingSigners.isEmpty()) return false
            if (current.hasMultipleSigners() || incoming.hasMultipleSigners()) return currentSigners == incomingSigners
            // Accept a forward rotation only when the archive proves the installed signer
            // belongs to its signing history. Android's installer remains the final authority.
            return incoming.signingCertificateHistory?.toSet().orEmpty().containsAll(currentSigners)
        }
        val current = installed.signatures?.toSet().orEmpty()
        return current.isNotEmpty() && current == candidate.signatures?.toSet().orEmpty()
    }

    // Called only after validation on Dispatchers.IO by UpdateViewModel.
    fun installApk(context: Context, apkFile: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (e: Exception) {
        Timber.w(e, "Não foi possível abrir o instalador")
        false
    }
}
