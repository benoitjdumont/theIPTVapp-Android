package com.studiogyro.theiptvapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Profils (jusqu'à 4) : chacun a ses propres listes, favoris, historique et réglages.
 * Le profil principal garde les données d'origine. Changer de profil redémarre l'appli.
 * Les enregistrements, le DVR et les enregistrements programmés restent communs à tous les profils.
 */
object Profiles {
    const val MAX = 4
    private const val BOOT = "profiles_boot"

    data class P(val id: String, val name: String)

    private fun boot(ctx: Context) = ctx.getSharedPreferences(BOOT, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<P> {
        val out = try {
            val a = JSONArray(boot(ctx).getString("list", "[]") ?: "[]")
            (0 until a.length()).map { i -> a.getJSONObject(i).let { P(it.getString("id"), it.getString("name")) } }
        } catch (e: Exception) { emptyList() }
        return out.ifEmpty { listOf(P("p0", "Principal")) }
    }

    private fun save(ctx: Context, l: List<P>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        boot(ctx).edit().putString("list", a.toString()).apply()
    }

    fun activeId(ctx: Context): String {
        val a = boot(ctx).getString("active", "p0") ?: "p0"
        return if (list(ctx).any { it.id == a }) a else "p0"
    }

    /** fichier de préférences du profil actif (le profil principal garde l'ancien fichier) */
    fun prefsName(ctx: Context): String = activeId(ctx).let { if (it == "p0") "theiptvapp" else "theiptvapp_$it" }

    fun add(ctx: Context, name: String): Boolean {
        val l = list(ctx)
        if (name.isBlank() || l.size >= MAX) return false
        save(ctx, l + P("p${System.currentTimeMillis()}", name.trim()))
        return true
    }

    fun rename(ctx: Context, id: String, name: String) {
        if (name.isBlank()) return
        save(ctx, list(ctx).map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    /** supprime un profil non actif et non principal, avec toutes ses données */
    fun delete(ctx: Context, id: String): Boolean {
        val l = list(ctx)
        if (id == "p0" || id == activeId(ctx) || l.size <= 1) return false
        save(ctx, l.filterNot { it.id == id })
        val name = "theiptvapp_$id"
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        File(ctx.applicationInfo.dataDir, "shared_prefs/$name.xml").delete()
        return true
    }

    /** passe à un autre profil : on redémarre l'appli pour qu'elle charge ses données */
    fun switchTo(ctx: Context, id: String) {
        boot(ctx).edit().putString("active", id).commit()
        restart(ctx)
    }

    private fun restart(ctx: Context) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pi = PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 400, pi)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
