package dev.shivampingale.vcport

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Optional Quick Settings tile. Wipes session leftovers and asks MainActivity
 * to close volumes. Does not claim unbreakable. Does not add INTERNET.
 */
class PanicTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "VC Port panic"
            updateTile()
        }
    }

    override fun onClick() {
        Hardening.panic(applicationContext)
        val launch = Intent(this, MainActivity::class.java).apply {
            action = PanicIntents.ACTION
            putExtra(PanicIntents.EXTRA, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, launch, flags))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }
}
