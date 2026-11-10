package com.ck.haloring

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Event-driven: Android calls this only when a notification is posted or removed,
 * so nothing runs in between.
 */
class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: NotificationListenerService.RankingMap?) {
        if (sbn == null) return
        if (sbn.isOngoing) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // Skip silent notifications.
        if (rankingMap != null) {
            val ranking = NotificationListenerService.Ranking()
            if (rankingMap.getRanking(sbn.key, ranking) &&
                ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
            ) return
        }

        // Only notifications that arrive while the screen is off count.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) return

        Pending.keys.add(sbn.key)
        RingService.instance?.update(true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (Pending.keys.remove(sbn.key)) {
            RingService.instance?.update(false)
        }
    }
}
