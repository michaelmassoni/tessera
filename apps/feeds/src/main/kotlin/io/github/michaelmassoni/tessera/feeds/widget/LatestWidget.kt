package io.github.michaelmassoni.tessera.feeds.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.michaelmassoni.tessera.feeds.MainActivity
import io.github.michaelmassoni.tessera.feeds.R
import io.github.michaelmassoni.tessera.feeds.app
import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.FeedData
import io.github.michaelmassoni.tessera.feeds.engine.Library
import io.github.michaelmassoni.tessera.feeds.engine.Selection
import io.github.michaelmassoni.tessera.feeds.sync.SyncWorker
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** The newest unread articles on the home screen: tap one to read it, refresh to fetch. */
class LatestWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val store = context.app.store
        provideContent {
            val data by store.data.collectAsState()
            // Formatted here, not in a composable (lint: NonObservableLocale).
            val now = System.currentTimeMillis()
            val rows = Library.articles(data, Selection.Unread).take(MAX_ROWS)
            val ages = rows.associate { it.id to age(it, now) }
            GlanceTheme { Content(context, data, rows, ages) }
        }
    }

    private fun age(
        a: Article,
        now: Long,
    ): String {
        val t = a.date.takeIf { it > 0 } ?: a.added
        return DateUtils.getRelativeTimeSpanString(t, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }

    @Composable
    private fun Content(
        context: Context,
        data: FeedData,
        rows: List<Article>,
        ages: Map<String, String>,
    ) {
        val openApp =
            actionStartActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        val unread = data.articles.count { it.unread }
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(openApp)
                .padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (unread > 0) context.getString(R.string.widget_title_count, unread) else context.getString(R.string.unread),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight().padding(4.dp),
                )
                Image(
                    ImageProvider(DesignR.drawable.ic_sync),
                    contentDescription = context.getString(R.string.refresh),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier =
                        GlanceModifier
                            .size(40.dp)
                            .padding(8.dp)
                            .clickable(actionRunCallback<RefreshFeeds>()),
                )
            }
            if (rows.isEmpty()) {
                Text(
                    context.getString(R.string.all_caught_up),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.fillMaxSize().padding(8.dp).clickable(openApp),
                )
            } else {
                LazyColumn(GlanceModifier.fillMaxWidth().wrapContentHeight()) {
                    items(rows, itemId = { it.id.hashCode().toLong() }) { a ->
                        Column(
                            GlanceModifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .clickable(
                                    actionStartActivity(
                                        Intent(context, MainActivity::class.java)
                                            .putExtra(MainActivity.EXTRA_ARTICLE, a.id)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                    ),
                                ),
                        ) {
                            Text(
                                a.title,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
                                maxLines = 2,
                            )
                            Text(
                                listOfNotNull(data.feed(a.feedId)?.title, ages[a.id]).joinToString(" · "),
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(GlanceModifier.size(0.dp))
        }
    }

    companion object {
        // Glance shows at most 500 views; a row is three.
        private const val MAX_ROWS = 40

        suspend fun refresh(context: Context) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LatestWidgetReceiver::class.java))
            if (ids.isEmpty()) return
            val manager = GlanceAppWidgetManager(context)
            ids.forEach { LatestWidget().update(context, manager.getGlanceIdBy(it)) }
        }
    }
}

/** The widget's refresh: a sync now. */
class RefreshFeeds : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        SyncWorker.schedule.soon(context, delaySeconds = 0)
    }
}

class LatestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LatestWidget()
}
