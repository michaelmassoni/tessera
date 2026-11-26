package io.github.michaelmassoni.tessera.contacts.engine

/**
 * A contact can be made of several raw contacts: the person's own (the device, a CardDAV or
 * Google account) and copies other apps add (Signal, WhatsApp, Telegram), which those apps
 * overwrite. Two rules for showing and editing such a contact. Pure, so it's unit tested.
 */
object RawChoice {
    /**
     * The raw contact edits go to: the first in one of the person's own accounts (whose sync
     * adapter uploads changes, or the device), else the first. Null when there are none.
     * [appTypes] are the account types whose sync adapter only mirrors (doesn't upload changes).
     */
    fun editable(
        raws: Map<Long, AccountRef>,
        appTypes: Set<String>,
    ): Long? = raws.entries.firstOrNull { (_, a) -> a.isDevice || a.type !in appTypes }?.key ?: raws.keys.firstOrNull()

    /**
     * Each value once (the same number written differently, the same email in another case),
     * the row from [preferredRaw] kept when there's a choice, otherwise the first; in the original order.
     */
    fun distinct(
        values: List<Labeled>,
        rawOf: (Labeled) -> Long?,
        preferredRaw: Long?,
        key: (String) -> String,
    ): List<Labeled> {
        val chosen = LinkedHashMap<String, Labeled>()
        for (v in values) {
            val k = key(v.value)
            val kept = chosen[k]
            if (kept == null || (rawOf(kept) != preferredRaw && rawOf(v) == preferredRaw)) chosen[k] = v
        }
        val keep = chosen.values.toSet()
        return values.filter { it in keep }
    }

    /** Phone numbers match on their last nine digits (national and international forms). */
    fun phoneKey(number: String): String = number.filter(Char::isDigit).takeLast(9).ifEmpty { number.trim() }

    fun emailKey(address: String): String = address.trim().lowercase()
}
