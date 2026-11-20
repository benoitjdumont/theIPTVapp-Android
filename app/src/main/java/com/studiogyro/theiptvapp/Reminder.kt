package com.studiogyro.theiptvapp

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Rappel avant le début d'une émission : une notification quelques minutes avant. */
object Reminder {
    private const val LEAD_MS = 2 * 60_000L

    /** Programme le rappel ; renvoie false si l'émission a déjà commencé. */
    fun schedule(ctx: Context, chan: String, p: Prog): Boolean {
        val now = System.currentTimeMillis()
        if (p.start <= now) return false
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("title", p.title).putExtra("chan", chan)
        val code = ((p.start / 1000) % Int.MAX_VALUE).toInt()
        val pi = PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = (p.start - LEAD_MS).coerceAtLeast(now + 1000)
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC_WAKEUP, at, pi)
        return true
    }
}

/** Affiche la notification de rappel au moment prévu. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val chan = intent.getStringExtra("chan").orEmpty()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("rappel", "Rappels d'émissions", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "rappel") else Notification.Builder(ctx)
        val n = builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Bientôt : $title")
            .setContentText(chan)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(title.hashCode(), n)
    }
}
