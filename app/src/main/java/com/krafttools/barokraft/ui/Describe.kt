package com.krafttools.barokraft.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.barokraft.core.BaroScale
import com.krafttools.barokraft.core.fmt1
import com.kraft.ui.tokens.KraftTypeScale
import com.kraft.ui.tokens.KraftSpacing

/**
 * A sentence about the pressure trace, built from the trace.
 *
 * ## Why this exists
 *
 * Because a `Canvas` announces nothing at all. Without it the only picture
 * in this app is invisible to a blind reader while looking complete to
 * everyone else, and that is a bug rather than a nicety.
 *
 * ## Why it is built from the data rather than written
 *
 * A hardcoded "pressure is falling" would be wrong whenever it was wrong,
 * which is to say most of the time. Deriving the direction from the actual
 * first and last values means the sentence cannot contradict the drawing
 * above it.
 */
fun traceDescription(values: List<Float>, spanMillis: Long = 0L): String {
    if (values.isEmpty()) return "No pressure readings yet."
    val change = values.last() - values.first()
    val span = values.max() - values.min()
    val direction = when {
        kotlin.math.abs(change) < 0.2f -> "essentially flat"
        change < 0 -> "falling"
        else -> "rising"
    }
    val window = when {
        spanMillis <= 0L -> ""
        spanMillis < 3_600_000L -> "over ${spanMillis / 60_000L} minutes"
        else -> "over ${spanMillis / 3_600_000L} hours"
    }
    return "Pressure trace of ${values.size} readings $window, $direction by " +
        "${fmt1(kotlin.math.abs(change))} hectopascals, spanning " +
        "${fmt1(span)} hectopascals."
}

/** An age, in the words a person uses rather than a number of minutes. */
internal fun ageLabel(hours: Float): String = when {
    // roundToInt, not toInt: truncation reported 19.8 minutes as "19",
    // which is a small lie about how old a forecast is. Caught by a test.
    hours < 1f -> "${(hours * 60f).let { kotlin.math.round(it).toInt() }} minutes"
    hours < 2f -> "1 hour"
    else -> "${kotlin.math.round(hours).toInt()} hours"
}

/**
 * A label and a value on one line.
 *
 * The whole row is a single announcement rather than two fragments, so a
 * screen reader says "Altitude, reference ageing: 100 metres" instead of
 * the caveat arriving as a separate unrelated item.
 */
@Composable
internal fun KeyValue(key: String, value: String, caveat: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = KraftSpacing.Spacing4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(key, style = label.copy(fontSize = KraftTypeScale.Caption1), color = TextSecondary)
            caveat?.let {
                Spacer(Modifier.height(KraftSpacing.BorderWidth))
                Text(it, style = label.copy(fontSize = KraftTypeScale.Badge), color = Warning)
            }
        }
        Text(
            value,
            style = label,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = buildString {
                    append(key)
                    caveat?.let { append(", $it") }
                    append(": $value")
                }
            },
        )
    }
}

/** Kept so the scale type stays referenced by this file's documentation. */
internal typealias TraceScale = BaroScale
