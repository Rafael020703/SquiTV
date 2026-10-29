package rsv.squitv.ui.viewmodel.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rsv.squitv.R
import rsv.squitv.data.model.SettingsBackup
import rsv.squitv.data.model.BackupOption
import rsv.squitv.data.repository.SettingsBackupRepository
import rsv.squitv.data.model.SettingsBackupCodec
import rsv.squitv.data.repository.SettingsRepository
import timber.log.Timber

data class SettingsBackupUiState(
    val busy: Boolean = false,
    val preview: SettingsBackup? = null,
    val matchedAccounts: Int = 0,
    val skippedAccounts: Int = 0,
    val notice: Int? = null,
    val exportSelected: Set<BackupOption> = BackupOption.entries.toSet(),
    val importSelected: Set<BackupOption> = emptySet()
)

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val backupRepository: SettingsBackupRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsBackupUiState())
    val state = _state.asStateFlow()

    fun clearNotice() { _state.update { it.copy(notice = null) } }
    fun cancelPreview() { if (!_state.value.busy) _state.update { it.copy(preview = null) } }
    fun pickerUnavailable() { _state.update { it.copy(notice = R.string.settings_backup_picker_unavailable) } }

    fun exportTo(uri: Uri) = perform(R.string.settings_backup_export_error) {
        val content = backupRepository.export(_state.value.exportSelected).toByteArray(Charsets.UTF_8)
        require(content.size <= SettingsBackupCodec.MAX_BYTES)
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
        stream.use { it.write(content); it.flush() }
        _state.update { it.copy(notice = R.string.settings_backup_export_success) }
    }

    fun readFrom(uri: Uri) = perform(R.string.settings_backup_import_error) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream")
        val text = stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                total += size
                require(total <= SettingsBackupCodec.MAX_BYTES)
                output.write(buffer, 0, size)
            }
            output.toString(Charsets.UTF_8.name())
        }
        val backup = SettingsBackupCodec.decode(text)
        val configured = SettingsBackupCodec.configuredAccounts(repository.settingsFlow.first())
        val matched = backup.accounts.count { it.fingerprint in configured }
        val available = SettingsBackupCodec.available(backup)
        require(available.isNotEmpty())
        _state.update { it.copy(preview = backup, matchedAccounts = matched,
            skippedAccounts = backup.accounts.size - matched, notice = null, importSelected = available) }
    }

    fun importPreview() {
        val backup = _state.value.preview ?: return
        perform(R.string.settings_backup_apply_error) {
            val result = backupRepository.import(backup, _state.value.importSelected)
            _state.update { it.copy(preview = null,
                notice = if (result.skippedAccounts > 0 || result.skippedFavorites > 0) R.string.settings_backup_import_partial
                    else R.string.settings_backup_import_success) }
        }
    }

    fun toggleOption(option: BackupOption) {
        if (_state.value.busy) return
        _state.update { state ->
            val current = if (state.preview == null) state.exportSelected else state.importSelected
            val selected = if (option in current) current - option else current + option
            if (state.preview == null) state.copy(exportSelected = selected) else state.copy(importSelected = selected)
        }
    }

    fun selectAll() {
        if (_state.value.busy) return
        _state.update { state ->
            val available = state.preview?.let(SettingsBackupCodec::available) ?: BackupOption.entries.toSet()
            val current = if (state.preview == null) state.exportSelected else state.importSelected
            val selected = if (current.containsAll(available)) emptySet() else available
            if (state.preview == null) state.copy(exportSelected = selected) else state.copy(importSelected = selected)
        }
    }

    private fun perform(error: Int, action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Settings backup failed: %s", e.javaClass.simpleName)
                _state.update { it.copy(notice = error) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
