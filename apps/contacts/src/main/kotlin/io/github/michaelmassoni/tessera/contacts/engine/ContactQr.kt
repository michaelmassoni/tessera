package io.github.michaelmassoni.tessera.contacts.engine

import io.github.michaelmassoni.tessera.core.dav.ICal

/**
 * The vCard shown as a QR code (Share as QR code; docs/contacts.md): name, numbers, emails,
 * addresses, company, job title and websites. No photo or note, so it fits a code a phone camera
 * reads easily. Phone and email types use the ContactsContract values (1 home, 2 mobile/work, 3 work).
 */
object ContactQr {
    fun vcard(detail: ContactDetail): String =
        buildString {
            append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            val form = detail.form
            if (form.givenName.isNotBlank() || form.familyName.isNotBlank()) {
                line("N", "${ICal.escape(form.familyName)};${ICal.escape(form.givenName)};;;")
            }
            line("FN", ICal.escape(detail.name))
            detail.phones.forEach { line(phoneType(it.type)?.let { t -> "TEL;TYPE=$t" } ?: "TEL", ICal.escape(it.value)) }
            detail.emails.forEach { line(emailType(it.type)?.let { t -> "EMAIL;TYPE=$t" } ?: "EMAIL", ICal.escape(it.value)) }
            // The whole address goes in the street field: it's stored formatted, not in parts.
            detail.addresses.forEach { line("ADR", ";;${ICal.escape(it.value)};;;;") }
            if (detail.company.isNotBlank()) line("ORG", ICal.escape(detail.company))
            if (detail.title.isNotBlank()) line("TITLE", ICal.escape(detail.title))
            detail.websites.forEach { line("URL", it) }
            append("END:VCARD\r\n")
        }

    private fun StringBuilder.line(
        name: String,
        value: String,
    ) {
        append(name).append(':').append(value).append("\r\n")
    }

    private fun phoneType(type: Int) =
        when (type) {
            1 -> "HOME"
            2 -> "CELL"
            3 -> "WORK"
            else -> null
        }

    private fun emailType(type: Int) =
        when (type) {
            1 -> "HOME"
            2 -> "WORK"
            else -> null
        }
}
