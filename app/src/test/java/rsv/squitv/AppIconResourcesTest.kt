package rsv.squitv

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIconResourcesTest {
    private fun drawable(id: Int): Drawable =
        ApplicationProvider.getApplicationContext<Application>().getDrawable(id)!!
    private fun render(id: Int, size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            drawable(id).apply { setBounds(0, 0, size, size); draw(Canvas(it)) }
        }
    @Test @Config(sdk = [24])
    fun legacyLaunchersReceiveRealBrandedBitmaps() {
        val icon = drawable(R.mipmap.ic_launcher)
        assertTrue(icon is BitmapDrawable)
        val bitmap = (icon as BitmapDrawable).bitmap
        val center = bitmap.getPixel(bitmap.width / 2, (bitmap.height * .53f).toInt())
        assertTrue(Color.red(center) > 220 && Color.green(center) < 180 && Color.blue(center) < 100)
        assertTrue(drawable(R.mipmap.ic_launcher_round) is BitmapDrawable)
    }
    @Test fun adaptiveLaunchersReceiveBothLayers() {
        val icon = drawable(R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        assertNotNull((icon as AdaptiveIconDrawable).foreground)
        assertNotNull(icon.background)
    }
    @Test fun themedIconHasNoOpaqueDarkPaintInsideItsMask() {
        val bitmap = render(R.drawable.ic_launcher_monochrome, 108)
        assertEquals(0, Color.alpha(bitmap.getPixel(40, 54)))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertTrue(Color.alpha(bitmap.getPixel(54, 57)) > 0)
    }
    @Test fun smallNotificationIconHasAnOpenScreenAndTransparentBackground() {
        val bitmap = render(R.drawable.ic_notification, 24)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(6, 12)))
        assertTrue(Color.alpha(bitmap.getPixel(12, 12)) > 0)
    }
    @Test fun tvBannerKeepsTheSixteenByNineAspectRatio() {
        val banner = drawable(R.drawable.banner_tv)
        assertEquals(16f / 9f, banner.intrinsicWidth.toFloat() / banner.intrinsicHeight, .01f)
    }
}
