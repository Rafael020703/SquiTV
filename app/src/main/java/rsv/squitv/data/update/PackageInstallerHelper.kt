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
    fun validationError(context: Context, apkFile: File, expectedVersion: String? = null, expectedVersionCode: Long? = null): String? {
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
                expectedVersionCode != null && versionCode(candidate) != expectedVersionCode ->
                    context.getString(R.string.updater_wrong_version)
                versionCode(candidate) <= versionCode(installed) -> context.getString(R.string.updater_old_apk)
                else -> when (checkSigners(context, apkFile, installed, candidate)) {
                    SignerCheck.MATCH -> null
                    SignerCheck.DIFFERENT -> context.getString(R.string.updater_wrong_signature)
                    SignerCheck.UNAVAILABLE -> context.getString(R.string.updater_signature_unavailable)
                }
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

    private enum class SignerCheck { MATCH, DIFFERENT, UNAVAILABLE }

    @Suppress("DEPRECATION")
    private fun checkSigners(context: Context, apkFile: File, installed: PackageInfo, candidate: PackageInfo): SignerCheck {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val current = installed.signingInfo
            val incoming = candidate.signingInfo
            val currentSigners = current?.apkContentsSigners?.toSet().orEmpty()
            val incomingSigners = incoming?.apkContentsSigners?.toSet().orEmpty()
            if (current != null && incoming != null && currentSigners.isNotEmpty() && incomingSigners.isNotEmpty()) {
                // An unchanged key needs no proof of rotation, even if the archive
                // does not expose its signing history.
                if (currentSigners == incomingSigners) return SignerCheck.MATCH
                if (current.hasMultipleSigners() || incoming.hasMultipleSigners()) return SignerCheck.DIFFERENT
                // Different keys require a verified forward history; never use the legacy
                // fallback to override a definite mismatch in the modern certificates.
                return if (incoming.signingCertificateHistory?.toSet().orEmpty().containsAll(currentSigners))
                    SignerCheck.MATCH else SignerCheck.DIFFERENT
            }
            // Retry only when modern signer data is unavailable. Both sides must still
            // provide the same nonempty certificate set through PackageManager.
            val manager = context.packageManager
            val legacyInstalled = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val legacyCandidate = manager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
                ?: return SignerCheck.UNAVAILABLE
            if (legacyCandidate.packageName != candidate.packageName || versionCode(legacyCandidate) != versionCode(candidate))
                return SignerCheck.UNAVAILABLE
            return compareLegacySigners(legacyInstalled, legacyCandidate)
        }
        return compareLegacySigners(installed, candidate)
    }

    @Suppress("DEPRECATION")
    private fun compareLegacySigners(installed: PackageInfo, candidate: PackageInfo): SignerCheck {
        val current = installed.signatures?.toSet().orEmpty()
        val incoming = candidate.signatures?.toSet().orEmpty()
        return when {
            current.isEmpty() || incoming.isEmpty() -> SignerCheck.UNAVAILABLE
            current == incoming -> SignerCheck.MATCH
            else -> SignerCheck.DIFFERENT
        }
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
