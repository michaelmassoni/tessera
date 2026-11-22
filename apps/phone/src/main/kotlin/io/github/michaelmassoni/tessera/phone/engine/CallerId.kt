package io.github.michaelmassoni.tessera.phone.engine

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

/**
 * Where a number is from, worked out on the phone with libphonenumber's offline data: nothing
 * is looked up online. [home] is the phone's country ("AU").
 */
class CallerLocation(
    private val home: String,
    private val locale: Locale = Locale.getDefault(),
) {
    private val util = PhoneNumberUtil.getInstance()
    private val geocoder = PhoneNumberOfflineGeocoder.getInstance()

    /**
     * A landline's area ("Sydney NSW") or, for a number from another country, that country
     * ("United Kingdom"). Null when there's nothing useful to say: a mobile or service number
     * from this country (they aren't tied to a place), or a number that doesn't parse.
     */
    fun describe(number: String): String? {
        val parsed = parse(number) ?: return null
        if (!util.isValidNumber(parsed)) return null
        val region = util.getRegionCodeForNumber(parsed) ?: return null
        // The place within its country ("Melbourne", "London"), or just the country name when the
        // data has nothing finer.
        val place = geocoder.getDescriptionForNumber(parsed, locale).takeIf { it.isNotBlank() }
        val country =
            Locale
                .Builder()
                .setRegion(region)
                .build()
                .getDisplayCountry(locale)
        val local = place?.takeUnless { it == country }
        return when {
            region == home -> local
            local != null -> "$local, $country"
            else -> country.ifBlank { null }
        }
    }

    private fun parse(number: String): PhoneNumber? =
        try {
            util.parse(number, home)
        } catch (_: NumberParseException) {
            null
        }
}

/**
 * Numbers the person reported as spam, stored on the phone only. Numbers are compared in E.164
 * ("+61455000111") so "0455 000 111" and "+61 455 000 111" are the same number.
 */
object SpamNumbers {
    private val util = PhoneNumberUtil.getInstance()

    fun key(
        number: String,
        home: String,
    ): String? =
        try {
            util.format(util.parse(number, home), PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (_: NumberParseException) {
            digits(number).ifEmpty { null }
        }

    fun isReported(
        number: String,
        reported: Set<String>,
        home: String,
    ): Boolean = key(number, home)?.let { it in reported } ?: false
}
