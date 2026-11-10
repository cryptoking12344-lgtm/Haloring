package com.ck.haloring

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager

/**
 * Event-driven: Android calls this only when a notification is posted or removed,
 * so nothing runs in between.
 */
class NotifListener : NotificationListenerService() {

    private val dialerPackages = setOf(
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.google.android.dialer",
        "com.android.server.telecom"
    )

    private fun isMissedCall(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_MISSED_CALL) return true
        var dialer: String? = null
        try {
            dialer = (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
        } catch (e: Exception) {
        }
        val pkg = sbn.packageName
        if (pkg == dialer || dialerPackages.contains(pkg)) {
            // Live calls are "ongoing" and already filtered out, so what is left is a missed call.
            return n.category != Notification.CATEGORY_CALL
        }
        return false
    }

    /** The ring colour for this notification, or null when it should not show a ring. */
    private fun colorFor(sbn: StatusBarNotification, missedCall: Boolean): Int? {
        val sp = Prefs.sp(this)
        val pkg = sbn.packageName

        if (missedCall) {
            return if (sp.getBoolean(Prefs.MISSED, true)) {
                sp.getInt(Prefs.MISSED_COLOR, Prefs.DEF_MISSED_COLOR)
            } else {
                null
            }
        }

        val allApps = sp.getBoolean(Prefs.ALL_APPS, true)
        if (!allApps && !Prefs.selectedApps(this).contains(pkg)) return null
        return Prefs.appColor(this, pkg) ?: sp.getInt(Prefs.COLOR, Prefs.DEF_COLOR)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?,
        rankingMap: NotificationListenerService.RankingMap?
    ) {
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

        val missedCall = isMissedCall(sbn)
        val color = colorFor(sbn, missedCall) ?: return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        // Our own black ring screen counts as "screen off".
        val screenOn = pm.isInteractive && RingActivity.current == null

        if (!screenOn) {
            Pending.add(sbn.key, color, missedCall)
            RingService.instance?.onNewNotification()
            return
        }

        // Screen lit but still locked (for example right after a missed call rang):
        // remember it and show the ring once the screen goes off.
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            Pending.add(sbn.key, color, missedCall)
            RingService.instance?.onDeferred()
        }
        // Otherwise the phone is in use and the notification has been seen.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (Pending.remove(sbn.key)) {
            RingService.instance?.onRemoved()
        }
    }
}
