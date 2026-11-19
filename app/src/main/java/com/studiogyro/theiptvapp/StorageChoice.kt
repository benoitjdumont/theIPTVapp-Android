package com.studiogyro.theiptvapp

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Choix du stockage pour les enregistrements, les films enregistrés et le DVR.
 * Les clés USB apparaissent comme dossiers propres à l'appli (aucune permission spéciale).
 */
object StorageChoice {
    private const val BOOT = "storage_choice"
    data class Opt(val label: String, val root: File)

    /** volumes disponibles : stockage interne et clés USB / cartes amovibles */
    fun options(ctx: Context): List<Opt> =
        ctx.getExternalFilesDirs(Environment.DIRECTORY_MOVIES).filterNotNull().map { f ->
            val removable = runCatching { Environment.isExternalStorageRemovable(f) }.getOrDefault(false)
            Opt(if (removable) "Clé USB / amovible" else "Stockage interne", f)
        }

    /** dossier choisi s'il existe encore, sinon le stockage interne de l'appli */
    fun current(ctx: Context): File {
        val saved = ctx.getSharedPreferences(BOOT, Context.MODE_PRIVATE).getString("root", null)
        if (saved != null && File(saved).exists()) return File(saved)
        return options(ctx).firstOrNull()?.root ?: ctx.filesDir
    }

    fun set(ctx: Context, root: File) {
        ctx.getSharedPreferences(BOOT, Context.MODE_PRIVATE).edit().putString("root", root.absolutePath).apply()
    }
}
