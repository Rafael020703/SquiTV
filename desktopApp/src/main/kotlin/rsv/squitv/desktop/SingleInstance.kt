package rsv.squitv.desktop

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT

/** A session-local Windows kernel object survives for the process lifetime, across portable builds. */
internal class SingleInstance private constructor(private val handle: WinNT.HANDLE) : AutoCloseable {
    override fun close() { Kernel32.INSTANCE.CloseHandle(handle) }

    companion object {
        fun acquire(): SingleInstance? {
            val kernel = Kernel32.INSTANCE
            val handle = kernel.CreateMutex(null, false, "Local\\SquiTV-${DesktopBuildInfo.DATA_FOLDER}")
                ?: error("Não foi possível reservar a instância do aplicativo.")
            if (kernel.GetLastError() == WinError.ERROR_ALREADY_EXISTS) {
                kernel.CloseHandle(handle)
                // Bring the existing app forward without restoring/resizing it out of fullscreen.
                User32.INSTANCE.FindWindow(null, DesktopBuildInfo.APP_NAME)?.let {
                    User32.INSTANCE.SetForegroundWindow(it)
                }
                return null
            }
            return SingleInstance(handle)
        }
    }
}
