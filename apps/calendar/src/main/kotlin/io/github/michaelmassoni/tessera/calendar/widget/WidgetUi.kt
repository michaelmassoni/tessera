package io.github.michaelmassoni.tessera.calendar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import io.github.michaelmassoni.tessera.calendar.engine.WidgetPlan
import io.github.michaelmassoni.tessera.calendar.ui.firstDayOfWeek
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
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
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

/**
 * The next 30 days, like Calendula's agenda: today's date with a + for a new event, then each
 * day's events as tonal cards with the calendar's colour bar. Tapping an event opens it.
 */
class AgendaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

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
 * This month as a grid, today highlighted. Tall cells list up to two events with a colour dot;
 * short ones show dots only. Tapping a day opens it in the app.
 */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val month = YearMonth.now()
        val weeks = MonthGrid.weeks(month, firstDayOfWeek(Locale.getDefault()))
        val data = CalendarWidgets.load(context, weeks.first().first(), weeks.last().last())
        val byDay = DayLayout.byDay(data.instances, data.zone)
        provideContent { GlanceTheme { Content(context, month, weeks, data, byDay) } }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val data = WidgetPreview.data(context)
        val month = YearMonth.from(data.today)
        val weeks = MonthGrid.weeks(month, firstDayOfWeek(Locale.getDefault()))
        provideContent { GlanceTheme { Content(context, month, weeks, data, DayLayout.byDay(data.instances, data.zone)) } }
    }

    @Composable
    private fun Content(
        context: Context,
        month: YearMonth,
        weeks: List<List<LocalDate>>,
        data: WidgetData,
        byDay: Map<LocalDate, List<Instance>>,
    ) {
        val size = LocalSize.current
        val cellHeight = (size.height - 92.dp) / weeks.size
        val roomy = cellHeight >= 52.dp && size.width >= 260.dp
        WidgetSurface(CalendarWidgets.openApp(context), padding = 10.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth().padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        CalendarWidgets.pattern("MMMMy").format(month),
                        style = style(GlanceTheme.colors.onSurface, 18.sp, FontWeight.Medium),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    AddButton(context, 32.dp)
                }
                Row(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
                    weeks.first().forEach { day ->
                        Text(
                            day.dayOfWeek.getDisplayName(DayStyle.NARROW, data.locale),
                            style = style(GlanceTheme.colors.onSurfaceVariant, 11.sp, FontWeight.Medium, TextAlign.Center),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
                if (!data.allowed) {
                    Message(context.getString(R.string.widget_needs_access))
                } else {
                    weeks.forEach { week ->
                        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            week.forEach { day -> DayCell(context, day, month, data.today, byDay[day].orEmpty(), roomy) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.DayCell(
        context: Context,
        day: LocalDate,
        month: YearMonth,
        today: LocalDate,
        events: List<Instance>,
        roomy: Boolean,
    ) {
        val isToday = day == today
        val inMonth = YearMonth.from(day) == month
        Column(
            GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(1.dp)
                .clickable(actionStartActivity(CalendarWidgets.openDay(context, day))),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                GlanceModifier
                    .size(22.dp)
                    .cornerRadius(11.dp)
                    .background(if (isToday) GlanceTheme.colors.primary else ColorProvider(Color.Transparent)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    style =
                        style(
                            when {
                                isToday -> GlanceTheme.colors.onPrimary
                                inMonth -> GlanceTheme.colors.onSurface
                                else -> GlanceTheme.colors.outline
                            },
                            12.sp,
                            if (isToday) FontWeight.Bold else FontWeight.Normal,
                            TextAlign.Center,
                        ),
                )
            }
            if (roomy) {
                events.take(2).forEach { event ->
                    Row(GlanceModifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.size(5.dp).cornerRadius(3.dp).background(eventColor(event))) {}
                        Spacer(GlanceModifier.width(2.dp))
                        Text(event.title, style = style(GlanceTheme.colors.onSurface, 9.sp), maxLines = 1)
                    }
                }
                if (events.size > 2) Text("+${events.size - 2}", style = style(GlanceTheme.colors.onSurfaceVariant, 9.sp))
            } else if (events.isNotEmpty()) {
                Row(GlanceModifier.padding(top = 2.dp)) {
                    events.take(3).forEach { event ->
                        Box(GlanceModifier.size(5.dp).cornerRadius(3.dp).background(eventColor(event))) {}
                        Spacer(GlanceModifier.width(2.dp))
                    }
                }
            }
        }
    }
}

/** Today's weekday and date; with room, the next event under it. */
class DateWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

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
