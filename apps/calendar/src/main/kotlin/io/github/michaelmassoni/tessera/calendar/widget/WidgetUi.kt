package io.github.michaelmassoni.tessera.calendar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.engine.AgendaRow
import io.github.michaelmassoni.tessera.calendar.engine.DayLayout
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.calendar.engine.MonthGrid
import io.github.michaelmassoni.tessera.calendar.engine.WeekPlan
import io.github.michaelmassoni.tessera.calendar.engine.WidgetLanes
import io.github.michaelmassoni.tessera.calendar.engine.WidgetPlan
import io.github.michaelmassoni.tessera.calendar.ui.firstDayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import io.github.michaelmassoni.tessera.core.design.R as DesignR
import java.time.format.TextStyle as DayStyle

/** The shared look: the wallpaper-coloured widget surface with large rounded corners. */
@Composable
private fun WidgetSurface(
    open: Intent,
    modifier: GlanceModifier = GlanceModifier,
    padding: Dp = 12.dp,
    /** The month widget passes its own (its settings choose it); others use the launcher's. */
    background: ColorProvider? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(background ?: GlanceTheme.colors.widgetBackground)
            .padding(padding)
            .clickable(actionStartActivity(open)),
    ) { content() }
}

@Composable
private fun AddButton(
    context: Context,
    size: Dp = 40.dp,
) {
    // The circle can be smaller than 48dp; the touch target around it isn't.
    Box(
        GlanceModifier
            .size(48.dp)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity(CalendarWidgets.newEvent(context)))
            .semantics { contentDescription = context.getString(R.string.new_event) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            GlanceModifier
                .size(size)
                .cornerRadius(size / 2)
                .background(GlanceTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(DesignR.drawable.ic_add),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                modifier = GlanceModifier.size(size * 0.55f),
            )
        }
    }
}

@Composable
private fun Message(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center))
    }
}

private fun style(
    color: ColorProvider,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
) = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align)

/** An event's own calendar colour (data, not a theme colour); 0, as in the picker previews, is primary. */
@Composable
private fun eventColor(instance: Instance) = if (instance.color == 0) GlanceTheme.colors.primary else ColorProvider(Color(instance.color))

/** Black or white, whichever reads on the event's colour (onPrimary on the theme colour). */
@Composable
private fun pillTextColor(instance: Instance): ColorProvider =
    when {
        instance.color == 0 -> GlanceTheme.colors.onPrimary
        Color(instance.color).copy(alpha = 1f).luminance() > 0.4f -> ColorProvider(Color.Black)
        else -> ColorProvider(Color.White)
    }

/**
 * The next 30 days, like Calendula's agenda: today's date with a + for a new event, then each
 * day's events as tonal cards with the calendar's colour bar. Tapping an event opens it.
 */
class AgendaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(236.dp, 236.dp)))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val today = LocalDate.now()
        val data = CalendarWidgets.load(context, today, today.plusDays(WidgetPlan.AGENDA_DAYS - 1))
        val rows = if (data.allowed) WidgetPlan.agenda(data.instances, data.now, data.zone) else emptyList()
        provideContent { GlanceTheme { Content(context, data, rows) } }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val data = WidgetPreview.data(context)
        val rows = WidgetPlan.agenda(data.instances, data.now, data.zone)
        provideContent { GlanceTheme { Content(context, data, rows) } }
    }

    @Composable
    private fun Content(
        context: Context,
        data: WidgetData,
        rows: List<AgendaRow>,
    ) {
        val today = data.today
        WidgetSurface(CalendarWidgets.openApp(context)) {
            Column(GlanceModifier.fillMaxSize()) {
                Row(
                    GlanceModifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(
                            today.dayOfWeek.getDisplayName(DayStyle.FULL, data.locale),
                            style = style(GlanceTheme.colors.primary, 13.sp, FontWeight.Medium),
                        )
                        // A narrow widget has no room for "November 21" next to the + button.
                        val skeleton = if (LocalSize.current.width < 240.dp) "MMMd" else "MMMMd"
                        Text(
                            CalendarWidgets.pattern(skeleton).format(today),
                            style = style(GlanceTheme.colors.onSurface, 22.sp),
                            maxLines = 1,
                        )
                    }
                    AddButton(context)
                }
                when {
                    !data.allowed -> {
                        Message(context.getString(R.string.widget_needs_access))
                    }

                    rows.isEmpty() -> {
                        Message(context.getString(R.string.widget_nothing_coming_up))
                    }

                    else -> {
                        LazyColumn(GlanceModifier.fillMaxSize()) {
                            items(rows, itemId = { row ->
                                if (row is AgendaRow.Event) {
                                    row.instance.begin * 31 + row.instance.eventId
                                } else {
                                    -(row as AgendaRow.Day)
                                        .date
                                        .toEpochDay()
                                }
                            }) { row ->
                                when (row) {
                                    is AgendaRow.Day -> {
                                        Text(
                                            CalendarWidgets.dayLabel(context, row.date, today),
                                            style = style(GlanceTheme.colors.onSurfaceVariant, 12.sp, FontWeight.Medium),
                                            modifier = GlanceModifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
                                        )
                                    }

                                    is AgendaRow.Event -> {
                                        EventCard(context, row.instance)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EventCard(
        context: Context,
        event: Instance,
    ) {
        val details = listOf(CalendarWidgets.timeLabel(context, event), event.location).filter { it.isNotBlank() }.joinToString(" · ")
        Column(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Row(
                GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(16.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(CalendarWidgets.openEvent(context, event))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    GlanceModifier
                        .width(4.dp)
                        .height(32.dp)
                        .cornerRadius(2.dp)
                        .background(eventColor(event)),
                ) {}
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        event.title.ifBlank { context.getString(R.string.untitled) },
                        style = style(GlanceTheme.colors.onSecondaryContainer, 14.sp, FontWeight.Medium),
                        maxLines = 1,
                    )
                    Text(details, style = style(GlanceTheme.colors.onSecondaryContainer, 12.sp), maxLines = 1)
                }
            }
        }
    }
}

/**
 * A month as a grid, like the app's: rounded day cells and events as pills in their calendar's
 * colour. The header's arrows move between months and today's date comes back; each placed widget
 * has its own settings (background, opacity, theme, calendars; see [MonthWidgetSettingsActivity]).
 */
class MonthWidget(
    /** Settings to draw with instead of the widget's own: the settings screen's live preview. */
    private val preview: MonthWidgetSettings? = null,
) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    // The picker draws the preview at this size (the default gives no size, so no room for pills).
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(320.dp, 560.dp)))

    /** A month's events, and what they were loaded for (to know when to load again). */
    private data class Loaded(
        val key: Triple<Int, Long, Set<Long>>,
        val month: YearMonth,
        val weeks: List<List<LocalDate>>,
        val data: WidgetData,
        val byDay: Map<LocalDate, List<Instance>>,
    )

    private fun load(
        context: Context,
        settings: MonthWidgetSettings,
    ): Loaded {
        val month = YearMonth.now().plusMonths(settings.offset.toLong())
        val weeks = MonthGrid.weeks(month, firstDayOfWeek(Locale.getDefault()))
        val data = CalendarWidgets.load(context, weeks.first().first(), weeks.last().last(), settings.hidden)
        return Loaded(
            Triple(settings.offset, settings.stamp, settings.hidden),
            month,
            weeks,
            data,
            DayLayout.byDay(data.instances, data.zone),
        )
    }

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val first = load(context, preview ?: MonthWidgetSettings.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, id)))
        provideContent {
            // Arrow taps and refreshes change the state and recompose; the events reload when the
            // month, the calendars or the refresh stamp change.
            val settings = preview ?: MonthWidgetSettings.from(currentState<Preferences>())
            val loaded by produceState(first, settings.offset, settings.stamp, settings.hidden) {
                if (value.key != Triple(settings.offset, settings.stamp, settings.hidden)) {
                    value = withContext(Dispatchers.IO) { load(context, settings) }
                }
            }
            Themed(context, settings) { background -> Content(context, loaded, settings, interactive = true, background = background) }
        }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val data = WidgetPreview.data(context)
        val month = YearMonth.from(data.today)
        val weeks = MonthGrid.weeks(month, firstDayOfWeek(Locale.getDefault()))
        val loaded = Loaded(Triple(0, 0, emptySet()), month, weeks, data, DayLayout.byDay(data.instances, data.zone))
        provideContent {
            Themed(
                context,
                MonthWidgetSettings(),
            ) { background -> Content(context, loaded, MonthWidgetSettings(), interactive = false, background = background) }
        }
    }

    @Composable
    private fun Themed(
        context: Context,
        settings: MonthWidgetSettings,
        content: @Composable (ColorProvider) -> Unit,
    ) {
        val colors = settings.colors(context)
        GlanceTheme(colors = colors.scheme) { content(colors.background) }
    }

    @Composable
    private fun Content(
        context: Context,
        loaded: Loaded,
        settings: MonthWidgetSettings,
        interactive: Boolean,
        background: ColorProvider? = null,
    ) {
        val month = loaded.month
        val weeks = loaded.weeks
        val data = loaded.data
        val byDay = loaded.byDay
        val size = LocalSize.current
        val padding = 8.dp
        // Glance has no weighted spans, so bars get exact widths from the cell width.
        val cellWidth = (size.width - padding * 2) / 7
        val rowHeight = (size.height - padding * 2 - HeaderHeight - WeekdayHeight) / weeks.size
        val room = ((rowHeight - NumberHeight - 4.dp) / LaneHeight).toInt().coerceAtLeast(0)
        // As many lanes as there's room for, and as Glance's 500-view limit allows (a busy month
        // with many lanes goes over it). The rest of the widget takes about 40 views plus 16 per week.
        val (lanes, plans) = WidgetLanes.fit(weeks, byDay, room, budget = VIEW_LIMIT - 40 - weeks.size * 16)
        val today = data.today
        val title = CalendarWidgets.pattern(if (month.year == today.year) "MMMM" else "MMMMy").format(month)
        WidgetSurface(CalendarWidgets.openApp(context), padding = padding, background = background) {
            Column(GlanceModifier.fillMaxSize()) {
                Row(
                    GlanceModifier.fillMaxWidth().height(HeaderHeight).padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = style(GlanceTheme.colors.onSurface, 18.sp, FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (interactive) {
                        HeaderButton(context.getString(R.string.previous_month), -1) { Arrow(DesignR.drawable.ic_chevron_left) }
                        Spacer(GlanceModifier.width(4.dp))
                        HeaderButton(context.getString(R.string.today), 0) {
                            Text(
                                today.dayOfMonth.toString(),
                                style = style(GlanceTheme.colors.onSecondaryContainer, 14.sp, FontWeight.Medium, TextAlign.Center),
                            )
                        }
                        Spacer(GlanceModifier.width(4.dp))
                        HeaderButton(context.getString(R.string.next_month), 1) { Arrow(DesignR.drawable.ic_chevron_right) }
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    AddButton(context, 40.dp)
                }
                Row(GlanceModifier.fillMaxWidth().height(WeekdayHeight), verticalAlignment = Alignment.CenterVertically) {
                    weeks.first().forEach { day ->
                        Text(
                            day.dayOfWeek.getDisplayName(DayStyle.SHORT, data.locale),
                            style = style(GlanceTheme.colors.onSurfaceVariant, 11.sp, FontWeight.Medium, TextAlign.Center),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
                if (!data.allowed) {
                    Message(context.getString(R.string.widget_needs_access))
                } else {
                    weeks.forEachIndexed { i, week ->
                        WeekRow(context, week, month, today, plans[i], cellWidth, lanes, GlanceModifier.fillMaxWidth().defaultWeight())
                    }
                }
            }
        }
    }

    /** A 40 dp tonal button in the header that moves the month ([delta] 0 goes back to this month). */
    @Composable
    private fun HeaderButton(
        label: String,
        delta: Int,
        content: @Composable () -> Unit,
    ) {
        Box(
            GlanceModifier
                .size(40.dp)
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionRunCallback<MonthNavigation>(actionParametersOf(MonthNavigation.Delta to delta)))
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    @Composable
    private fun Arrow(icon: Int) {
        Image(
            ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(22.dp),
        )
    }

    /** Day cells underneath, and the week's events as pills on top (laid out as in the app). */
    @Composable
    private fun WeekRow(
        context: Context,
        week: List<LocalDate>,
        month: YearMonth,
        today: LocalDate,
        plan: WeekPlan,
        cellWidth: Dp,
        lanes: Int,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        Box(modifier) {
            Row(GlanceModifier.fillMaxSize()) {
                week.forEach { day -> DayCell(context, day, YearMonth.from(day) == month, day == today) }
            }
            // The pills don't take taps, so a tap reaches the day underneath. Every view counts
            // against Glance's limit (see WidgetLanes), so a pill is a single Text.
            if (lanes > 0) {
                Column(GlanceModifier.fillMaxWidth().padding(top = NumberHeight + 2.dp)) {
                    repeat(lanes) { lane ->
                        Row(GlanceModifier.fillMaxWidth().height(LaneHeight)) {
                            WidgetLanes.segments(plan, lane, last = lane == lanes - 1).forEach { segment ->
                                when (segment) {
                                    is WidgetLanes.Segment.Gap -> {
                                        Spacer(GlanceModifier.width(cellWidth * segment.cells))
                                    }

                                    is WidgetLanes.Segment.Pill -> {
                                        val event = segment.bar.instance
                                        // The box holds the margins, so neighbouring pills need no gap view.
                                        // Glance draws text at the top of its view, so the pill wraps its
                                        // text and the box centres it in the lane.
                                        Box(
                                            GlanceModifier
                                                .width(cellWidth * segment.bar.span)
                                                .fillMaxHeight()
                                                .padding(horizontal = PillMargin),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Text(
                                                event.title,
                                                style = style(pillTextColor(event), 11.sp),
                                                maxLines = 1,
                                                modifier =
                                                    GlanceModifier
                                                        .fillMaxWidth()
                                                        .cornerRadius(4.dp)
                                                        .background(eventColor(event))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }

                                    is WidgetLanes.Segment.More -> {
                                        Text(
                                            context.getString(R.string.more_events, segment.count),
                                            style = style(GlanceTheme.colors.onSurfaceVariant, 11.sp, align = TextAlign.Center),
                                            maxLines = 1,
                                            modifier = GlanceModifier.width(cellWidth),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Two views per day (a padded, clickable box and its number on the cell colour); today adds a circle. */
    @Composable
    private fun androidx.glance.layout.RowScope.DayCell(
        context: Context,
        day: LocalDate,
        inMonth: Boolean,
        isToday: Boolean,
    ) {
        Box(
            GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(1.5.dp)
                .clickable(actionStartActivity(CalendarWidgets.openDay(context, day))),
        ) {
            val number = day.dayOfMonth.toString()
            if (isToday) {
                Box(
                    GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(8.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .padding(top = 1.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        number,
                        style = style(GlanceTheme.colors.onPrimary, 11.sp, FontWeight.Bold, TextAlign.Center),
                        modifier =
                            GlanceModifier
                                .size(NumberHeight - 2.dp)
                                .cornerRadius((NumberHeight - 2.dp) / 2)
                                .background(GlanceTheme.colors.primary)
                                .padding(top = 1.dp),
                    )
                }
            } else {
                // Other months' days only dim their number, as in the app.
                Text(
                    number,
                    style =
                        style(
                            if (inMonth) GlanceTheme.colors.onSurface else GlanceTheme.colors.outline,
                            11.sp,
                            align = TextAlign.Center,
                        ),
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(8.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .padding(top = 3.dp),
                )
            }
        }
    }

    private companion object {
        val HeaderHeight = 48.dp
        val WeekdayHeight = 18.dp
        val NumberHeight = 20.dp
        val LaneHeight = 18.dp
        val PillMargin = 1.5.dp

        /** Glance's limit on views in one widget, with a little room for its own. */
        const val VIEW_LIMIT = 480
    }
}

/** Today's weekday and date; with room, the next event under it. */
class DateWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(160.dp, 120.dp)))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val today = LocalDate.now()
        val data = CalendarWidgets.load(context, today, today.plusDays(1))
        val next = WidgetPlan.next(data.instances, data.now)
        provideContent { GlanceTheme { Content(context, data, next) } }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val data = WidgetPreview.data(context)
        provideContent { GlanceTheme { Content(context, data, WidgetPlan.next(data.instances, data.now)) } }
    }

    @Composable
    private fun Content(
        context: Context,
        data: WidgetData,
        next: Instance?,
    ) {
        val size = LocalSize.current
        val today = data.today
        WidgetSurface(CalendarWidgets.openDay(context, today), padding = 8.dp) {
            Column(
                GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    today.dayOfWeek.getDisplayName(DayStyle.SHORT, data.locale),
                    style = style(GlanceTheme.colors.primary, 13.sp, FontWeight.Medium, TextAlign.Center),
                )
                Text(today.dayOfMonth.toString(), style = style(GlanceTheme.colors.onSurface, 40.sp, FontWeight.Normal, TextAlign.Center))
                if (next != null && size.width >= 140.dp) {
                    Text(next.title, style = style(GlanceTheme.colors.onSurface, 12.sp, FontWeight.Medium, TextAlign.Center), maxLines = 1)
                    Text(
                        CalendarWidgets.timeLabel(context, next),
                        style = style(GlanceTheme.colors.onSurfaceVariant, 11.sp, align = TextAlign.Center),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
