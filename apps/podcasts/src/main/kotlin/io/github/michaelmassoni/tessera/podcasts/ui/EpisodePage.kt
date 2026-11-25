package io.github.michaelmassoni.tessera.podcasts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.podcasts.engine.Episodes

/** An episode: cover, show, title, date and length, play and download, and the show notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(
    episodeId: String,
    state: PodcastsState,
    actions: PodcastsActions,
    onBack: () -> Unit,
    onOpenShow: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialNotes: String? = null,
) {
    val episode = state.episode(episodeId)
    var notes by remember(episodeId) { mutableStateOf(initialNotes) }
    LaunchedEffect(episodeId) { if (notes == null) notes = actions.notes(episodeId) }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = {}, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        if (episode == null) return@Scaffold
        val show = state.show(episode.showId)
        val es = state.state(episodeId)
        val player = state.player
        val current = player.episodeId == episodeId
        val linkColor = MaterialTheme.colorScheme.primary
        val html = notes
        val text =
            remember(html, linkColor) {
                html?.let {
                    AnnotatedString.fromHtml(
                        cleanHtml(it),
                        linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    )
                }
            }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(episode.artworkUrl ?: show?.artworkUrl, 96.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    show?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).clickable { onOpenShow(episode.showId) },
                )
            }
            Text(
                listOf(dateText(episode.publishedAt, state.now), durationText(episode.durationMs))
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                PlayButton(
                    playing = current && player.isPlaying,
                    progress = if (es.played) 0f else Episodes.progress(episode, es),
                    buffering = current && player.buffering,
                    onClick = { if (current) actions.togglePlay() else actions.play(episode) },
                )
                if (es.positionMs > 0 && !es.played) {
                    Text(
                        durationText(Episodes.remaining(episode, es), left = true),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                DownloadButton(
                    downloaded = es.download != null,
                    percent = state.downloading[episodeId],
                    onDownload = { actions.download(episodeId) },
                    onCancel = { actions.cancelDownload(episodeId) },
                )
                Spacer(Modifier.weight(1f))
                EpisodeMenu(episode, es, episodeId in player.queue, current, actions, onGoToShow = { onOpenShow(episode.showId) })
            }
            if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
        }
    }
}

/** Show notes without images (we don't load them) and with plain line breaks for plain-text notes. */
internal fun cleanHtml(html: String): String {
    val noImages = html.replace(Regex("(?is)<img[^>]*>"), "").replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
    val hasTags = Regex("<[a-zA-Z][^>]*>").containsMatchIn(noImages)
    return if (hasTags) noImages else noImages.replace("\n", "<br>")
}
