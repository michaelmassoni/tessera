package io.github.michaelmassoni.tessera.weather.ui

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.engine.AqiBand
import io.github.michaelmassoni.tessera.weather.engine.PollenLevel
import io.github.michaelmassoni.tessera.weather.engine.Warning
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** A warning in force, under the hero: error colours for major warnings, tertiary for minor. */
@Composable
fun WarningBanner(
    warning: Warning,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (warning.major) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (warning.major) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        onClick = onClick,
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(DesignR.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(warning.shortTitle, style = MaterialTheme.typography.titleSmall)
                Text(warning.title, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A warning's full text from the Bureau, with when it was issued. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WarningSheet(
    warning: Warning,
    text: String?,
    failed: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(warning.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(
                    R.string.warning_issued,
                    DateUtils.formatDateTime(
                        context,
                        warning.issued,
                        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY,
                    ),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                text != null -> {
                    // BOM sends HTML; shown as plain text, without its embedded images.
                    val plain =
                        remember(text) {
                            text
                                .parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT, { null }, null)
                                .toString()
                                .replace("￼", "")
                                .replace(Regex("\n{3,}"), "\n\n")
                                .trim()
                        }
                    Text(plain, style = MaterialTheme.typography.bodyMedium)
                }

                failed -> {
                    Text(stringResource(R.string.warning_failed), color = MaterialTheme.colorScheme.error)
                }

                else -> {
                    LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
            }
            Text(
                stringResource(R.string.warning_source),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
fun AqiBand.label(): Int =
    when (this) {
        AqiBand.Good -> R.string.aqi_good
        AqiBand.Moderate -> R.string.aqi_moderate
        AqiBand.SensitiveGroups -> R.string.aqi_sensitive
        AqiBand.Unhealthy -> R.string.aqi_unhealthy
        AqiBand.VeryUnhealthy -> R.string.aqi_very_unhealthy
        AqiBand.Hazardous -> R.string.aqi_hazardous
    }

@StringRes
fun PollenLevel.label(): Int =
    when (this) {
        PollenLevel.None -> R.string.pollen_none
        PollenLevel.Low -> R.string.pollen_low
        PollenLevel.Moderate -> R.string.pollen_moderate
        PollenLevel.High -> R.string.pollen_high
        PollenLevel.VeryHigh -> R.string.pollen_very_high
    }

@StringRes
fun pollenLabel(type: String): Int =
    when (type) {
        "alder" -> R.string.pollen_alder
        "birch" -> R.string.pollen_birch
        "grass" -> R.string.pollen_grass
        "mugwort" -> R.string.pollen_mugwort
        "olive" -> R.string.pollen_olive
        else -> R.string.pollen_ragweed
    }
