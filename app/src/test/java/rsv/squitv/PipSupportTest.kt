package rsv.squitv

import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import rsv.squitv.util.findActivity
import rsv.squitv.util.supportsMediaPip

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PipSupportTest {
    @Test fun nestedContextWrappersResolveTheHostingActivity() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        assertSame(activity, ContextWrapper(ContextWrapper(activity)).findActivity())
        controller.pause().stop().destroy()
    }

    @Test fun unsupportedDevicesDoNotOfferPip() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false)
        assertFalse(context.supportsMediaPip())
    }

    @Test fun supportedMobileDevicesOfferPip() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, false)
        context.resources.configuration.uiMode = Configuration.UI_MODE_TYPE_NORMAL
        assertTrue(context.supportsMediaPip())
    }

    @Test fun tvDoesNotOfferUnsupportedEntertainmentPip() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        assertFalse(context.supportsMediaPip())
    }
}
