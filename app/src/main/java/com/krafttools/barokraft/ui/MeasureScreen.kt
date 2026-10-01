package com.krafttools.barokraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.krafttools.barokraft.core.Method
import com.krafttools.barokraft.core.Policy
import com.krafttools.barokraft.core.ReferenceStaleness
import com.krafttools.barokraft.core.SourceState
import com.krafttools.barokraft.core.Verdict
import com.krafttools.barokraft.core.VerdictTone
import com.krafttools.barokraft.core.baroScale
import com.krafttools.barokraft.core.fmt1
import com.krafttools.barokraft.core.tendencyHpaPerHour
import com.krafttools.barokraft.net.Failure

/**
 * The screen, as a pure function of [MeasureState].
 *
 * This is the whole UI, and it reads nothing but its argument. That is
 * not architectural purity for its own sake — it is what makes every state
 * above reachable from a test without a sensor, a network, a clock or a
 * phone, and the alternative was demonstrated to be worse in a sibling
 * project where a layout bug could only be found by rotating a real
 * handset and squinting.
 */

/** Spacing scale. 8dp rhythm, because the rest of this family uses it. */
private val Gap = 8.dp
private val Pad = 20.dp

@Composable
fun MeasureContent(
    state: MeasureState,
    nowMillis: Long,
    onToggleSampling: () -> Unit = {},
    onOpenPlacePicker: () -> Unit = {},
    onOpenMethod: () -> Unit = {},
    onDismissMethod: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // Edge-to-edge is enforced from targetSdk 35, so the window
                // no longer insets itself and the header draws *under* the
                // status bar. Found by dumping the real view hierarchy on a
                // Pixel: the city chip's bounds were y=40..166, which puts its
                // top 23px behind the status bar — the control is drawn but
                // the system eats the tap.
                //
                // `safeDrawing` rather than `statusBars` so the navigation
                // bar is covered too, on the three-button navigation this
                // app will meet on a six-year-old handset.
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Gap * 2),
        ) {
            Header(state, onOpenPlacePicker)

            state.verdict?.let { VerdictBlock(it) }

            if (state.isEmpty && state.verdict == null) {
                EmptyNotice(state)
            }

            // The local block. Entirely absent on a phone with no
            // barometer, rather than present and empty — a row of zeros
            // is a claim about the weather and this app will not make it.
            if (state.capabilities.canShowPressure) {
                LocalBlock(state)
            }

            // The trace, when there is a barometer and enough history to
            // draw one. Six samples is the minimum the nowcast needs, and
            // drawing below that would be drawing an interpolation of
            // nothing.
            if (state.capabilities.canShowPressure && state.samples.size >= 6) {
                TraceBlock(state)
            }

            if (state.capabilities.canShowForecast) {
                ForecastBlock(state, nowMillis)
            }

            FailureNotice(state.failure, state)

            Controls(state, onToggleSampling, onOpenMethod)
        }

        if (state.showingMethod) {
            MethodSheet(onDismissMethod)
        }
    }
}

@Composable
private fun Header(state: MeasureState, onOpenPlacePicker: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "BaroKraft",
                style = label.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            // The state, stated. Four sources of information in one line
            // so the user never has to wonder why a figure is missing.
            Text(
                stateLabel(state),
                style = label.copy(fontSize = 12.sp),
                color = TextMuted,
            )
        }
        if (state.capabilities.canShowForecast) {
            PlaceChip(state, onOpenPlacePicker)
        }
    }
}

/**
 * The honest one-liner for the current state.
 *
 * This is where the four-state design pays off. A phone with no
 * barometer and a working network says "Forecast only" — which is what it
 * is, and not a warning. Only [SourceState.NEITHER] gets a caution, and
 * only because there is genuinely nothing on screen.
 */
private fun stateLabel(state: MeasureState): String = when (state.sourceState) {
    SourceState.BOTH -> "Barometer + forecast"
    SourceState.BAROMETER_ONLY -> "Barometer only, offline"
    SourceState.NETWORK_ONLY -> "Forecast only — no barometer on this device"
    SourceState.NEITHER -> "No barometer, no connection"
}

@Composable
private fun PlaceChip(state: MeasureState, onOpenPlacePicker: () -> Unit) {
    // Named `name` rather than `label`: a local `label` would shadow the
    // type scale for the rest of the function, and `label.copy(...)`
    // would then resolve against a String.
    val name = state.place?.name ?: "Choose a city"
    Text(
        text = name,
        style = label.copy(fontSize = 13.sp),
        color = if (state.place != null) Accent else TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .then(
                Modifier.semantics {
                    contentDescription =
                        if (state.place != null) "City: $name. Tap to change."
                        else "No city chosen. Tap to choose one."
                },
            )
            .clickableNoRipple(onOpenPlacePicker),
    )
}

@Composable
private fun VerdictBlock(verdict: Verdict) {
    val accent = when (verdict.tone) {
        VerdictTone.BLOCKED -> Critical
        VerdictTone.WARNING -> Warning
        VerdictTone.CALIBRATION -> Warning
        VerdictTone.DIVERGENCE -> Accent
        VerdictTone.NOWCAST -> Accent
        VerdictTone.QUIET -> TextPrimary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(16.dp)
            .semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            verdict.headline,
            style = label.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
            color = accent,
        )
        verdict.detail?.let {
            Text(it, style = label.copy(fontSize = 14.sp), color = TextSecondary)
        }
    }
}

@Composable
private fun EmptyNotice(state: MeasureState) {
    Text(
        when (state.sourceState) {
            SourceState.NEITHER ->
                "There is nothing to read. This app needs either a pressure sensor " +
                    "or a connection, and this device currently has neither."
            SourceState.NETWORK_ONLY ->
                "No pressure sensor on this device, so there is no local reading. " +
                    "The forecast below is the whole of what this app can tell you."
            else -> "Reading the barometer…"
        },
        style = label.copy(fontSize = 14.sp),
        color = TextMuted,
    )
}

@Composable
private fun LocalBlock(state: MeasureState) {
    Card {
        val hpa = state.currentHpa
        // The figure. Tabular figures, always: a value that updates every
        // few seconds must not reflow its own digits.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = hpa?.let { fmt1(it) } ?: "—",
                style = figure,
                color = if (hpa != null) TextPrimary else TextMuted,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "hPa",
                style = label.copy(fontSize = 16.sp),
                color = TextMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        tendencyHpaPerHour(state.samples)?.let { rate ->
            Spacer(Modifier.height(Gap))
            TrendRow(rate)
        }

        if (state.capabilities.canShowAltitude) {
            val alt = state.altitudeMetres
            if (alt != null) {
                Spacer(Modifier.height(Gap))
                KeyValue(
                    "Altitude",
                    "${fmt1(alt)} m",
                    // The one-line caveat, printed rather than buried. An
                    // altitude without its reference is a number that
                    // looks authoritative and is not.
                    caveat = when (state.staleness) {
                        ReferenceStaleness.AGING -> "reference is ageing"
                        else -> null
                    },
                )
            }
        } else {
            Spacer(Modifier.height(Gap))
            Text(
                "No altitude. " + when (state.staleness) {
                    ReferenceStaleness.STALE ->
                        "The sea-level reference is too old to use, and a stale one " +
                            "would give a plausible wrong number."
                    else -> "Set a sea-level reference to get one."
                },
                style = label.copy(fontSize = 12.sp),
                color = TextMuted,
            )
        }

        Spacer(Modifier.height(Gap))
        Text(
            Method.PRESSURE_SOURCE,
            style = label.copy(fontSize = 11.sp),
            color = TextMuted,
        )
    }
}

@Composable
private fun TrendRow(hpaPerHour: Float) {
    val trend = com.krafttools.barokraft.core.classifyTendency(hpaPerHour)
    val colour = when (trend) {
        com.krafttools.barokraft.core.BaroTrend.FALLING,
        com.krafttools.barokraft.core.BaroTrend.FALLING_FAST -> Falling
        com.krafttools.barokraft.core.BaroTrend.RISING,
        com.krafttools.barokraft.core.BaroTrend.RISING_FAST -> Rising
        com.krafttools.barokraft.core.BaroTrend.STEADY -> Steady
    }
    // The arrow and the words are the message; the colour is reinforcement.
    // A trend carried by colour alone is invisible to a reader with
    // deuteranopia, and this app's severity accents are chosen for the
    // same reason.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            contentDescription = "Pressure ${trend.label.lowercase()}, ${fmt1(kotlin.math.abs(hpaPerHour))} hectopascals an hour"
        },
    ) {
        Text(trend.arrow, style = label.copy(fontSize = 18.sp), color = colour)
        Spacer(Modifier.width(6.dp))
        Text(trend.label, style = label.copy(fontSize = 15.sp), color = colour)
        Spacer(Modifier.width(8.dp))
        Text(
            "${fmt1(kotlin.math.abs(hpaPerHour))} hPa/h",
            style = label.copy(fontSize = 13.sp),
            color = TextMuted,
        )
    }
}

@Composable
private fun TraceBlock(state: MeasureState) {
    val values = state.samples.map { it.hpa }
    val scale = baroScale(values)
    Card {
        Text(
            "Pressure, last ${values.size} samples",
            style = label.copy(fontSize = 13.sp),
            color = TextSecondary,
        )
        Spacer(Modifier.height(Gap))
        PressureTrace(
            values = values,
            scale = scale,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics {
                    // The plot describes itself from its own data, because
                    // a Canvas announces nothing at all and this would
                    // otherwise be a picture invisible to a blind reader.
                    contentDescription = traceDescription(values, scale)
                },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Scale is centred on the median and sized to the variation. " +
                "A barometer's signal is the change, not the value.",
            style = label.copy(fontSize = 11.sp),
            color = TextMuted,
        )
    }
}

@Composable
private fun ForecastBlock(state: MeasureState, nowMillis: Long) {
    val forecast = state.forecast ?: run {
        Card {
            Text("No forecast yet", style = label.copy(fontSize = 15.sp), color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pull a forecast by choosing a city above.",
                style = label.copy(fontSize = 12.sp),
                color = TextMuted,
            )
        }
        return
    }

    val now = forecast.hourAt(nowMillis)
    Card {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                now?.temperatureC?.let { "${fmt1(it)}°" } ?: "—",
                style = figure,
            )
            Column(horizontalAlignment = Alignment.End) {
                now?.weatherCode?.let {
                    Text(
                        com.krafttools.barokraft.core.Wmo.label(it) ?: "Unknown",
                        style = label.copy(fontSize = 15.sp),
                        color = TextSecondary,
                    )
                }
                now?.windSpeedKmh?.let {
                    Text(
                        "Wind ${fmt1(it)} km/h",
                        style = label.copy(fontSize = 12.sp),
                        color = TextMuted,
                    )
                }
            }
        }

        // Rain probability is printed only when the API actually supplied
        // it. A null rendered as 0% would be a confident false statement
        // about the weather, and this is the single most common way a
        // forecast app lies.
        val probability = now?.precipitationProbability
        if (probability != null) {
            Spacer(Modifier.height(Gap))
            KeyValue("Chance of rain", "$probability%")
        } else {
            Spacer(Modifier.height(Gap))
            Text(
                "No rain probability from this model.",
                style = label.copy(fontSize = 12.sp),
                color = TextMuted,
            )
        }

        state.forecastAgeHours(nowMillis)?.let { age ->
            Spacer(Modifier.height(Gap))
            Text(
                "Fetched ${ageLabel(age)} ago",
                style = label.copy(fontSize = 11.sp),
                color = TextMuted,
            )
        }

        Spacer(Modifier.height(Gap))
        Text(
            Method.FORECAST_DEFINITION,
            style = label.copy(fontSize = 11.sp),
            color = TextMuted,
        )
    }
}

@Composable
private fun FailureNotice(failure: Failure?, state: MeasureState) {
    if (failure == null) return
    if (failure is Failure.NotNeeded) return
    val text = when (failure) {
        is Failure.Offline ->
            "No connection. " + if (state.capabilities.canShowPressure) {
                "The barometer below still works, and is the more immediate half of this app."
            } else {
                "Nothing can be fetched until there is a connection."
            }
        is Failure.RateLimited ->
            "The free API allows 10,000 calls a day and that limit is reached. " +
                "The barometer is unaffected."
        is Failure.Malformed ->
            "The forecast service answered with something this app could not read. " +
                "That is a bug here, not a weather problem."
        is Failure.Server ->
            "The forecast service is unavailable (HTTP ${failure.statusCode})."
        is Failure.Unreachable ->
            "Could not reach the forecast service."
        is Failure.NoPlaceSelected ->
            "Choose a city to get a forecast."
        is Failure.NotNeeded -> ""
    }
    Card {
        Text(text, style = label.copy(fontSize = 13.sp), color = Warning)
    }
}

@Composable
private fun Controls(
    state: MeasureState,
    onToggleSampling: () -> Unit,
    onOpenMethod: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        if (state.capabilities.canShowPressure) {
            Text(
                if (state.sampling) "Stop reading" else "Start reading",
                style = label.copy(fontSize = 14.sp),
                color = if (state.sampling) TextPrimary else Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.sampling) Surface3 else Surface1)
                    .padding(vertical = 14.dp)
                    .then(
                        Modifier.semantics {
                            contentDescription = if (state.sampling) {
                                "Stop reading the barometer"
                            } else {
                                "Start reading the barometer"
                            }
                        },
                    )
                    .clickableNoRipple(onToggleSampling),
            )
        }
        Text(
            "Method",
            style = label.copy(fontSize = 14.sp),
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1)
                .padding(vertical = 14.dp)
                .then(
                    Modifier.semantics {
                        contentDescription = "Read how this app measures and what it cannot tell you"
                    },
                )
                .clickableNoRipple(onOpenMethod),
        )
    }
}

@Composable
private fun MethodSheet(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.92f))
            .clickableNoRipple(onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Surface1)
                .safeDrawingPadding()
                .padding(Pad)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Gap * 1.5f),
        ) {
            Text(
                "What this app measures",
                style = label.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
            MethodParagraph("Pressure", Method.PRESSURE_SOURCE)
            MethodParagraph("Altitude", Method.ALTITUDE_DEPENDS_ON)
            MethodParagraph("Tendency", Method.TENDENCY_DEFINITION)
            MethodParagraph("Nowcast", Method.NOWCAST_DEFINITION)
            MethodParagraph("The forecast", Method.FORECAST_DEFINITION)
            MethodParagraph("Both sources", Method.TWO_SOURCES)

            Spacer(Modifier.height(Gap))
            Text(
                "What it will not claim",
                style = label.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = Warning,
                modifier = Modifier.semantics { heading() },
            )
            MethodParagraph("Temperature", Method.WillNot.TEMPERATURE)
            MethodParagraph("Rainfall", Method.WillNot.RAINFALL_AMOUNT)
            MethodParagraph("Which day", Method.WillNot.WHICH_DAY)
            MethodParagraph("Accuracy", Method.WillNot.BEAT_THE_MODEL)
            MethodParagraph("Location", Method.WillNot.LOCATION)

            Spacer(Modifier.height(Gap))
            Text(
                "Close",
                style = label.copy(fontSize = 14.sp),
                color = Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .padding(vertical = 14.dp)
                    .clickableNoRipple(onDismiss),
            )
        }
    }
}

@Composable
private fun MethodParagraph(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading, style = label.copy(fontSize = 14.sp), color = TextPrimary)
        Text(body, style = label.copy(fontSize = 12.sp), color = TextMuted)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}

@Composable
private fun KeyValue(key: String, value: String, caveat: String? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(key, style = label.copy(fontSize = 13.sp), color = TextSecondary)
            caveat?.let {
                Text(it, style = label.copy(fontSize = 11.sp), color = Warning)
            }
        }
        Text(
            value,
            style = label.copy(fontSize = 15.sp),
            // The whole row is one phrase for a screen reader rather than
            // two unrelated items announced in sequence.
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

internal fun ageLabel(hours: Float): String = when {
    // roundToInt, not toInt. Truncation reported 19.8 minutes as "19
    // minutes", which is a small lie about how old a forecast is, and it
    // was caught by a test expecting 20. The distinction never matters
    // much; the inaccuracy never not mattering is not a good trade for
    // one function call.
    hours < 1f -> "${(hours * 60f).roundToInt()} minutes"
    hours < 2f -> "1 hour"
    else -> "${hours.roundToInt()} hours"
}

/** A sentence about the trace, built from the trace. */
internal fun traceDescription(values: List<Float>, scale: com.krafttools.barokraft.core.BaroScale): String {
    if (values.isEmpty()) return "No pressure readings yet."
    val first = values.first()
    val last = values.last()
    val change = last - first
    val span = values.max() - values.min()
    val direction = when {
        kotlin.math.abs(change) < 0.2f -> "essentially flat"
        change < 0 -> "falling"
        else -> "rising"
    }
    return "Pressure trace of ${values.size} readings, $direction by " +
        "${fmt1(kotlin.math.abs(change))} hectopascals across a range of " +
        "${fmt1(span)} hectopascals."
}
