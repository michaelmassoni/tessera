package io.github.michaelmassoni.tessera.notes.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.michaelmassoni.tessera.notes.MainActivity
import io.github.michaelmassoni.tessera.notes.R
import io.github.michaelmassoni.tessera.notes.engine.Note
import io.github.michaelmassoni.tessera.notes.engine.NoteQuery
import io.github.michaelmassoni.tessera.notes.engine.NoteView
import io.github.michaelmassoni.tessera.notes.notes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Pinned notes first, then the most recently edited, with buttons for a new note or checklist. */
class NotesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val repository = context.notes
        withContext(Dispatchers.IO) { repository.load() }
        val notes = NoteQuery.select(repository.notes.value.values, NoteView.Notes).take(MAX_NOTES)
        provideContent { GlanceTheme { Content(context, notes) } }
    }

    @Composable
    private fun Content(
        context: Context,
        notes: List<Note>,
    ) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.app_name),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(open(context))),
                )
                Button(context, DesignR.drawable.ic_check_box, R.string.new_checklist, MainActivity.ACTION_NEW_CHECKLIST)
                Button(context, DesignR.drawable.ic_add, R.string.new_note, MainActivity.ACTION_NEW_NOTE)
            }
            if (notes.isEmpty()) {
                Box(GlanceModifier.fillMaxSize().padding(end = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        context.getString(R.string.widget_empty),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    )
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(notes, itemId = { it.id.hashCode().toLong() }) { note -> NoteCard(context, note) }
                }
            }
        }
    }

    @Composable
    private fun NoteCard(
        context: Context,
        note: Note,
    ) {
        val (title, body) = NoteQuery.preview(note)
        Column(GlanceModifier.fillMaxWidth().padding(end = 8.dp, bottom = 6.dp)) {
            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(16.dp)
                    .background(GlanceTheme.colors.surface)
                    .padding(12.dp)
                    .clickable(
                        actionStartActivity(
                            open(context).setAction(MainActivity.ACTION_OPEN_NOTE).putExtra(MainActivity.EXTRA_NOTE, note.id),
                        ),
                    ),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        title,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    )
                }
                if (body.isNotBlank()) {
                    if (title.isNotBlank()) Spacer(GlanceModifier.height(2.dp))
                    Text(body, maxLines = 3, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                }
            }
        }
    }

    @Composable
    private fun Button(
        context: Context,
        icon: Int,
        label: Int,
        action: String,
    ) {
        Box(
            GlanceModifier
                .size(48.dp)
                .cornerRadius(24.dp)
                .clickable(actionStartActivity(open(context).setAction(action)))
                .semantics { contentDescription = context.getString(label) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }

    companion object {
        private const val MAX_NOTES = 20

        private fun open(context: Context) =
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** Redraws placed widgets after notes change; does nothing when there are none. */
        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(NotesWidget::class.java).isNotEmpty()) NotesWidget().updateAll(context)
        }
    }
}

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}
