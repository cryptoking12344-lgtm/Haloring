package com.ck.haloring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires on the reminder alarm while notifications are still unseen. */
class RepeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RingService.instance?.onRepeat()
    }
}
