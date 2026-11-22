package io.github.michaelmassoni.tessera.tasks.data

import android.content.Context
import io.github.michaelmassoni.tessera.core.dav.DavAccountStore

/**
 * Tasks' CalDAV account, in `caldav.xml` (left out of every backup), with the password encrypted
 * by a Keystore key. The names are the ones Tasks 0.1 used, so an existing sign-in carries over.
 */
class AccountStore(
    context: Context,
) : DavAccountStore(context, FILE, "tasks_caldav_password") {
    companion object {
        const val FILE = "caldav"
    }
}
