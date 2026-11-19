package io.github.michaelmassoni.tessera.phone.engine

/** Where call audio goes. */
enum class Route { Earpiece, Speaker, Bluetooth, Wired, Streaming }

/** One place call audio can go: [id] is what the in-call service uses to switch to it. */
data class AudioRoute(
    val id: String,
    val route: Route,
    /** A device name (for Bluetooth), or null to use the route's own label. */
    val name: String? = null,
)

data class AudioInfo(
    val muted: Boolean = false,
    val current: Route = Route.Earpiece,
    val currentId: String? = null,
    val available: List<AudioRoute> = emptyList(),
) {
    /**
     * With only the earpiece and speaker, a Speaker toggle is enough; with a headset or Bluetooth
     * there's a real choice, so the button opens a list.
     */
    val needsPicker: Boolean get() = available.any { it.route != Route.Earpiece && it.route != Route.Speaker }

    val speaker: Boolean get() = current == Route.Speaker

    /** The route for the Speaker toggle: speaker, or back to the earpiece (or a wired headset). */
    fun toggleTarget(): AudioRoute? =
        if (speaker) {
            available.firstOrNull { it.route == Route.Wired } ?: available.firstOrNull { it.route == Route.Earpiece }
        } else {
            available.firstOrNull { it.route == Route.Speaker }
        }
}

object Proximity {
    /**
     * The screen should turn off near the ear while a call is live (not ringing, not over) and
     * its audio is on the earpiece: that's when the phone is held to the face.
     */
    fun wanted(
        liveCall: Boolean,
        route: Route,
    ): Boolean = liveCall && route == Route.Earpiece
}
