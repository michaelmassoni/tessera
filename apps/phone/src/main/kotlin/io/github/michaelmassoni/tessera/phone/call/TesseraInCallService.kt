package io.github.michaelmassoni.tessera.phone.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

    @Deprecated("Replaced by onCallEndpointChanged and onMuteStateChanged on API 34+")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        CallManager.setAudio(AudioInfo(muted = audioState.isMuted, speaker = audioState.route == CallAudioState.ROUTE_SPEAKER))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        CallManager.setAudio(CallManager.audio.value.copy(speaker = callEndpoint.endpointType == CallEndpoint.TYPE_SPEAKER))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        endpoints = availableEndpoints
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMuteStateChanged(isMuted: Boolean) {
        CallManager.setAudio(CallManager.audio.value.copy(muted = isMuted))
    }

    override fun mute(on: Boolean) = setMuted(on)

    override fun speaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val wanted = if (on) CallEndpoint.TYPE_SPEAKER else CallEndpoint.TYPE_EARPIECE
            val target =
                endpoints.firstOrNull { it.endpointType == wanted }
                    ?: endpoints.firstOrNull { it.endpointType != CallEndpoint.TYPE_SPEAKER }
            if (target != null) requestCallEndpointChange(target, mainExecutor) { }
        } else {
            @Suppress("DEPRECATION")
            setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
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
                .setImportant(true)
                .build()
        val style =
            if (call.ringing) {
                NotificationCompat.CallStyle.forIncomingCall(person, action(ACTION_DECLINE, call.id), answerIntent(call.id))
            } else {
                NotificationCompat.CallStyle.forOngoingCall(person, action(ACTION_HANG_UP, call.id))
            }
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setSmallIcon(DesignR.drawable.ic_call)
                .setStyle(style)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
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
        const val CHANNEL = "calls"
        const val NOTIFICATION_ID = 1
        const val ACTION_ANSWER = "io.github.michaelmassoni.tessera.phone.ANSWER"
        const val ACTION_DECLINE = "io.github.michaelmassoni.tessera.phone.DECLINE"
        const val ACTION_HANG_UP = "io.github.michaelmassoni.tessera.phone.HANG_UP"
        const val EXTRA_ID = "id"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_calls), NotificationManager.IMPORTANCE_HIGH),
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
