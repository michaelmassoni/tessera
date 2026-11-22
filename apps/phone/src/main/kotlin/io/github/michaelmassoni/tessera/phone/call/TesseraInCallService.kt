package io.github.michaelmassoni.tessera.phone.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import io.github.michaelmassoni.tessera.phone.R
import io.github.michaelmassoni.tessera.phone.data.PhoneRepository
import io.github.michaelmassoni.tessera.phone.data.SpamReports
import io.github.michaelmassoni.tessera.phone.engine.AudioInfo
import io.github.michaelmassoni.tessera.phone.engine.AudioRoute
import io.github.michaelmassoni.tessera.phone.engine.CallerLocation
import io.github.michaelmassoni.tessera.phone.engine.Route
import io.github.michaelmassoni.tessera.phone.engine.SpamNumbers
import kotlin.concurrent.thread
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Telecom binds this while Tessera is the default phone app. It feeds [CallManager], posts the
 * incoming/ongoing call notification (CallStyle, full-screen for incoming) and opens the
 * in-call screen.
 */
class TesseraInCallService :
    InCallService(),
    CallManager.AudioControl {
    private var endpoints: List<CallEndpoint> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val repo = PhoneRepository(contentResolver)
        CallManager.nameLookup = { number -> runCatching { repo.nameFor(number) }.getOrNull() }
        val home =
            getSystemService(android.telephony.TelephonyManager::class.java)?.networkCountryIso?.uppercase()?.ifBlank { null }
                ?: resources.configuration.locales[0].country
        val spam = SpamReports(this)
        val where = CallerLocation(home)
        CallManager.callerLookup = { number ->
            runCatching { where.describe(number) to SpamNumbers.isReported(number, spam.all(), home) }.getOrDefault(null to false)
        }
        // libphonenumber reads its data on first use: do that now, not when a call rings.
        thread(name = "callerid-warmup") { runCatching { where.describe("+61390000000") } }
        CallManager.audioControl = this
        ensureChannel(this)
    }

    override fun onDestroy() {
        if (CallManager.audioControl === this) CallManager.audioControl = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(call)
        notifyCalls()
        call.registerCallback(
            object : Call.Callback() {
                override fun onStateChanged(
                    call: Call,
                    state: Int,
                ) {
                    CallManager.refresh()
                    notifyCalls()
                }
            },
        )
        // Outgoing calls open the in-call screen straight away; incoming ones use the full-screen notification.
        if (call.details.callDirection != Call.Details.DIRECTION_INCOMING) {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
        notifyCalls()
    }

    // ---- Audio routes: CallEndpoint on Android 14+, CallAudioState before. ----

    /** Bluetooth devices by route id, for the pre-14 API. */
    private var bluetooth: Map<String, BluetoothDevice> = emptyMap()

    @Deprecated("Replaced by onCallEndpointChanged and onMuteStateChanged on API 34+")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val mask = audioState.supportedRouteMask

        @Suppress("DEPRECATION")
        val devices = audioState.supportedBluetoothDevices.associateBy { "bt:${it.address}" }
        bluetooth = devices
        val available =
            buildList {
                if (mask and CallAudioState.ROUTE_EARPIECE != 0) add(AudioRoute(EARPIECE, Route.Earpiece))
                if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioRoute(WIRED, Route.Wired))
                if (mask and CallAudioState.ROUTE_SPEAKER != 0) add(AudioRoute(SPEAKER, Route.Speaker))
                if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) {
                    if (devices.isEmpty()) add(AudioRoute(BLUETOOTH, Route.Bluetooth))
                    // Device names need BLUETOOTH_CONNECT, which Phone doesn't ask for; Android 14+ names
                    // them through CallEndpoint instead.
                    devices.keys.forEach { id -> add(AudioRoute(id, Route.Bluetooth)) }
                }
            }
        val current =
            when (audioState.route) {
                CallAudioState.ROUTE_SPEAKER -> Route.Speaker
                CallAudioState.ROUTE_BLUETOOTH -> Route.Bluetooth
                CallAudioState.ROUTE_WIRED_HEADSET -> Route.Wired
                else -> Route.Earpiece
            }
        val currentId =
            when (current) {
                Route.Bluetooth -> audioState.activeBluetoothDevice?.let { "bt:${it.address}" } ?: BLUETOOTH
                Route.Speaker -> SPEAKER
                Route.Wired -> WIRED
                else -> EARPIECE
            }
        CallManager.setAudio(AudioInfo(audioState.isMuted, current, currentId, available))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        CallManager.setAudio(CallManager.audio.value.copy(current = routeOf(callEndpoint), currentId = callEndpoint.identifier.toString()))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        endpoints = availableEndpoints
        val available =
            availableEndpoints.map {
                val route = routeOf(it)
                AudioRoute(it.identifier.toString(), route, if (route == Route.Bluetooth) it.endpointName.toString() else null)
            }
        CallManager.setAudio(CallManager.audio.value.copy(available = available))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun routeOf(endpoint: CallEndpoint): Route =
        when (endpoint.endpointType) {
            CallEndpoint.TYPE_SPEAKER -> Route.Speaker
            CallEndpoint.TYPE_BLUETOOTH -> Route.Bluetooth
            CallEndpoint.TYPE_WIRED_HEADSET -> Route.Wired
            CallEndpoint.TYPE_STREAMING -> Route.Streaming
            else -> Route.Earpiece
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMuteStateChanged(isMuted: Boolean) {
        CallManager.setAudio(CallManager.audio.value.copy(muted = isMuted))
    }

    override fun mute(on: Boolean) = setMuted(on)

    override fun route(id: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            endpoints.firstOrNull { it.identifier.toString() == id }?.let { requestCallEndpointChange(it, mainExecutor) { } }
            return
        }
        @Suppress("DEPRECATION")
        when (id) {
            EARPIECE -> setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            WIRED -> setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET)
            SPEAKER -> setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            BLUETOOTH -> setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
            else -> bluetooth[id]?.let(::requestBluetoothAudio)
        }
    }

    private fun notifyCalls() {
        val manager = NotificationManagerCompat.from(this)
        val call = CallManager.state.value.firstOrNull { !it.ended }
        if (call == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val person =
            Person
                .Builder()
                .setName(call.name ?: call.number)
                // Lets Do Not Disturb match the caller against starred contacts.
                .apply { if (call.number.isNotBlank()) setUri("tel:${call.number}") }
                .setImportant(true)
                .build()
        val style =
            if (call.ringing) {
                NotificationCompat.CallStyle.forIncomingCall(person, action(ACTION_DECLINE, call.id), answerIntent(call.id))
            } else {
                NotificationCompat.CallStyle.forOngoingCall(person, action(ACTION_HANG_UP, call.id))
            }
        // "Suspected spam", or where an unknown caller is from, on the notification too.
        when {
            call.spam -> style.setVerificationText(getString(R.string.suspected_spam))
            call.location != null -> style.setVerificationText(call.location)
        }
        val builder =
            NotificationCompat
                .Builder(this, if (call.ringing) CHANNEL_INCOMING else CHANNEL_ONGOING)
                .setSmallIcon(DesignR.drawable.ic_call)
                .setStyle(style)
                // CallStyle's caller isn't in the people list Do Not Disturb checks for starred contacts.
                .addPerson(person)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                // The channels are silent (Telecom rings); state updates shouldn't pop up again either.
                .setOnlyAlertOnce(true)
        if (call.ringing) builder.setFullScreenIntent(open, true)
        if (call.active && call.connectedAt > 0) builder.setUsesChronometer(true).setWhen(call.connectedAt)
        // Call-style notifications must belong to a foreground service; a call in progress is one.
        // A notification problem must never take the call service down, so failures are swallowed.
        try {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (_: RuntimeException) {
            try {
                manager.notify(NOTIFICATION_ID, builder.build())
            } catch (_: SecurityException) {
                // Notifications not allowed: the in-call screen still works.
            } catch (_: IllegalArgumentException) {
                // Not allowed to post: the in-call screen still works.
            }
        }
    }

    /** Answer opens the in-call screen, which answers: a broadcast receiver may not start an activity. */
    private fun answerIntent(id: String): PendingIntent =
        PendingIntent.getActivity(
            this,
            ACTION_ANSWER.hashCode(),
            Intent(
                this,
                InCallActivity::class.java,
            ).setAction(ACTION_ANSWER).putExtra(EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(
        action: String,
        id: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            Intent(this, CallActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        /** Silent channels: Telecom plays the ringtone and vibrates, as the user's settings say. */
        const val CHANNEL_INCOMING = "incoming_calls"
        const val CHANNEL_ONGOING = "ongoing_calls"

        /** Before 0.2.3 this channel made the notification sound, and Telecom didn't ring at all. */
        private const val OLD_CHANNEL = "calls"

        // Route ids for the pre-14 audio API.
        private const val EARPIECE = "earpiece"
        private const val WIRED = "wired"
        private const val SPEAKER = "speaker"
        private const val BLUETOOTH = "bluetooth"
        const val NOTIFICATION_ID = 1
        const val ACTION_ANSWER = "io.github.michaelmassoni.tessera.phone.ANSWER"
        const val ACTION_DECLINE = "io.github.michaelmassoni.tessera.phone.DECLINE"
        const val ACTION_HANG_UP = "io.github.michaelmassoni.tessera.phone.HANG_UP"
        const val EXTRA_ID = "id"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.deleteNotificationChannel(OLD_CHANNEL)

            fun silent(
                id: String,
                name: Int,
                importance: Int,
            ) = NotificationChannel(id, context.getString(name), importance).apply {
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannels(
                listOf(
                    silent(CHANNEL_INCOMING, R.string.channel_incoming_calls, NotificationManager.IMPORTANCE_HIGH),
                    silent(CHANNEL_ONGOING, R.string.channel_ongoing_calls, NotificationManager.IMPORTANCE_DEFAULT),
                ),
            )
        }
    }
}

/** Answer, decline and hang up from the call notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getStringExtra(TesseraInCallService.EXTRA_ID) ?: return
        when (intent.action) {
            TesseraInCallService.ACTION_DECLINE -> {
                CallManager.reject(id)
            }

            TesseraInCallService.ACTION_HANG_UP -> {
                CallManager.hangUp(id)
            }
        }
    }
}
