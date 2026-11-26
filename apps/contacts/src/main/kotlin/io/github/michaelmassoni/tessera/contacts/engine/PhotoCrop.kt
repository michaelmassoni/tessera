package io.github.michaelmassoni.tessera.contacts.engine

/**
 * A contact photo from a picked picture: the largest centred square, scaled down to at most
 * [MAX_SIDE] pixels (what Contacts stores as the display photo). Pure, so it's unit tested.
 */
object PhotoCrop {
    const val MAX_SIDE = 720

    data class Crop(
        val left: Int,
        val top: Int,
        val side: Int,
        /** The side of the saved photo. */
        val target: Int,
    )

    fun square(
        width: Int,
        height: Int,
    ): Crop {
        val side = minOf(width, height)
        return Crop((width - side) / 2, (height - side) / 2, side, minOf(side, MAX_SIDE))
    }
}
