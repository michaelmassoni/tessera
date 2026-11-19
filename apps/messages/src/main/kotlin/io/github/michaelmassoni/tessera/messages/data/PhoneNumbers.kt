package io.github.michaelmassoni.tessera.messages.data

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Formatting and checking numbers for the phone's country, with the platform's built-in
 * libphonenumber (no extra dependency).
 */
class PhoneNumbers(
    context: Context,
) {
    /** The SIM's country, else the network's, else the locale's: "AU". */
    val country: String =
        context
            .getSystemService(TelephonyManager::class.java)
            ?.let { t -> t.simCountryIso.ifBlank { t.networkCountryIso } }
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
            ?: Locale.getDefault().country

    /** "0412345678" → "0412 345 678"; null if it can't be laid out. */
    fun format(dialable: String): String? = PhoneNumberUtils.formatNumber(dialable, country)

    /** A complete, valid number for [country] (or any country, with a +). */
    fun isValid(dialable: String): Boolean = PhoneNumberUtils.formatNumberToE164(dialable, country) != null
}
