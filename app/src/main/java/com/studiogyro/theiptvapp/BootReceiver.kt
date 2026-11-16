package com.studiogyro.theiptvapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Au démarrage d'Android, ouvre l'appli si l'option « Démarrer au démarrage d'Android » est active. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = Store(context)
        val shouldLaunch = when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> store.bootStart()
            Intent.ACTION_USER_PRESENT -> store.wakeStart()
            else -> false
        }
        if (!shouldLaunch) return
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}
