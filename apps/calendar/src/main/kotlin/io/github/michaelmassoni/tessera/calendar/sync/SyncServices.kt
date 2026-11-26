package io.github.michaelmassoni.tessera.calendar.sync

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import io.github.michaelmassoni.tessera.calendar.SettingsActivity
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory

/**
 * The "Tessera Calendar" account type. Adding an account from system Settings opens the app's
 * Settings to sign in; there are no tokens (the password is kept encrypted by the app).
 */
class AuthenticatorService : Service() {
    private val authenticator by lazy { Authenticator(this) }

    override fun onBind(intent: Intent?): IBinder? = authenticator.iBinder

    private class Authenticator(
        private val context: Context,
    ) : AbstractAccountAuthenticator(context) {
        override fun addAccount(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
            authTokenType: String?,
            requiredFeatures: Array<out String>?,
            options: Bundle?,
        ): Bundle =
            Bundle().apply {
                putParcelable(
                    AccountManager.KEY_INTENT,
                    SettingsCategory
                        .intent(
                            context,
                            SettingsActivity::class.java,
                            SettingsActivity.SYNC,
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

        override fun editProperties(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
        ): Bundle? = null

        override fun confirmCredentials(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            options: Bundle?,
        ): Bundle? = null

        override fun getAuthToken(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?,
        ): Bundle? = null

        override fun getAuthTokenLabel(authTokenType: String?): String? = null

        override fun updateCredentials(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?,
        ): Bundle? = null

        override fun hasFeatures(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            features: Array<out String>?,
        ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
    }
}

/**
 * The calendar sync adapter. The system calls it when an app changes the account's events
 * ("immediately on local changes", when on) or someone taps Sync in system Settings; it hands
 * the sync to WorkManager, which keeps to the Wi-Fi setting and runs one sync at a time.
 */
class CalendarSyncService : Service() {
    private val adapter by lazy { Adapter(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = adapter.syncAdapterBinder

    private class Adapter(
        context: Context,
    ) : AbstractThreadedSyncAdapter(context, true) {
        override fun onPerformSync(
            account: Account?,
            extras: Bundle?,
            authority: String?,
            provider: ContentProviderClient?,
            syncResult: SyncResult?,
        ) {
            val local = extras?.getBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD) == true
            if (local) {
                // A burst of edits makes one sync a few seconds after the last.
                CalendarSyncWorker.schedule.afterLocalChange(context)
            } else {
                CalendarSyncWorker.schedule.soon(context, delaySeconds = 0)
            }
        }
    }
}
