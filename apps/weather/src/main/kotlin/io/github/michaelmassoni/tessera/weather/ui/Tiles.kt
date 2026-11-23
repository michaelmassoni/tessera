package io.github.michaelmassoni.tessera.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.engine.Metric
import io.github.michaelmassoni.tessera.weather.engine.VisibilityBand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What a tile draws behind its text. */
sealed interface Look {
    /** A shaped tile in a container colour, its text centred. */
    data class Shaped(
        val shape: TileShape,
        val color: TileColor,
    ) : Look

    /** UV: a scalloped tile with a dot per band, [band] (0–4) lit; [high] for high and above. */
    data class Uv(
        val band: Int,
        val high: Boolean,
    ) : Look

    /** Wind: a round tile with a big arrow the way the wind blows ([towards], degrees from north). */
    data class Wind(
        val towards: Int,
    ) : Look

    /** Humidity: a wave filled to [fraction], with the dew point in a badge. */
    data class Humidity(
        val fraction: Float,
        val dewPoint: String?,
    ) : Look

    /** A round dial filled to [fraction]; [alarm] draws it in the error colour. */
    data class Gauge(
        val fraction: Float,
        val alarm: Boolean = false,
    ) : Look

    /** Visibility: a round tile with a soft burst inside. */
    data object Visibility : Look

    /** The sun's arc from [rise] to [set] (times as text), with the sun at [progress]. */
    data class Sun(
        val progress: Float,
        val rise: String,
        val set: String,
    ) : Look
}

enum class TileShape { Clover, Cloud, Flower }

enum class TileColor { Primary, Secondary, Tertiary }

data class Tile(
    /** What the tile opens in the day details (nothing for air quality and pollen). */
    val metric: Metric?,
    val icon: Int,
    val label: String,
    val value: String,
    val note: String? = null,
    /** The part of [value] that's a unit, drawn smaller ("km/h" in "33 km/h"). */
    val unit: String? = null,
    val look: Look = Look.Shaped(TileShape.Clover, TileColor.Primary),
)

fun VisibilityBand.label(): Int =
    when (this) {
        VisibilityBand.VeryPoor -> R.string.visibility_very_poor
        VisibilityBand.Poor -> R.string.visibility_poor
        VisibilityBand.Moderate -> R.string.visibility_moderate
        VisibilityBand.Good -> R.string.visibility_good
    }

/** At least as tall as it is wide (taller when large text needs it). */
private fun Modifier.squareAtLeast(): Modifier =
    layout { measurable, constraints ->
        val width = constraints.maxWidth
        val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width, minHeight = width))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TileView(
    tile: Tile,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val look = tile.look
    val shape: Shape =
        when (look) {
            is Look.Shaped -> {
                when (look.shape) {
                    TileShape.Clover -> MaterialShapes.Clover4Leaf
                    TileShape.Cloud -> MaterialShapes.Puffy
                    TileShape.Flower -> MaterialShapes.Flower
                }.toShape()
            }

            is Look.Uv -> {
                MaterialShapes.Cookie12Sided.toShape()
            }

            is Look.Wind, is Look.Gauge, Look.Visibility -> {
                CircleShape
            }

            is Look.Humidity, is Look.Sun -> {
                MaterialTheme.shapes.extraLarge
            }
        }
    val (fill, content) =
        when (look) {
            is Look.Shaped -> {
                when (look.color) {
                    TileColor.Primary -> colors.primaryContainer to colors.onPrimaryContainer
                    TileColor.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
                    TileColor.Tertiary -> colors.tertiaryContainer to colors.onTertiaryContainer
                }
            }

            else -> {
                colors.surfaceContainerHigh to colors.onSurface
            }
        }
    val centred = look !is Look.Humidity && look !is Look.Sun
    Box(
        modifier
            .squareAtLeast()
            .clip(shape)
            .background(fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        Backdrop(look)
        Column(
            Modifier
                .fillMaxWidth()
                .align(if (centred) Alignment.Center else Alignment.TopStart)
                .padding(horizontal = if (centred) 28.dp else 20.dp, vertical = if (centred) 24.dp else 20.dp),
            horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(tile.icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Text(
                    tile.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (look !is Look.Sun) {
                Text(
                    value(tile.value, tile.unit),
                    style = MaterialTheme.typography.displaySmall,
                    color = content,
                    textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.padding(top = if (centred) 4.dp else 16.dp),
                )
            }
            when (look) {
                is Look.Humidity -> {
                    look.dewPoint?.let { DewPoint(it) }
                }

                is Look.Sun -> {
                    SunTimes(look, tile.value)
                }

                else -> {
                    tile.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.8f),
                            textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}

/** [text] with its [unit] part smaller. */
private fun value(
    text: String,
    unit: String?,
): AnnotatedString {
    val at = unit?.takeIf { it.isNotEmpty() }?.let { text.lastIndexOf(it) } ?: -1
    if (at < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, at))
        withStyle(SpanStyle(fontSize = 0.5.em)) { append(unit) }
        append(text.substring(at + unit!!.length))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.Backdrop(look: Look) {
    val colors = MaterialTheme.colorScheme
    when (look) {
        is Look.Wind -> {
            Box(Modifier.matchParentSize().padding(14.dp), contentAlignment = Alignment.Center) {
                // Arrow points up at 0°; turn it to where the wind is heading.
                Box(
                    Modifier
                        .fillMaxSize()
                        .rotate(look.towards.toFloat())
                        .background(colors.secondaryContainer, MaterialShapes.Arrow.toShape()),
                )
            }
        }

        Look.Visibility -> {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(12.dp)
                    .background(colors.primaryContainer.copy(alpha = 0.8f), MaterialShapes.SoftBurst.toShape()),
            )
        }

        is Look.Humidity -> {
            val wave = colors.tertiaryContainer
            Canvas(Modifier.matchParentSize()) { drawWave(look.fraction, wave) }
        }

        is Look.Gauge -> {
            val track = colors.surfaceContainerHighest
            val bar = if (look.alarm) colors.error else colors.primary
            Canvas(Modifier.matchParentSize().padding(14.dp)) { drawGauge(look.fraction, track, bar) }
        }

        is Look.Uv -> {
            val lit = if (look.high) colors.error else colors.tertiary
            val dim = colors.onSurfaceVariant.copy(alpha = 0.25f)
            Canvas(Modifier.matchParentSize()) { drawBands(look.band, lit, dim) }
        }

        is Look.Sun, is Look.Shaped -> {}
    }
}

@Composable
private fun DewPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiary)
        }
        Text(
            stringResource(R.string.dew_point_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SunTimes(
    look: Look.Sun,
    description: String,
) {
    val path = MaterialTheme.colorScheme.tertiary
    val sun = MaterialTheme.colorScheme.tertiaryContainer
    val dim = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().padding(top = 20.dp, start = 4.dp, end = 4.dp).height(56.dp)) {
            drawSun(look.progress, path, sun, dim)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(look.rise, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(look.set, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A gentle wave across the tile, its crest at [fraction] of the height from the bottom. */
private fun DrawScope.drawWave(
    fraction: Float,
    color: Color,
) {
    val top = size.height * (1 - fraction.coerceIn(0f, 1f))
    val amp = 6.dp.toPx()
    val path =
        Path().apply {
            moveTo(0f, top)
            val steps = 48
            for (i in 0..steps) {
                val x = size.width * i / steps
                lineTo(x, top + amp * sin(2 * PI * i / steps * 1.5).toFloat())
            }
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    drawPath(path, color)
}

/** A 270° dial open at the bottom, filled to [fraction]. */
private fun DrawScope.drawGauge(
    fraction: Float,
    track: Color,
    bar: Color,
) {
    val width = 10.dp.toPx()
    val inset = width / 2
    val arc = Size(size.width - width, size.height - width)
    val stroke = Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawArc(track, 135f, 270f, false, Offset(inset, inset), arc, style = stroke)
    if (fraction > 0f) drawArc(bar, 135f, 270f * fraction, false, Offset(inset, inset), arc, style = stroke)
}

/** Five dots along the bottom of the tile, the [lit] one (the band now) bigger. */
private fun DrawScope.drawBands(
    band: Int,
    lit: Color,
    dim: Color,
) {
    val radius = size.minDimension * 0.36f
    val centre = Offset(size.width / 2, size.height / 2)
    for (i in 0..4) {
        // From lower left (150°) round the bottom to lower right (30°).
        val angle = Math.toRadians(150.0 - i * 30.0)
        val at = Offset(centre.x + radius * cos(angle).toFloat(), centre.y + radius * sin(angle).toFloat())
        drawCircle(if (i == band) lit else dim, if (i == band) 9.dp.toPx() else 6.dp.toPx(), at)
    }
}

/** The day's arc (dashed), the part already travelled solid, and the sun on it. */
private fun DrawScope.drawSun(
    progress: Float,
    path: Color,
    sun: Color,
    dim: Color,
) {
    val box = Size(size.width, size.height * 2)
    drawLine(
        dim,
        Offset(0f, size.height),
        Offset(size.width, size.height),
        1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
    )
    drawArc(
        dim,
        180f,
        180f,
        false,
        Offset.Zero,
        box,
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
    )
    if (progress >
        0f
    ) {
        drawArc(
            path,
            180f,
            180f * progress,
            false,
            Offset.Zero,
            box,
            style = Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
    val angle = Math.toRadians(180.0 + 180.0 * progress)
    val at = Offset(size.width / 2 + size.width / 2 * cos(angle).toFloat(), size.height + size.height * sin(angle).toFloat())
    drawCircle(sun, 11.dp.toPx(), at)
    drawCircle(path, 11.dp.toPx(), at, style = Stroke(3.dp.toPx()))
}
