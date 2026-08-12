// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.deox9.musicplayer.audio.EqBand
import com.deox9.musicplayer.audio.EqBandType
import com.deox9.musicplayer.audio.EqResponse
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The response of the current bands, drawn.
 *
 * The thing a parametric equaliser cannot be used without. A graphic equaliser shows
 * its shape in the position of its sliders; here each band is three numbers whose
 * combined effect nobody can picture — two overlapping bells at +6 dB are +12 dB
 * where they meet, and the sliders do not say so.
 */
@Composable
internal fun EqCurve(
    bands: List<EqBand>,
    sampleRate: Int,
    modifier: Modifier = Modifier,
) {
    val curveColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CURVE_HEIGHT)
            // Read out, a path is noise; the shape is described instead.
            .semantics { contentDescription = describeCurve(bands) },
    ) {
        val frequencies = EqResponse.logSpacedFrequencies(size.width.toInt().coerceAtLeast(2))
        val response = EqResponse.magnitudeDb(bands, sampleRate, frequencies)

        drawGrid(gridColor, zeroColor)

        val path = Path()
        response.forEachIndexed { index, db ->
            val x = index.toFloat() / (response.size - 1) * size.width
            val y = size.height / 2f - (db / CURVE_RANGE_DB).toFloat() * size.height / 2f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, curveColor, style = Stroke(width = CURVE_STROKE))
    }
}

/** Decade lines and the 0 dB axis, so the curve has something to be read against. */
private fun DrawScope.drawGrid(gridColor: Color, zeroColor: Color) {
    listOf(100.0, 1000.0, 10_000.0).forEach { frequency ->
        val fraction = (log10(frequency) - log10(EqResponse.MIN_FREQUENCY_HZ)) /
            (log10(EqResponse.MAX_FREQUENCY_HZ) - log10(EqResponse.MIN_FREQUENCY_HZ))
        val x = fraction.toFloat() * size.width
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
    drawLine(
        zeroColor,
        Offset(0f, size.height / 2f),
        Offset(size.width, size.height / 2f),
        strokeWidth = 1f,
    )
}

/**
 * The curve in words, for a screen reader.
 *
 * Names the bands rather than the path: "boost 6 dB at 1 kHz" is the information the
 * picture carries, and a list of coordinates is not.
 */
private fun describeCurve(bands: List<EqBand>): String {
    val active = bands.filterNot { it.isTransparent }
    if (active.isEmpty()) return "Equaliser response, flat"
    return "Equaliser response: " + active.joinToString(", ") { band ->
        val direction = if (band.gainDb >= 0) "boost" else "cut"
        "$direction ${kotlin.math.abs(band.gainDb).roundToInt()} decibels at ${formatHz(band.frequencyHz)}"
    }
}

@Composable
internal fun EqBandEditor(
    band: EqBand,
    index: Int,
    onChange: (EqBand) -> Unit,
    onRemove: () -> Unit,
) {
    var typeMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${index + 1}. ${formatHz(band.frequencyHz)} · ${band.type.label()}",
                style = MaterialTheme.typography.titleSmall,
            )
            Row {
                TextButton(onClick = { typeMenuOpen = true }) { Text("Type") }
                DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    EqBandType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label()) },
                            onClick = {
                                onChange(band.copy(type = type))
                                typeMenuOpen = false
                            },
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove band ${index + 1}",
                    )
                }
            }
        }

        // Frequency moves on a log scale, so a drag covers the same number of
        // octaves everywhere. Linear, the entire bottom half of the range would sit
        // in the first few percent of the track.
        LabelledSlider(
            label = "Frequency",
            value = frequencyToFraction(band.frequencyHz),
            valueText = formatHz(band.frequencyHz),
            onValueChange = { onChange(band.copy(frequencyHz = fractionToFrequency(it))) },
        )

        // A pass filter's gain does nothing, so offering it would be a lie.
        if (band.type != EqBandType.HighPass && band.type != EqBandType.LowPass) {
            LabelledSlider(
                label = "Gain",
                value = ((band.gainDb - MIN_DB) / (MAX_DB - MIN_DB)).toFloat(),
                valueText = "%+.1f dB".format(band.gainDb),
                onValueChange = { onChange(band.copy(gainDb = MIN_DB + it * (MAX_DB - MIN_DB))) },
            )
        }

        LabelledSlider(
            label = "Q",
            value = ((band.q - MIN_Q) / (MAX_Q - MIN_Q)).toFloat(),
            valueText = "%.2f".format(band.q),
            onValueChange = { onChange(band.copy(q = MIN_Q + it * (MAX_Q - MIN_Q))) },
        )

        HorizontalDivider()
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    valueText: String,
    onValueChange: (Double) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(valueText, style = MaterialTheme.typography.labelMedium)
    }
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = { onValueChange(it.toDouble()) },
        modifier = Modifier.semantics { contentDescription = "$label, $valueText" },
    )
}

private fun EqBandType.label(): String = when (this) {
    EqBandType.Peaking -> "Peak"
    EqBandType.LowShelf -> "Low shelf"
    EqBandType.HighShelf -> "High shelf"
    EqBandType.HighPass -> "High pass"
    EqBandType.LowPass -> "Low pass"
}

internal fun formatHz(frequencyHz: Double): String = when {
    frequencyHz >= 1000.0 -> "%.1f kHz".format(frequencyHz / 1000.0)
    else -> "${frequencyHz.roundToInt()} Hz"
}

private fun frequencyToFraction(frequencyHz: Double): Float {
    val min = log10(EqResponse.MIN_FREQUENCY_HZ)
    val max = log10(EqResponse.MAX_FREQUENCY_HZ)
    val clamped = frequencyHz.coerceIn(EqResponse.MIN_FREQUENCY_HZ, EqResponse.MAX_FREQUENCY_HZ)
    return ((log10(clamped) - min) / (max - min)).toFloat()
}

private fun fractionToFrequency(fraction: Double): Double {
    val min = log10(EqResponse.MIN_FREQUENCY_HZ)
    val max = log10(EqResponse.MAX_FREQUENCY_HZ)
    val clamped = fraction.coerceIn(0.0, 1.0)
    return 10.0.pow(min + clamped * (max - min))
}

private const val MIN_DB = -24.0
private const val MAX_DB = 24.0
private const val MIN_Q = 0.1
private const val MAX_Q = 18.0

/** The curve is drawn over ±24 dB, matching the range a band can be set to. */
private const val CURVE_RANGE_DB = 24.0
private const val CURVE_STROKE = 3f
private val CURVE_HEIGHT = 140.dp
