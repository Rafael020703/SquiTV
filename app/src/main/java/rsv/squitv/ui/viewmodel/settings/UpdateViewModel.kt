package rsv.squitv.ui.viewmodel.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rsv.squitv.R
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.repository.UpdateRepository
import rsv.squitv.data.update.ApkDownloadManager
import rsv.squitv.data.update.DownloadStatus
import rsv.squitv.data.update.PackageInstallerHelper
import rsv.squitv.domain.model.AppUpdateInfo
import rsv.squitv.domain.model.UpdateCheckResult
import java.io.File
import javax.inject.Inject

sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data class DebugBuild(val currentVersion: String) : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class UpdateAvailable(val currentVersion: String, val updateInfo: AppUpdateInfo, val isIgnored: Boolean = false) : UpdateUiState
    data class Downloading(val updateInfo: AppUpdateInfo, val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateUiState
    data class Verifying(val updateInfo: AppUpdateInfo) : UpdateUiState
    data class ReadyToInstall(val updateInfo: AppUpdateInfo, val apkFile: File) : UpdateUiState
    data class PermissionRequired(val updateInfo: AppUpdateInfo, val apkFile: File) : UpdateUiState
    data class OpeningInstaller(val updateInfo: AppUpdateInfo) : UpdateUiState
    data class InstallerOpened(val updateInfo: AppUpdateInfo) : UpdateUiState
    data class Error(val message: String, val updateInfo: AppUpdateInfo? = null, val retryInstall: Boolean = false) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository,
    private val apkDownloadManager: ApkDownloadManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Checking)
    val uiState = _uiState.asStateFlow()
    val currentVersionName: String get() = updateRepository.currentVersionName
    private var activeUpdateInfo: AppUpdateInfo? = null
    private var downloadedApkFile: File? = null
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var installJob: Job? = null
    private var awaitingPermission = false

    init {
        observeDownloadStatus()
        // Restore an existing download instead of resetting its state when reopening the page.
        if (apkDownloadManager.downloadStatus.value is DownloadStatus.Idle) checkForUpdates(force = false)
    }

    private fun observeDownloadStatus() {
        viewModelScope.launch {
            apkDownloadManager.downloadStatus.collect { status ->
                when (status) {
                    DownloadStatus.Idle -> {
                        if (_uiState.value is UpdateUiState.Downloading || _uiState.value is UpdateUiState.Verifying) {
                            showAvailable()
                        }
                    }
                    is DownloadStatus.Downloading -> {
                        activeUpdateInfo = status.updateInfo
                        _uiState.value = UpdateUiState.Downloading(status.updateInfo, status.progressPercent, status.bytesDownloaded, status.totalBytes)
                    }
                    is DownloadStatus.VerifyingIntegrity -> {
                        activeUpdateInfo = status.updateInfo
                        _uiState.value = UpdateUiState.Verifying(status.updateInfo)
                    }
                    is DownloadStatus.Completed -> {
                        activeUpdateInfo = status.updateInfo
                        downloadedApkFile = status.apkFile
                        showReady(status.updateInfo, status.apkFile)
                    }
                    is DownloadStatus.Failed -> {
                        activeUpdateInfo = status.updateInfo
                        _uiState.value = UpdateUiState.Error(status.errorMessage, status.updateInfo)
                    }
                }
            }
        }
    }

    fun checkForUpdates(force: Boolean = true) {
        if (checkJob?.isActive == true || downloadJob?.isActive == true || installJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            downloadedApkFile = null
            awaitingPermission = false
            activeUpdateInfo = null
            apkDownloadManager.resetState()
            when (val result = updateRepository.checkForUpdates(force)) {
                is UpdateCheckResult.DebugBuild -> _uiState.value = UpdateUiState.DebugBuild(result.currentVersion)
                is UpdateCheckResult.UpdateAvailable -> {
                    activeUpdateInfo = result.updateInfo
                    showAvailable()
                }
                is UpdateCheckResult.UpToDate -> _uiState.value = UpdateUiState.UpToDate(currentVersionName)
                UpdateCheckResult.NoCompatibleApk -> _uiState.value = UpdateUiState.Error(context.getString(R.string.no_compatible_apk_title))
                is UpdateCheckResult.Error -> _uiState.value = UpdateUiState.Error(result.message)
            }
        }
    }

    private suspend fun showAvailable() {
        val info = activeUpdateInfo ?: return
        val ignored = settingsRepository.settingsFlow.first().ignoredVersion == info.versionName
        _uiState.value = UpdateUiState.UpdateAvailable(currentVersionName, info, ignored)
    }

    fun startDownload() {
        val info = activeUpdateInfo ?: return
        if (downloadJob?.isActive == true || installJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            settingsRepository.updateIgnoredVersion(null)
            downloadedApkFile = null
            _uiState.value = UpdateUiState.Downloading(info, 0, 0, info.apkSize)
            apkDownloadManager.downloadApk(info)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        apkDownloadManager.cancelDownload()
        viewModelScope.launch { showAvailable() }
    }

    private fun showReady(info: AppUpdateInfo, file: File) {
        _uiState.value = if (PackageInstallerHelper.canRequestPackageInstalls(context)) {
            UpdateUiState.ReadyToInstall(info, file)
        } else UpdateUiState.PermissionRequired(info, file)
    }

    fun installUpdate() {
        val file = downloadedApkFile ?: return
        val info = activeUpdateInfo ?: return
        if (installJob?.isActive == true) return
        if (!PackageInstallerHelper.canRequestPackageInstalls(context)) {
            _uiState.value = UpdateUiState.PermissionRequired(info, file)
            return
        }
        awaitingPermission = false
        installJob = viewModelScope.launch {
            _uiState.value = UpdateUiState.OpeningInstaller(info)
            val error = withContext(Dispatchers.IO) { PackageInstallerHelper.validationError(context, file, info.versionName) }
            _uiState.value = when {
                error != null -> UpdateUiState.Error(error, info)
                PackageInstallerHelper.installApk(context, file) -> UpdateUiState.InstallerOpened(info)
                else -> UpdateUiState.Error(context.getString(R.string.updater_installer_failed), info, retryInstall = true)
            }
        }
    }

    fun openSettingsForPermission() {
        awaitingPermission = true
        if (!PackageInstallerHelper.openUnknownAppSourcesSettings(context)) {
            awaitingPermission = false
            _uiState.value = UpdateUiState.Error(context.getString(R.string.updater_permission_failed), activeUpdateInfo, retryInstall = true)
        }
    }

    fun onResume() {
        if (awaitingPermission) {
            awaitingPermission = false
            if (PackageInstallerHelper.canRequestPackageInstalls(context)) installUpdate()
        } else if (_uiState.value is UpdateUiState.InstallerOpened) {
            val info = activeUpdateInfo ?: return
            val file = downloadedApkFile ?: return
            showReady(info, file)
        }
    }

    fun retry() {
        val error = _uiState.value as? UpdateUiState.Error ?: return
        when {
            error.retryInstall -> installUpdate()
            error.updateInfo != null -> startDownload()
            else -> checkForUpdates(force = true)
        }
    }

    fun ignoreVersion() {
        val info = activeUpdateInfo ?: return
        viewModelScope.launch {
            settingsRepository.updateIgnoredVersion(info.versionName)
            showAvailable()
        }
    }
}
