package rsv.squitv.desktop

import java.awt.Dialog
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Platform adapter for Android's CreateDocument/OpenDocument flow. Null means cancellation. */
internal suspend fun chooseDesktopBackupFile(owner: Window, save: Boolean): Path? = suspendCancellableCoroutine { result ->
    EventQueue.invokeLater {
        if (!result.isActive) return@invokeLater
        var dialog: FileDialog? = null
        try {
            val title = if (save) "Exportar configurações do Squi TV" else "Restaurar configurações do Squi TV"
            val mode = if (save) FileDialog.SAVE else FileDialog.LOAD
            val picker = when (owner) {
                is Frame -> FileDialog(owner, title, mode)
                is Dialog -> FileDialog(owner, title, mode)
                else -> error("Unsupported backup window")
            }
            dialog = picker
            result.invokeOnCancellation { EventQueue.invokeLater { picker.dispose() } }
            picker.isMultipleMode = false
            picker.file = if (save) "SquiTV-Windows-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json" else "*.json"
            if (result.isActive) {
                picker.isVisible = true
                val name = picker.file
                result.resume(if (name == null) null else Path.of(picker.directory, name).toAbsolutePath().normalize())
            }
        } catch (error: Exception) {
            if (result.isActive) result.resumeWithException(error)
        } finally { dialog?.dispose() }
    }
}
