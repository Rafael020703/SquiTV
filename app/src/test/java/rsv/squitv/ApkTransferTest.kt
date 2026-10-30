package rsv.squitv

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.data.update.ApkDownloadManager
import rsv.squitv.data.update.DownloadStatus
import rsv.squitv.domain.model.AppUpdateInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ApkTransferTest {
    @get:Rule val temp = TemporaryFolder()
    private fun context(): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getCacheDir() = temp.root
    }
    private fun info(port: Int) = AppUpdateInfo("1.20", releaseName = "Squi TV 1.20", tagName = "v1.20", publishedAt = "", changelog = "", releaseUrl = "https://github.com/Rafael020703/SquiTV", apkUrl = "http://127.0.0.1:$port/update.apk", apkName = "SquiTV.apk", apkSize = 100)
    private fun manager() = ApkDownloadManager(context(), OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build())
    private fun noTemporaryFile() = assertTrue(temp.root.walkTopDown().none { it.extension == "tmp" })
    private fun serve(server: ServerSocket, response: String, waitForDisconnect: Boolean = false, ready: CountDownLatch = CountDownLatch(0), disconnected: CountDownLatch = CountDownLatch(0)) = thread(isDaemon = true) {
        try {
            server.accept().use { socket ->
                socket.soTimeout = 10_000
                val reader = socket.getInputStream().bufferedReader()
                while (reader.readLine()?.isNotEmpty() == true) { }
                socket.getOutputStream().apply { write(response.toByteArray(Charsets.US_ASCII)); flush() }
                ready.countDown()
                if (waitForDisconnect) socket.getInputStream().read()
            }
        } finally { disconnected.countDown(); server.close() }
    }
    @Test fun httpFailure() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val worker = serve(server, "HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        val manager = manager()
        manager.downloadApk(info(port))
        worker.join(2_000)
        assertTrue(manager.downloadStatus.value is DownloadStatus.Failed)
        noTemporaryFile()
    }
    @Test fun truncatedBody() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val worker = serve(server, "HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nxx")
        val manager = manager()
        manager.downloadApk(info(port))
        worker.join(2_000)
        assertTrue(manager.downloadStatus.value is DownloadStatus.Failed)
        noTemporaryFile()
    }
    @Test fun cancelConnection() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val ready = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val worker = serve(server, "HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nx", true, ready, disconnected)
        val manager = manager()
        val job = launch(Dispatchers.IO) { manager.downloadApk(info(port)) }
        try {
            assertTrue(withContext(Dispatchers.IO) { ready.await(5, TimeUnit.SECONDS) })
            manager.cancelDownload()
            withTimeout(5_000) { job.join() }
            assertTrue(withContext(Dispatchers.IO) { disconnected.await(3, TimeUnit.SECONDS) })
            assertEquals(DownloadStatus.Idle, manager.downloadStatus.value)
            noTemporaryFile()
        } finally { job.cancelAndJoin(); server.close(); worker.join(2_000) }
    }
}
