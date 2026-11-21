package io.github.michaelmassoni.tessera.calendar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.calendar.data.CalendarRepository
import io.github.michaelmassoni.tessera.calendar.data.ReminderReceiver
import io.github.michaelmassoni.tessera.calendar.engine.CalendarInfo
import io.github.michaelmassoni.tessera.calendar.engine.EventDraft
import io.github.michaelmassoni.tessera.calendar.engine.Ics
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.calendar.ui.CalendarActions
import io.github.michaelmassoni.tessera.calendar.ui.CalendarApp
import io.github.michaelmassoni.tessera.calendar.ui.PermissionScreen
import io.github.michaelmassoni.tessera.calendar.ui.firstDayOfWeek
import io.github.michaelmassoni.tessera.calendar.widget.CalendarWidgets
import io.github.michaelmassoni.tessera.calendar.widget.WidgetPreview
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels {
        viewModelFactory {
            initializer {
                CalendarViewModel(
                    repository = CalendarRepository(contentResolver),
                    io = Dispatchers.IO,
                    firstDayOfWeek = { firstDayOfWeek(resources.configuration.locales[0]) },
                    localCalendarName = getString(R.string.device_calendar),
                    localCalendarColor = getColor(R.color.ic_launcher_background),
                )
            }
        }
    }
    private var granted by mutableStateOf(false)
    private var denied by mutableStateOf(false)
    private var pendingIntent: Intent? = null
    private var observing = false

    // onCreate has just loaded: the first onResume needn't load again.
    private var loadedOnCreate = false

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = viewModel.refresh(after = CHANGE_DELAY_MS)
        }

    private val request =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            granted = hasCalendar()
            denied = !granted && result.isNotEmpty()
            if (granted) {
                onGranted()
                loadedOnCreate = true
            }
        }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importFrom(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        granted = hasCalendar()
        if (savedInstanceState == null) pendingIntent = intent
        ReminderReceiver.ensureChannel(this)
        lifecycleScope.launch(Dispatchers.Default) { WidgetPreview.publish(applicationContext) }
        val actions = Actions()
        setTesseraContent {
            if (!granted) {
                PermissionScreen(
                    denied = denied,
                    onGrant = ::askForAccess,
                    onSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) },
                )
            } else {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val detail by viewModel.detail.collectAsStateWithLifecycle()
                val editor by viewModel.editor.collectAsStateWithLifecycle()
                CalendarApp(state, detail, editor, viewModel.events, actions)
            }
        }
        if (granted) {
            onGranted()
            loadedOnCreate = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (!granted && hasCalendar()) {
            granted = true
            onGranted()
        } else if (granted) {
            if (loadedOnCreate) loadedOnCreate = false else viewModel.refresh()
            // Widgets pick up a new permission, and keep watching for calendar changes.
            CalendarWidgets.refreshSoon(this)
        }
    }

    override fun onStart() {
        super.onStart()
        if (granted) observe()
    }

    // Changes while in the background are picked up by onResume's refresh; watching meanwhile
    // would reload for every sync.
    override fun onStop() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        observing = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (granted) handleIntent(intent) else pendingIntent = intent
    }

    private fun hasCalendar() =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun askForAccess() {
        val calendar = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val notifications =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
        request.launch((calendar + notifications).toTypedArray())
    }

    private fun observe() {
        if (!observing) {
            contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        }
    }

    private fun onGranted() {
        observe()
        viewModel.start()
        pendingIntent?.let(::handleIntent)
        pendingIntent = null
    }

    private fun handleIntent(intent: Intent) {
        // From a widget: a day tapped in the month or date widget, or + (a new event on today).
        val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE)
        if (day != Long.MIN_VALUE) {
            val date = java.time.LocalDate.ofEpochDay(day)
            if (intent.action != Intent.ACTION_INSERT) {
                viewModel.openDay(date)
                return
            }
            viewModel.select(date)
        }
        val data = intent.data
        when (intent.action) {
            Intent.ACTION_INSERT, Intent.ACTION_EDIT -> {
                if (data != null && intent.action == Intent.ACTION_EDIT) {
                    runCatching { ContentUris.parseId(data) }.getOrNull()?.let { viewModel.openEvent(it, null) }
                    return
                }
                viewModel.create(prefill(intent))
            }

            Intent.ACTION_VIEW -> {
                if (data == null) return
                val type = intent.type ?: contentResolver.getType(data)
                if (type == "text/calendar" || data.lastPathSegment?.endsWith(".ics") == true) {
                    importFrom(data)
                    return
                }
                val id = runCatching { ContentUris.parseId(data) }.getOrNull() ?: return
                val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1).takeIf { it > 0 }
                viewModel.openEvent(id, begin)
            }
        }
    }

    /** Fields passed with CalendarContract insert extras; null to use the defaults. */
    private fun prefill(intent: Intent): EventDraft? {
        if (intent.extras == null) return null
        val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1)
        val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1)
        val allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        val title = intent.getStringExtra(CalendarContract.Events.TITLE).orEmpty()
        if (begin <= 0 && title.isEmpty()) return null
        val start = if (begin > 0) begin else System.currentTimeMillis()
        return EventDraft(
            title = title,
            allDay = allDay,
            start = start,
            end = if (end > start) end else start + if (allDay) 86_400_000L else 3_600_000L,
            timeZone = ZoneId.systemDefault().id,
            location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION).orEmpty(),
            description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty(),
            rrule = intent.getStringExtra(CalendarContract.Events.RRULE),
            reminders = listOf(10),
        )
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            val events =
                withContext(Dispatchers.IO) {
                    try {
                        contentResolver
                            .openInputStream(
                                uri,
                            )?.use { Ics.parse(it.readBytes().decodeToString(), ZoneId.systemDefault()) }
                            .orEmpty()
                    } catch (_: IOException) {
                        emptyList()
                    } catch (_: SecurityException) {
                        emptyList()
                    }
                }
            viewModel.import(events)
        }
    }

    private inner class Actions : CalendarActions {
        override fun setMode(mode: ViewMode) = viewModel.setMode(mode)

        override fun showMonth(month: YearMonth) = viewModel.showMonth(month)

        override fun select(day: LocalDate) = viewModel.select(day)

        override fun openDay(day: LocalDate) = viewModel.openDay(day)

        override fun closeDay() = viewModel.closeDay()

        override fun today() = viewModel.goToToday()

        override fun open(instance: Instance) = viewModel.open(instance)

        override fun closeDetail() = viewModel.closeDetail()

        override fun create() = viewModel.create()

        override fun edit() = viewModel.edit()

        override fun delete() = viewModel.delete()

        override fun map(location: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(location)}".toUri()))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, R.string.no_app, Toast.LENGTH_SHORT).show()
            }
        }

        override fun update(draft: EventDraft) = viewModel.update(draft)

        override fun save() = viewModel.save()

        override fun cancelEdit() = viewModel.cancelEdit()

        override fun loadMore() = viewModel.loadMoreSchedule()

        override fun setVisible(
            calendar: CalendarInfo,
            visible: Boolean,
        ) = viewModel.setVisible(calendar, visible)

        override fun import() = importFile.launch(arrayOf("text/calendar", "application/octet-stream"))

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
    }

    companion object {
        /** Opens the app on this day (`LocalDate.toEpochDay`), from a widget. */
        const val EXTRA_DAY = "io.github.michaelmassoni.tessera.calendar.DAY"

        // A sync writes events one at a time; wait for it to settle before reloading.
        private const val CHANGE_DELAY_MS = 500L
    }
}
