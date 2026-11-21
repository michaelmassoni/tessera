package io.github.michaelmassoni.tessera.contacts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.contacts.R
import io.github.michaelmassoni.tessera.contacts.engine.ContactSummary
import io.github.michaelmassoni.tessera.contacts.engine.Section
import io.github.michaelmassoni.tessera.core.design.Avatar

/** Sections with sticky headers, or flat search results. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactList(
    sections: List<Pair<Section, List<ContactSummary>>>,
    results: List<ContactSummary>?,
    selectedId: Long?,
    onOpen: (ContactSummary) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        if (results != null) {
            items(results, key = { "r${it.id}" }) { ContactRow(it, it.id == selectedId, onOpen) }
        } else {
            sections.forEach { (section, contacts) -> section(section, contacts, selectedId, onOpen) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.section(
    section: Section,
    contacts: List<ContactSummary>,
    selectedId: Long?,
    onOpen: (ContactSummary) -> Unit,
) {
    val key = if (section is Section.Letter) "s${section.letter}" else "fav"
    stickyHeader(key = key, contentType = "header") {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Text(
                if (section is Section.Letter) section.letter else stringResource(R.string.favourites),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
            )
        }
    }
    items(contacts, key = { "$key-${it.id}" }, contentType = { "contact" }) { ContactRow(it, it.id == selectedId, onOpen) }
}

@Composable
private fun ContactRow(
    contact: ContactSummary,
    selected: Boolean,
    onOpen: (ContactSummary) -> Unit,
) {
    val name = contact.name.ifBlank { stringResource(R.string.unnamed) }
    ListItem(
        selected = selected,
        onClick = { onOpen(contact) },
        leadingContent = { Avatar(name, contact.lookupKey, contact.photoUri) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Row { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
