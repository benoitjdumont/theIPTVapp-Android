package com.studiogyro.theiptvapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/** Enregistrement programmé d'une émission du guide (heures en ms). */
data class Sched(val id: String, val chName: String, val url: String, val title: String, val start: Long, val stop: Long)

/** Liste des enregistrements programmés, sauvegardée et armée avec AlarmManager (survit au redémarrage via BootReceiver). */
object Scheduler {
    private const val PREFS = "scheduled"

    fun list(ctx: Context): List<Sched> = try {
        val a = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { Sched(it.getString("id"), it.getString("ch"), it.getString("url"), it.getString("t"), it.getLong("s"), it.getLong("e")) }
        }
    } catch (e: Exception) { emptyList() }

    private fun save(ctx: Context, l: List<Sched>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("ch", it.chName).put("url", it.url).put("t", it.title).put("s", it.start).put("e", it.stop)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", a.toString()).apply()
    }

    fun add(ctx: Context, s: Sched) {
        save(ctx, list(ctx).filter { it.id != s.id } + s)
        arm(ctx, s)
    }

    fun cancel(ctx: Context, id: String) {
        save(ctx, list(ctx).filter { it.id != id })
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx, id))
    }

    /** émission terminée : on la retire de la liste (sans toucher à l'alarme déjà passée) */
    fun done(ctx: Context, id: String) = save(ctx, list(ctx).filter { it.id != id })

    /** au démarrage d'Android les alarmes sont perdues : on les remet et on oublie ce qui est passé */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        val all = list(ctx)
        save(ctx, all.filter { it.stop > now })
        all.filter { it.stop > now }.forEach { arm(ctx, it) }
    }

    private fun arm(ctx: Context, s: Sched) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // une minute d'avance ; setAndAllowWhileIdle ne demande aucune permission d'alarme exacte
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(System.currentTimeMillis(), s.start - 60_000L), pending(ctx, s.id))
    }

    private fun pending(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, SchedReceiver::class.java).setData(Uri.parse("sched:$id"))
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** L'alarme sonne : on lance le service qui enregistre jusqu'à la fin de l'émission. */
class SchedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.data?.schemeSpecificPart ?: return
        val s = Scheduler.list(context).firstOrNull { it.id == id } ?: return
        val svc = Intent(context, RecordService::class.java)
            .putExtra("id", s.id).putExtra("ch", s.chName).putExtra("url", s.url)
            .putExtra("t", s.title).putExtra("s", s.start).putExtra("e", s.stop)
        runCatching { ContextCompat.startForegroundService(context, svc) }
    }
}
