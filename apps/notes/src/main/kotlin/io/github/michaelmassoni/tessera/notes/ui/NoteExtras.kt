package io.github.michaelmassoni.tessera.notes.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.notes.R
import io.github.michaelmassoni.tessera.notes.engine.Links
import io.github.michaelmassoni.tessera.notes.engine.NoteColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/*
 * Note colours (docs/notes.md, Colours; docs/decisions.md): a fixed hue per colour, blended into
 * the theme's surface, so a coloured note follows light, dark and dynamic colour and keeps the
 * theme's text colours (and their contrast).
 */
private val hues =
    mapOf(
        NoteColor.Red to Color(0xFFE5534B),
        NoteColor.Orange to Color(0xFFF08C2E),
        NoteColor.Yellow to Color(0xFFE8C52A),
        NoteColor.Green to Color(0xFF5BAF5E),
        NoteColor.Teal to Color(0xFF2FA89B),
        NoteColor.Blue to Color(0xFF4A8BE0),
        NoteColor.Purple to Color(0xFF9A6BDB),
        NoteColor.Pink to Color(0xFFE0669F),
        NoteColor.Brown to Color(0xFFA0785A),
        NoteColor.Grey to Color(0xFF8D8F94),
    )

/** The note's background: the theme's surface, tinted with its colour. */
@Composable
fun noteBackground(color: NoteColor?): Color {
    val surface = MaterialTheme.colorScheme.surface
    val hue = color?.let(hues::get) ?: return surface
    val dark = surface.luminance() < 0.5f
    return lerp(surface, hue, if (dark) 0.30f else 0.22f)
}

private val colorNames =
    mapOf(
        NoteColor.Red to R.string.color_red,
        NoteColor.Orange to R.string.color_orange,
        NoteColor.Yellow to R.string.color_yellow,
        NoteColor.Green to R.string.color_green,
        NoteColor.Teal to R.string.color_teal,
        NoteColor.Blue to R.string.color_blue,
        NoteColor.Purple to R.string.color_purple,
        NoteColor.Pink to R.string.color_pink,
        NoteColor.Brown to R.string.color_brown,
        NoteColor.Grey to R.string.color_grey,
    )

/** Choosing a note's colour: Default, then a swatch per colour, each named for TalkBack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorDialog(
    current: NoteColor?,
    onPick: (NoteColor?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_color)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<NoteColor?>(null) + NoteColor.entries).forEach { c ->
                    val name = stringResource(c?.let { colorNames.getValue(it) } ?: R.string.color_default)
                    val selected = c == current
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(noteBackground(c))
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ).selectable(selected = selected, role = Role.RadioButton) { onPick(c) }
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                painterResource(DesignR.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

/** The note's reminder under its text: tap to change it, × to remove it. Past ones are struck through. */
@Composable
fun ReminderChip(
    at: Long,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val text =
        DateUtils.formatDateTime(
            context,
            at,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
        )
    val past = at < System.currentTimeMillis()
    InputChip(
        selected = false,
        onClick = onEdit,
        label = {
            Text(
                text,
                textDecoration = if (past) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(painterResource(DesignR.drawable.ic_alarm), contentDescription = null) },
        trailingIcon = {
            Icon(
                painterResource(DesignR.drawable.ic_close),
                contentDescription = stringResource(R.string.remove_reminder),
                modifier = Modifier.clip(CircleShape).selectable(selected = false, role = Role.Button, onClick = onClear),
            )
        },
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

/**
 * The link the cursor is in, offered as a chip under the text (docs/notes.md, Links): Open for a
 * web address, Email, Call.
 */
@Composable
fun LinkChip(
    link: Links.Link,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val (label, icon) =
        when (link.kind) {
            Links.Kind.Web -> R.string.link_open to DesignR.drawable.ic_open_in_new
            Links.Kind.Email -> R.string.link_email to DesignR.drawable.ic_mail
            Links.Kind.Phone -> R.string.link_call to DesignR.drawable.ic_call
        }
    AssistChip(
        onClick = {
            val action = if (link.kind == Links.Kind.Phone) Intent.ACTION_DIAL else Intent.ACTION_VIEW
            try {
                context.startActivity(Intent(action, link.target.toUri()))
            } catch (_: ActivityNotFoundException) {
                // Nothing on the phone opens it.
            }
        },
        label = { Text(stringResource(label, link.text), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

/**
 * Setting a reminder: a date, then a time (docs/notes.md, Reminders). Asks for notifications the
 * first time, and says when reminders may be late because exact alarms aren't allowed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPicker(
    current: Long?,
    onSet: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    // Default: the current reminder, or the next whole hour.
    val start =
        current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?: LocalDateTime.now(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
    var day by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        // Android 13 and later ask; before that notifications are on by default.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val picked = day
    if (picked == null) {
        val dateState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    start
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            modifier = modifier,
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(
                dateState,
                title = { Text(stringResource(R.string.reminder_pick_date), Modifier.padding(start = 24.dp, top = 16.dp)) },
            )
        }
    } else {
        val timeState = rememberTimePickerState(start.hour, start.minute, DateFormat.is24HourFormat(context))
        val exact = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = modifier,
            title = { Text(stringResource(R.string.reminder_pick_time)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(timeState)
                    if (!exact) {
                        Text(stringResource(R.string.reminder_inexact), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                                )
                            } catch (_: ActivityNotFoundException) {
                                // No such settings page on this phone.
                            }
                        }) { Text(stringResource(R.string.reminder_allow)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSet(
                        picked
                            .atTime(LocalTime.of(timeState.hour, timeState.minute))
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli(),
                    )
                    onDismiss()
                }) { Text(stringResource(R.string.reminder_set)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
