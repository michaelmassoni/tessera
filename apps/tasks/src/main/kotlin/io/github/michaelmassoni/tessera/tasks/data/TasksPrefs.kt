package io.github.michaelmassoni.tessera.tasks.data

import android.content.Context
import androidx.core.content.edit

/** The app's own settings, in `settings.xml` (backed up with the tasks). */
object TasksPrefs {
    private const val FILE = "settings"
    private const val STARS = "starred_tasks"

    /** Stars on tasks, the Starred view and "starred first" sorting; off hides them all. */
    fun stars(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(STARS, true)

    fun setStars(
        context: Context,
        on: Boolean,
    ) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(STARS, on) }
}
