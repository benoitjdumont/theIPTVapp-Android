package com.studiogyro.theiptvapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Au démarrage d'Android, ouvre l'appli si l'option « Démarrer au démarrage d'Android » est active. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!Store(context).bootStart()) return
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
