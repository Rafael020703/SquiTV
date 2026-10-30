package rsv.squitv

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.data.update.PackageInstallerHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ApkIdentityTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var incoming: PackageInfo
    private lateinit var apk: File
    private val signer = Signature("0102")
    private fun signing(current: Signature, history: Array<Signature> = arrayOf(current)): SigningInfo = mock(SigningInfo::class.java).also {
        `when`(it.apkContentsSigners).thenReturn(arrayOf(current))
        `when`(it.signingCertificateHistory).thenReturn(history)
        `when`(it.hasMultipleSigners()).thenReturn(false)
    }
    @Before fun setup() {
        val real = ApplicationProvider.getApplicationContext<Context>()
        context = mock(Context::class.java)
        val pm = mock(PackageManager::class.java)
        `when`(context.packageName).thenReturn("rsv.squitv")
        `when`(context.packageManager).thenReturn(pm)
        `when`(context.getString(anyInt())).thenAnswer { real.getString(it.getArgument(0)) }
        val installed = PackageInfo().apply {
            packageName = "rsv.squitv"; versionName = "1.19"; longVersionCode = 20; signingInfo = signing(signer)
        }
        incoming = PackageInfo().apply {
            packageName = "rsv.squitv"; versionName = "1.20"; longVersionCode = 21; signingInfo = signing(signer)
        }
        `when`(pm.getPackageInfo(eq("rsv.squitv"), anyInt())).thenReturn(installed)
        `when`(pm.getPackageArchiveInfo(anyString(), anyInt())).thenReturn(incoming)
        apk = temp.newFile("update.apk").apply { writeBytes(byteArrayOf(1)) }
    }
    @Test fun validIdentity() { assertNull(PackageInstallerHelper.validationError(context, apk, "1.20")) }
    @Test fun packageMismatch() {
        incoming.packageName = "rsv.squitv.debug"
        assertEquals(context.getString(R.string.updater_wrong_package), PackageInstallerHelper.validationError(context, apk, "1.20"))
    }
    @Test fun advertisedVersion() {
        incoming.versionName = "1.21"
        assertEquals(context.getString(R.string.updater_wrong_version), PackageInstallerHelper.validationError(context, apk, "1.20"))
    }
    @Test fun versionCode() {
        incoming.longVersionCode = 20
        assertEquals(context.getString(R.string.updater_old_apk), PackageInstallerHelper.validationError(context, apk, "1.20"))
    }
    @Test fun unrelatedSigner() {
        incoming.signingInfo = signing(Signature("0304"))
        assertEquals(context.getString(R.string.updater_wrong_signature), PackageInstallerHelper.validationError(context, apk, "1.20"))
    }
    @Test fun signerRotation() {
        incoming.signingInfo = signing(Signature("0304"), arrayOf(signer, Signature("0304")))
        assertNull(PackageInstallerHelper.validationError(context, apk, "1.20"))
    }
}
