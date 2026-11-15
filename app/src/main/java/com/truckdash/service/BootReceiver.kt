package com.truckdash.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Best-effort autostart. On Android 14+ a location foreground service may not start from
 * BOOT_COMPLETED; head-unit ROMs usually allow it, otherwise use the ROM's autostart setting.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { DriveService.start(context) }
    }
}
