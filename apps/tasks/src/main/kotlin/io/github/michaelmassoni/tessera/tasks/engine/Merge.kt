package io.github.michaelmassoni.tessera.tasks.engine

/**
 * A task changed both here and on the server since the last sync: each field takes this phone's
 * value if it was changed here, else the server's. [base] is the task as last synced. Pure, so
 * it's unit tested.
 */
object Merge {
    fun merge(
        base: Task?,
        local: Task,
        remote: Task,
    ): Task {
        fun <T> pick(field: (Task) -> T): T = if (base != null && field(local) == field(base)) field(remote) else field(local)
        return remote.copy(
            title = pick { it.title },
            notes = pick { it.notes },
            due = pick { it.due },
            repeat = pick { it.repeat },
            starred = pick { it.starred },
            parentId = pick { it.parentId },
            completedAt = pick { it.completedAt },
            order = pick { it.order },
            listId = local.listId,
            snoozedUntil = local.snoozedUntil,
            // Something of this phone's won: it still needs uploading.
            dirty =
                base == null ||
                    listOf<
                        (
                            Task,
                        ) -> Any?,
                    >(
                        { it.title },
                        { it.notes },
                        { it.due },
                        { it.repeat },
                        { it.starred },
                        { it.parentId },
                        { it.completedAt },
                        { it.order },
                    ).any { f -> f(local) != f(base) && f(local) != f(remote) },
        )
    }
}
