package io.github.michaelmassoni.tessera.contacts.engine

/** Kinds of data row the editor writes. */
enum class RowKind { Name, Phone, Email, Address, Organization, Birthday, Note }

/** A change to one data row. Values are column → value in ContactsContract terms (see the repository). */
sealed interface RowOp {
    val kind: RowKind

    data class Insert(
        override val kind: RowKind,
        val values: Map<String, Any?>,
    ) : RowOp

    data class Update(
        override val kind: RowKind,
        val id: Long,
        val values: Map<String, Any?>,
    ) : RowOp

    data class Delete(
        override val kind: RowKind,
        val id: Long,
    ) : RowOp
}

/**
 * Turns an edit into the smallest set of row changes: unchanged rows aren't written, cleared
 * fields are deleted and new fields inserted. Column keys are the generic ContactsContract.Data
 * names (`data1`, `data2`, `data3`, ...), so this stays free of Android types.
 */
object EditDiff {
    const val DATA1 = "data1"
    const val DATA2 = "data2"
    const val DATA3 = "data3"

    // StructuredName: data2 = given name, data3 = family name, data1 = display name.
    // Phone/Email/Postal: data1 = value, data2 = type, data3 = custom label.
    // Organization: data1 = company. Event: data1 = date, data2 = type (3 = birthday). Note: data1.
    const val EVENT_TYPE_BIRTHDAY = 3

    fun diff(
        original: ContactForm,
        edited: ContactForm,
    ): List<RowOp> {
        val ops = mutableListOf<RowOp>()

        fun single(
            kind: RowKind,
            id: Long?,
            before: Map<String, Any?>,
            after: Map<String, Any?>,
            blank: Boolean,
        ) {
            when {
                id == null && !blank -> ops += RowOp.Insert(kind, after)
                id != null && blank -> ops += RowOp.Delete(kind, id)
                id != null && before != after -> ops += RowOp.Update(kind, id, after)
            }
        }

        fun nameValues(f: ContactForm) =
            mapOf(
                DATA2 to f.givenName.trim(),
                DATA3 to f.familyName.trim(),
                DATA1 to listOf(f.givenName.trim(), f.familyName.trim()).filter { it.isNotEmpty() }.joinToString(" "),
            )
        single(
            RowKind.Name,
            original.nameId,
            nameValues(original),
            nameValues(edited),
            edited.givenName.isBlank() && edited.familyName.isBlank(),
        )
        single(
            RowKind.Organization,
            original.companyId,
            mapOf(DATA1 to original.company.trim()),
            mapOf(DATA1 to edited.company.trim()),
            edited.company.isBlank(),
        )
        single(
            RowKind.Birthday,
            original.birthdayId,
            mapOf(DATA1 to original.birthday, DATA2 to EVENT_TYPE_BIRTHDAY),
            mapOf(DATA1 to edited.birthday, DATA2 to EVENT_TYPE_BIRTHDAY),
            edited.birthday.isBlank(),
        )
        single(RowKind.Note, original.noteId, mapOf(DATA1 to original.note), mapOf(DATA1 to edited.note), edited.note.isBlank())

        fun labeledValues(l: Labeled) = mapOf(DATA1 to l.value.trim(), DATA2 to l.type, DATA3 to l.label)

        fun list(
            kind: RowKind,
            before: List<Labeled>,
            after: List<Labeled>,
        ) {
            val beforeById = before.filter { it.id != null }.associateBy { it.id!! }
            val keptIds = mutableSetOf<Long>()
            for (item in after) {
                val id = item.id
                val old = id?.let { beforeById[it] }
                when {
                    old == null && item.value.isNotBlank() -> {
                        ops += RowOp.Insert(kind, labeledValues(item))
                    }

                    // A cleared value is left out of keptIds, so it is deleted below.
                    old != null && item.value.isNotBlank() -> {
                        keptIds += id
                        if (labeledValues(old) != labeledValues(item)) ops += RowOp.Update(kind, id, labeledValues(item))
                    }
                }
            }
            beforeById.keys.filter { it !in keptIds }.forEach { ops += RowOp.Delete(kind, it) }
        }
        list(RowKind.Phone, original.phones, edited.phones)
        list(RowKind.Email, original.emails, edited.emails)
        list(RowKind.Address, listOfNotNull(original.address), listOfNotNull(edited.address))
        return ops
    }
}
