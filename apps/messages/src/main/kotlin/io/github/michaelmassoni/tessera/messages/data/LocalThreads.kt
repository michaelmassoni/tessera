package io.github.michaelmassoni.tessera.messages.data

import android.app.role.RoleManager
import android.content.Context
import android.provider.Telephony
import androidx.core.content.edit

/**
 * True when Tessera Messages is the default SMS app, so it may write the message database. The
 * role is the source of truth; `Telephony.Sms.getDefaultSmsPackage` can lag behind it.
 */
fun Context.isDefaultSmsApp(): Boolean =
    getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true ||
        Telephony.Sms.getDefaultSmsPackage(this) == packageName

/**
 * Pinned and archived conversations. Android's message database has no place for these, so
 * they're kept by the app (on this device only).
 */
class LocalThreads(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("threads", Context.MODE_PRIVATE)

    val pinned: Set<Long> get() = read(PINNED)
    val archived: Set<Long> get() = read(ARCHIVED)

    fun pin(
        ids: Collection<Long>,
        on: Boolean,
    ) = write(PINNED, ids, on)

    fun archive(
        ids: Collection<Long>,
        on: Boolean,
    ) = write(ARCHIVED, ids, on)

    /** Deleted conversations drop out of both lists. */
    fun forget(ids: Collection<Long>) {
        write(PINNED, ids, false)
        write(ARCHIVED, ids, false)
    }

    private fun read(key: String) =
        prefs
            .getStringSet(key, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    @Synchronized
    private fun write(
        key: String,
        ids: Collection<Long>,
        on: Boolean,
    ) {
        val current = read(key)
        val next = if (on) current + ids else current - ids.toSet()
        if (next != current) prefs.edit { putStringSet(key, next.map(Long::toString).toSet()) }
    }

    private companion object {
        const val PINNED = "pinned"
        const val ARCHIVED = "archived"
    }
}

/** The conversation on screen, if any: its notifications are suppressed and it's marked read. */
object Foreground {
    @Volatile var openThread: Long? = null
}
