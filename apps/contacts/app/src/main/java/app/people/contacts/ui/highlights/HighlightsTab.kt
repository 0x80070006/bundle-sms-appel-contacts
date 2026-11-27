package app.people.contacts.ui.highlights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.EmptyState
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.segmentShape

/** The Highlights tab: favorites, upcoming birthdays ("For you"), recently added, contacts with no number, and Recents. */
@Composable
fun HighlightsTab(
    viewModel: ContactsViewModel,
    contentPadding: PaddingValues,
    onOpenContact: (Long) -> Unit,
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val birthdays by viewModel.birthdays.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val favorites = contacts?.filter { it.starred }.orEmpty()
    // Bundle: newest contacts (ids grow as contacts are added) and contacts with no number.
    val recentlyAdded = contacts.orEmpty().sortedByDescending { it.id }.take(10)
    val noNumber = contacts.orEmpty().filter { it.numbers.isEmpty() }.take(10)
    // Bundle: emergency contacts (ICE) first, when there are any.
    val ice by app.people.contacts.data.Emergency.info(androidx.compose.ui.platform.LocalContext.current).collectAsStateWithLifecycle()
    val emergency = contacts.orEmpty().filter { it.id in ice?.contactIds.orEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        if (contacts == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            return@LazyColumn
        }
        if (favorites.isEmpty() && birthdays.isEmpty() && recents.isEmpty() && recentlyAdded.size < 3 && noNumber.isEmpty() && emergency.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    Icons.Rounded.AutoAwesome,
                    stringResource(R.string.highlights_empty_title),
                    stringResource(R.string.highlights_empty_body),
                )
            }
            return@LazyColumn
        }
        if (emergency.isNotEmpty()) {
            item(key = "ice-header") { SectionHeader(stringResource(R.string.ice_section)) }
            item(key = "ice") { FavoritesRow(emergency, onOpenContact) }
        }
        if (favorites.isNotEmpty()) {
            item(key = "favorites-header") { SectionHeader(stringResource(R.string.favorites)) }
            item(key = "favorites") { FavoritesRow(favorites, onOpenContact) }
        }
        if (birthdays.isNotEmpty()) {
            item(key = "for-you-header") { SectionHeader(stringResource(R.string.for_you)) }
            items(birthdays, key = { "birthday-${it.contact.id}" }) { birthday ->
                BirthdayCard(birthday, onOpen = { onOpenContact(birthday.contact.id) }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        if (recentlyAdded.size >= 3) {
            item(key = "added-header") { SectionHeader(stringResource(R.string.recently_added)) }
            item(key = "added") { FavoritesRow(recentlyAdded, onOpenContact) }
        }
        if (noNumber.isNotEmpty()) {
            item(key = "nonumber-header") { SectionHeader(stringResource(R.string.to_complete)) }
            item(key = "nonumber") { FavoritesRow(noNumber, onOpenContact) }
        }
        if (recents.isNotEmpty()) {
            item(key = "recents-header") { SectionHeader(stringResource(R.string.recents)) }
            itemsIndexed(recents, key = { _, r -> "recent-${r.contact.id}" }) { index, entry ->
                RecentRow(
                    entry,
                    segmentShape(index, recents.size),
                    onOpenContact = { onOpenContact(entry.contact.id) },
                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                )
            }
        }
    }
}
