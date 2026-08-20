package dev.shivampingale.vcport

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * USB host permission + mass-storage list. Never auto-mounts on plug-in.
 * Feature idea from OTG Master by moylali (https://github.com/moylali/OTGMaster).
 */
object OtgUsb {
    const val ACTION_PERMISSION = "dev.shivampingale.vcport.USB_PERMISSION"

    fun manager(context: Context): UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun massStorageDevices(context: Context): List<UsbDevice> {
        return manager(context).deviceList.values.filter { OtgScsiDevice.isMassStorage(it) }
    }

    fun requestPermission(context: Context, device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
        val pi = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        manager(context).requestPermission(device, pi)
    }

    fun hasPermission(context: Context, device: UsbDevice): Boolean =
        manager(context).hasPermission(device)

    fun registerPermissionReceiver(context: Context, onGranted: (UsbDevice) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_PERMISSION) return
                val device = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                } ?: return
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    onGranted(device)
                }
            }
        }
        val filter = IntentFilter(ACTION_PERMISSION)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return receiver
    }
}
