package om.android.mobiled.comman

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.mobiled.android.base.model.HardwareDevice
import java.net.InetAddress


fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}


fun List<HardwareDevice>.hasIp(ip: String?): Boolean {
    this.forEachIndexed { index, hardwareDevice ->
        if (hardwareDevice.ip?.equals(ip ?: "") == true) {
            return true
        }
    }
    return false
}

fun List<HardwareDevice>.hasSameAPName(APName: String?): Boolean {
    this.forEachIndexed { index, hardwareDevice ->
        if (hardwareDevice.ApName?.equals(APName ?: "") == true) {
            return true
        }
    }
    return false
}

fun List<HardwareDevice>.hasSameAPNameAndIp(device: HardwareDevice?): Boolean {
    this.forEachIndexed { index, hardwareDevice ->
        if (hardwareDevice.ApName?.equals(device?.ApName ?: "") == true) {
            if (hardwareDevice.ip?.equals(device?.ip ?: "") == true) {
                return true
            }
        }
    }
    return false
}

fun ImageView.setImageDrawableWithAnimation(drawable: BitmapDrawable, drawable2: BitmapDrawable, duration: Int = 300) {
    val currentDrawable = getDrawable()
    if (currentDrawable == null) {
        setImageDrawable(drawable2)
        return
    }

    val transitionDrawable = TransitionDrawable(
        arrayOf(
            drawable,
            drawable2
        )
    )
    setImageDrawable(transitionDrawable)
    transitionDrawable.startTransition(duration)
}

val FragmentManager.currentNavigationFragment: Fragment?
    get() = primaryNavigationFragment?.childFragmentManager?.fragments?.first()


fun Context.getBitmapDrawableFromVectorDrawable(drawableId: Int): BitmapDrawable? {
    return BitmapDrawable(this.resources, getBitmapFromVectorDrawable(drawableId))
}

fun Context.getBitmapFromVectorDrawable(drawableId: Int): Bitmap? {
    var drawable = ContextCompat.getDrawable(this, drawableId)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        drawable = DrawableCompat.wrap(drawable!!).mutate()
    }
    val bitmap = Bitmap.createBitmap(
        drawable!!.intrinsicWidth,
        drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun InetAddress.toInt() : Int
{
    val addressBytes = this.address
    var result = 0
    for (i in addressBytes.indices) {
        result = result shl 8 or (addressBytes[i].toInt() and 0xff)
    }
    return result
}

fun Int.toInetAddress() : InetAddress
{
    val addressBytes = ByteArray(4)
    addressBytes[0] = (this shr 24 and 0xff).toByte()
    addressBytes[1] = (this shr 16 and 0xff).toByte()
    addressBytes[2] = (this shr 8 and 0xff).toByte()
    addressBytes[3] = (this and 0xff).toByte()
    return InetAddress.getByAddress(addressBytes)
}