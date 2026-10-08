package com.krafttools.barokraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.barokraft.core.BaroTrend
import com.krafttools.barokraft.core.Method
import com.krafttools.barokraft.core.Policy
import com.krafttools.barokraft.core.ReferenceStaleness
import com.krafttools.barokraft.core.Sky
import com.krafttools.barokraft.core.SourceState
import com.krafttools.barokraft.core.Verdict
import com.krafttools.barokraft.core.VerdictTone
import com.krafttools.barokraft.core.Wmo
import com.krafttools.barokraft.core.baroScale
import com.krafttools.barokraft.core.classifyTendency
import com.krafttools.barokraft.core.dayLabel
import com.krafttools.barokraft.core.describeGlyph
import com.krafttools.barokraft.core.fmt1
import com.krafttools.barokraft.core.glyphFor
import com.krafttools.barokraft.core.hourLabel
import com.krafttools.barokraft.core.tendencyHpaPerHour
import com.krafttools.barokraft.net.Failure
import com.krafttools.barokraft.net.Protocol
import kotlin.math.roundToInt
import com.kraft.ui.tokens.KraftTypeScale
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftRadius
import com.krafttools.barokraft.ui.BaroMetrics

/**
 * The screen.
 *
 * ## The order of this file, and why
 *
 * Conditions, then hours, then days, then the barometer. That is the order
 * every weather app uses and it is not arbitrary: the reader is answering
 * "what is it like", then "what is it doing", then "what is coming", and
 * only then — if they care — "what does my phone think".
 *
 * An earlier version put this app's verdict at the top, at 26 sp, above
 * the temperature. That inverted what the app is for. The barometer is the
 * thing that makes BaroKraft different; it is not the thing someone opens
 * the app to see. It gets the most *considered* treatment, not the largest.
 *
 * ## What is unchanged
 *
 * Every honesty rule from before still holds, and they are the reason some
 * of this screen is quieter than a typical weather app:
 *
 *  - a `null` rain probability is never printed as 0%
 *  - a phone with no barometer gets no pressure figure at all
 *  - the grid-cell caveat is on screen, once, in one line
 *  - the two sources are never blended
 *
 * A weather app that lies confidently is worse than one that is quiet and
 * correct, and the polish here is entirely in legibility — never in
 * filling space with numbers the app cannot stand behind.
 */
@Composable
fun MeasureContent(
    state: MeasureState,
    nowMillis: Long,
    onToggleSampling: () -> Unit = {},
    onOpenPlacePicker: () -> Unit = {},
    onOpenMethod: () -> Unit = {},
    onDismissMethod: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onDismissAbout: () -> Unit = {},
    onOpenReference: () -> Unit = {},
    onDismissReference: () -> Unit = {},
    onCalibrateFromAltitude: (Float) -> Unit = {},
    onCalibrateFromQnh: (Float) -> Unit = {},
    onClearReference: () -> Unit = {},
    versionName: String = "0.1.0",
) {
    val forecast = state.forecast
    val today = forecast?.days?.firstOrNull()
    val code = forecast?.current?.weatherCode ?: today?.weatherCode
    val sunrise = today?.sunriseMillis
    val sunset = today?.sunsetMillis

    val base = Sky.paletteFor(code, nowMillis, sunrise, sunset)
    val sky = Sky.withTwilight(base, nowMillis, sunrise, sunset)

    // Always white on the sky, over a scrim.
    //
    // An earlier version computed the ink from the gradient's *bottom*
    // colour and got dark text on a dark sky. The reason it failed is that
    // the hero sits across the whole band, and picking one ink to satisfy
    // the lightest stop guarantees it fails on the darkest one. No single
    // ink is correct across a gradient; a scrim is.
    val onSky = Color.White
    val onPage = TextPrimary

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        SkyBackground(
            top = sky.top.toColor(),
            bottom = sky.bottom.toColor(),
            page = Ink,
            modifier = Modifier
                .fillMaxWidth()
                .height(BaroMetrics.SkyHeight),
        )

        // The contrast guarantee. Sits over the top of the sky, under the
        // hero text, and fades out before the cards begin.
        //
        // Without it, a light sky — snow, fog, a bright overcast noon — puts
        // white text on 0xD5E5EE, which is 1.6:1 and unreadable. Palettes
        // cannot all be made dark enough for white text without making a
        // clear noon look like dusk, so the text colour is fixed and the
        // background is adjusted instead.
        SkyScrim(
            modifier = Modifier
                .fillMaxWidth()
                .height(BaroMetrics.SkyScrimHeight),
        )

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Pad, vertical = KraftSpacing.Spacing8),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing16),
        ) {
            Header(state, onSky, onOpenPlacePicker)
            NowHero(state, forecast, onSky, onOpenPlacePicker)

            if (forecast != null) {
                HourlyStrip(forecast, nowMillis, onPage)
            }
            if (forecast != null && forecast.days.isNotEmpty()) {
                DayRows(forecast.days, nowMillis, onPage)
            }

            // One line, not the six it used to be. The full sentence is in
            // About, but the caveat itself has to be here: it is required
            // by the model licence, and a forecast presented without it
            // reads as a statement about your street.
            if (forecast != null) {
                Spacer(Modifier.height(KraftSpacing.Spacing2))
                Text(
                    "Forecasts describe a grid cell 1–25 km across, not this street.",
                    style = label.copy(fontSize = KraftTypeScale.Badge),
                    color = TextMuted.copy(alpha = 0.75f),
                )
            }

            // Gated on the same capability the rest of the screen uses. An
            // earlier version rendered this unconditionally, so a phone
            // with no pressure sensor showed a "–– hPa" panel — which is
            // worse than showing nothing, because a dash is a rendered
            // element claiming a measurement exists.
            if (state.capabilities.canShowPressure || state.capabilities.canShowNowcast) {
                InstrumentPanel(state, onPage, onOpenReference)
            } else {
                NoBarometerNote()
            }

            state.verdict?.let { VerdictStrip(it, onPage) }
            FailureNotice(state.failure, state)
            Controls(state, onToggleSampling, onOpenMethod, onOpenAbout)
            Spacer(Modifier.height(KraftSpacing.Spacing12))
        }

        if (state.showingMethod) MethodSheet(onDismissMethod)
        if (state.showingAbout) {
            AboutSheet(versionName = versionName, onDismiss = onDismissAbout)
        }

        if (state.showingReference) {
            ReferenceSheet(
                state = state,
                onCalibrateFromAltitude = onCalibrateFromAltitude,
                onCalibrateFromQnh = onCalibrateFromQnh,
                onClear = onClearReference,
                onDismiss = onDismissReference,
            )
        }
    }
}

/**
 * Ink or white for text sitting on a sky colour.
 *
 * The palettes are chosen by eye, so the contrast cannot be reasoned about
 * by eye alone — this computes it. WCAG AA is 4.5:1 for body text, and the
 * screen keeps a 3:1 floor for the large display numerals where the rule
 * permits it.
 */
private fun contrastOn(colour: Long): Color {
    val r = ((colour shr 16) and 0xFF) / 255f
    val g = ((colour shr 8) and 0xFF) / 255f
    val b = (colour and 0xFF) / 255f
    fun lum(c: Float) = if (c <= 0.03928f) c / 12.92f else
        Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    val luminance = 0.2126f * lum(r) + 0.7152f * lum(g) + 0.0722f * lum(b)
    val vsWhite = 1.05f / (luminance + 0.05f)
    val vsBlack = (luminance + 0.05f) / 0.05f
    return if (vsWhite >= vsBlack) Color.White else Ink
}

/**
 * The accent for a pressure trend.
 *
 * Blue for falling and amber for rising, which is a blue-versus-orange
 * distinction rather than a red-versus-green one — so it survives both
 * deuteranopia and protanopia. The arrow glyph and the word beside it carry
 * the meaning; this is reinforcement only.
 */
private fun trendColour(trend: BaroTrend): Color = when (trend) {
    BaroTrend.FALLING, BaroTrend.FALLING_FAST -> Falling
    BaroTrend.RISING, BaroTrend.RISING_FAST -> Rising
    BaroTrend.STEADY -> Steady
}

private fun Long.toColor(): Color = Color(
    ((this shr 16) and 0xFF).toInt(),
    ((this shr 8) and 0xFF).toInt(),
    (this and 0xFF).toInt(),
    0xFF,
)

/**
 * The legibility scrim over the top of the sky.
 *
 * Deliberately a gradient rather than a flat wash, so it is invisible
 * against a dark sky and merely deepens a light one. 45% at the very top is
 * enough to take a light snow sky from 1.6:1 to about 7:1 against white,
 * and it is gone well before the first card.
 */
@Composable
private fun SkyScrim(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.45f),
                0.55f to Color.Black.copy(alpha = 0.18f),
                1f to Color.Transparent,
            ),
            size = size,
        )
    }
}

@Composable
private fun Header(state: MeasureState, onSky: Color, onOpenPlacePicker: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "BaroKraft",
                style = label.copy(
                    fontSize = KraftTypeScale.Footnote,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp, // @kraft-lint-ignore type.no-raw-sp — brand mark tracking
                ),
                color = onSky.copy(alpha = 0.75f),
            )
            state.place?.let {
                Text(
                    it.displayName,
                    style = label.copy(fontSize = KraftTypeScale.Title3, fontWeight = FontWeight.SemiBold),
                    color = onSky,
                )
            } ?: Text(
                "Choose a city",
                style = label.copy(fontSize = KraftTypeScale.Title3, fontWeight = FontWeight.Medium),
                color = onSky.copy(alpha = 0.9f),
                modifier = Modifier
                    .clip(RoundedCornerShape(KraftRadius.Small))
                    .clickableNoRipple(onOpenPlacePicker)
                    .semantics { contentDescription = "No city chosen. Tap to choose one." },
            )
        }
        Text(
            sourceLine(state.sourceState),
            style = label.copy(fontSize = KraftTypeScale.Caption2),
            color = onSky.copy(alpha = 0.8f),
        )
    }
}

/**
 * The one-line source state.
 *
 * Shortened from the previous wording because it sits in the corner of the
 * hero now. The full sentence is in About.
 */
private fun sourceLine(state: SourceState): String = when (state) {
    SourceState.BOTH -> "Barometer + forecast"
    SourceState.BAROMETER_ONLY -> "Offline"
    SourceState.NETWORK_ONLY -> "Forecast only"
    SourceState.NEITHER -> "No data"
}

@Composable
private fun NowHero(
    state: MeasureState,
    forecast: Protocol.Forecast?,
    onSky: Color,
    onOpenCity: () -> Unit,
) {
    val current = forecast?.current
    val code = current?.weatherCode
    val conditionLabel = Wmo.label(code ?: -1) ?: "No conditions yet"

    Column(
        Modifier
            .fillMaxWidth()
            .semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The temperature is the largest thing on the screen. That is a
            // deliberate demotion of the verdict, which used to be bigger.
            // No city chosen yet: a prompt, not a placeholder.
            //
            // Two em dashes at 84 sp look like a broken screen, and this is
            // the very first thing a new user sees. A dash is also not an
            // honest absence — it is a rendered element occupying the place
            // where a number would be.
            if (current?.temperatureC != null) {
                Text(
                    text = current.temperatureC.roundToInt().toString(),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontSize = 84.sp, // @kraft-lint-ignore type.no-raw-sp — hero temperature, sized to the sky
                    lineHeight = 88.sp, // @kraft-lint-ignore type.no-raw-sp — hero leading
                    fontWeight = FontWeight.Thin,
                    color = onSky,
                )
                Spacer(Modifier.width(KraftSpacing.Spacing6))
                WeatherIcon(
                    glyph = glyphFor(code),
                    tint = onSky.copy(alpha = 0.95f),
                    size = KraftSpacing.Spacing48,
                    description = describeGlyph(code),
                    modifier = Modifier.padding(bottom = KraftSpacing.Spacing8),
                )
            } else {
                Text(
                    "No city yet",
                    style = label.copy(
                        fontSize = KraftTypeScale.LargeTitle,
                        fontWeight = FontWeight.Thin,
                    ),
                    color = onSky.copy(alpha = 0.9f),
                    modifier = Modifier.clickableNoRipple(onOpenCity),
                )
            }
        }

        if (current?.temperatureC != null) {
            Text(
                conditionLabel,
                style = label.copy(fontSize = KraftTypeScale.Title2, fontWeight = FontWeight.Medium),
                color = onSky,
            )
        } else {
            Text(
                "Tap to pick one, and this becomes a forecast.",
                style = label.copy(fontSize = KraftTypeScale.Subheadline),
                color = onSky.copy(alpha = 0.85f),
            )
        }

        val details = buildList {
            current?.apparentTemperatureC?.let {
                add("Feels like ${it.roundToInt()}°")
            }
            current?.windSpeedKmh?.let {
                add("Wind ${it.roundToInt()} km/h")
            }
            forecast?.days?.firstOrNull()?.let { d ->
                if (d.hasRange) add("H ${d.temperatureMaxC!!.roundToInt()}°  L ${d.temperatureMinC!!.roundToInt()}°")
            }
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("   ·   "),
                style = label,
                color = onSky.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun HourlyStrip(forecast: Protocol.Forecast, nowMillis: Long, onPage: Color) {
    // Twelve hours, starting at the current one. A longer strip scrolls, but
    // a weather app that opens on an hour that has already passed is showing
    // the reader something they cannot act on.
    val start = forecast.hourAt(nowMillis)
    val hours = if (start == null) emptyList() else {
        forecast.hours.filter { it.atMillis >= start.atMillis }.take(12)
    }
    if (hours.isEmpty()) return

    GlassCard {
        Text(
            "HOURLY",
            style = label.copy(fontSize = KraftTypeScale.Badge, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp), // @kraft-lint-ignore type.no-raw-sp — display micro-label tracking, off-scale by design
            color = TextMuted,
        )
        Spacer(Modifier.height(KraftSpacing.Spacing8))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
        ) {
            hours.forEach { hour ->
                val prob = hour.precipitationProbability
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(KraftSpacing.Spacing48)
                        // The whole column is one announcement, so a screen
                        // reader says "15, rain, 14 degrees, 80% chance"
                        // rather than four unrelated fragments.
                        .clearAndSetSemantics {
                            contentDescription = buildString {
                                append("${hourLabel(hour.atMillis, nowMillis)}, ")
                                append(Wmo.label(hour.weatherCode ?: -1) ?: "unknown")
                                hour.temperatureC?.let { append(", ${it.roundToInt()} degrees") }
                                prob?.let { append(", $it percent chance of rain") }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing6),
                ) {
                    Text(
                        hourLabel(hour.atMillis, nowMillis),
                        style = label.copy(fontSize = KraftTypeScale.Caption1),
                        color = TextSecondary,
                    )
                    WeatherIcon(
                        glyph = glyphFor(hour.weatherCode),
                        tint = onPage,
                        size = KraftSpacing.Spacing24,
                    )
                    Text(
                        hour.temperatureC?.let { "${it.roundToInt()}°" } ?: "–",
                        style = label.copy(fontSize = KraftTypeScale.Callout, fontWeight = FontWeight.Medium),
                        color = onPage,
                    )
                    // Only drawn when the API supplied a probability. A
                    // zero-width placeholder here would be the app claiming
                    // zero chance of rain when it simply does not know.
                    Box(Modifier.height(KraftSpacing.Spacing16), contentAlignment = Alignment.Center) {
                        if (prob != null && prob > 0) {
                            Text(
                                "$prob%",
                                style = label.copy(fontSize = KraftTypeScale.Badge),
                                color = if (prob >= 40) WaterBlue else TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRows(days: List<Protocol.Day>, nowMillis: Long, onPage: Color) {
    val shown = days.take(7)
    if (shown.isEmpty()) return

    // One shared scale across every row, so a 2° day is visibly short and a
    // 15° day visibly long. A per-row scale would make every day look the
    // same, which is the single most common way a forecast range bar lies.
    val mins = shown.mapNotNull { it.temperatureMinC }
    val maxs = shown.mapNotNull { it.temperatureMaxC }
    val weekMin = mins.minOrNull() ?: 0f
    val weekMax = maxs.maxOrNull() ?: 1f

    GlassCard {
        Text(
            "7 DAYS",
            style = label.copy(fontSize = KraftTypeScale.Badge, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp), // @kraft-lint-ignore type.no-raw-sp — display micro-label tracking, off-scale by design
            color = TextMuted,
        )
        Spacer(Modifier.height(KraftSpacing.Spacing6))
        shown.forEachIndexed { i, day ->
            DayRow(day, nowMillis, weekMin, weekMax, onPage)
            if (i != shown.lastIndex) {
                Spacer(Modifier.height(KraftSpacing.Spacing2))
                Box(Modifier.fillMaxWidth().height(KraftSpacing.BorderWidth).background(Hairline))
            }
        }
    }
}

@Composable
private fun DayRow(
    day: Protocol.Day,
    nowMillis: Long,
    weekMin: Float,
    weekMax: Float,
    onPage: Color,
) {
    val span = (weekMax - weekMin).coerceAtLeast(1f)
    val prob = day.precipitationProbabilityMax

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = KraftSpacing.Spacing8)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(dayLabel(day.dateMillis, nowMillis))
                    append(", ")
                    append(Wmo.label(day.weatherCode ?: -1) ?: "unknown")
                    if (day.hasRange) {
                        append(", high ${day.temperatureMaxC!!.roundToInt()} degrees")
                        append(", low ${day.temperatureMinC!!.roundToInt()} degrees")
                    }
                    prob?.let { append(", $it percent chance of rain") }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayLabel(day.dateMillis, nowMillis),
            style = label.copy(fontSize = KraftTypeScale.Subheadline),
            color = onPage,
            modifier = Modifier.width(BaroMetrics.DayLabelMaxWidth),
        )
        Spacer(Modifier.width(KraftSpacing.Spacing2))
        WeatherIcon(glyph = glyphFor(day.weatherCode), tint = onPage, size = KraftSpacing.Spacing20)
        Spacer(Modifier.width(KraftSpacing.Spacing8))
        if (prob != null && prob > 0) {
            Text(
                "$prob%",
                style = label.copy(fontSize = KraftTypeScale.Caption2),
                color = if (prob >= 40) WaterBlue else TextMuted,
                modifier = Modifier.width(KraftSpacing.Spacing32),
            )
        } else {
            Spacer(Modifier.width(KraftSpacing.Spacing32))
        }
        Text(
            day.temperatureMinC?.let { "${it.roundToInt()}°" } ?: "–",
            style = label.copy(fontSize = KraftTypeScale.Subheadline),
            color = TextMuted,
            modifier = Modifier.width(KraftSpacing.Spacing32),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(KraftSpacing.Spacing8))
        Box(Modifier.weight(1f).height(KraftSpacing.Spacing6).clip(RoundedCornerShape(KraftRadius.DragHandle)).background(Surface3)) {
            if (day.hasRange) {
                val startFrac = ((day.temperatureMinC!! - weekMin) / span).coerceIn(0f, 1f)
                val endFrac = ((day.temperatureMaxC!! - weekMin) / span).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(endFrac - startFrac)
                        .fillMaxSize()
                        .padding()
                        .clip(RoundedCornerShape(KraftRadius.DragHandle))
                        .background(rangeGradient(startFrac, endFrac)),
                )
            }
        }
        Spacer(Modifier.width(KraftSpacing.Spacing8))
        Text(
            day.temperatureMaxC?.let { "${it.roundToInt()}°" } ?: "–",
            style = label.copy(fontSize = KraftTypeScale.Subheadline, fontWeight = FontWeight.Medium),
            color = onPage,
            modifier = Modifier.width(KraftSpacing.Spacing32),
        )
    }
}

/** Cool at the week's low end, warm at its high end. */
@Composable
private fun rangeGradient(startFrac: Float, endFrac: Float) = androidx.compose.ui.graphics.Brush.horizontalGradient(
    0f to if (startFrac > 0.5f) Warm else Cool,
    1f to if (endFrac > 0.5f) Warm else Cool,
)

@Composable
private fun InstrumentPanel(
    state: MeasureState,
    onPage: Color,
    onOpenReference: () -> Unit,
) {
    val hpa = state.currentHpa
    val tendency = tendencyHpaPerHour(state.samples)
    val trend = tendency?.let { classifyTendency(it) }

    GlassCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "BAROMETER",
                    style = label.copy(fontSize = KraftTypeScale.Badge, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp), // @kraft-lint-ignore type.no-raw-sp — display micro-label tracking, off-scale by design
                    color = TextMuted,
                )
                Spacer(Modifier.height(KraftSpacing.Spacing4))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        hpa?.let { fmt1(it) } ?: "––",
                        style = label.copy(
                            fontSize = KraftTypeScale.Title1,
                            fontWeight = FontWeight.Light,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        ),
                        color = if (hpa != null) onPage else TextMuted,
                    )
                    Spacer(Modifier.width(KraftSpacing.Spacing8))
                    Text(
                        "hPa",
                        style = label.copy(fontSize = KraftTypeScale.Caption1),
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = KraftSpacing.Spacing6),
                    )
                }
            }
            if (trend != null && tendency != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trend.arrow, style = label.copy(fontSize = KraftTypeScale.Title2), color = trendColour(trend))
                    Spacer(Modifier.width(KraftSpacing.Spacing6))
                    Column {
                        Text(
                            trend.label,
                            style = label.copy(fontWeight = FontWeight.Medium),
                            color = trendColour(trend),
                        )
                        Text(
                            "${fmt1(kotlin.math.abs(tendency))} hPa/h",
                            style = label.copy(fontSize = KraftTypeScale.Caption2),
                            color = TextMuted,
                        )
                    }
                }
            }
        }

        // The nowcast band, which is the product. Drawn above the sample
        // trace because it is an estimate about the future and the trace is a
        // record of the past, and the reader should not have to work out
        // which is which.
        if (state.capabilities.canShowNowcast && state.nowcast.isNotEmpty() && hpa != null) {
            Spacer(Modifier.height(KraftSpacing.Spacing12))
            Text(
                "NOWCAST",
                style = label.copy(fontSize = KraftTypeScale.Badge, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp), // @kraft-lint-ignore type.no-raw-sp — display micro-label tracking, off-scale by design
                color = TextMuted,
            )
            Spacer(Modifier.height(KraftSpacing.Spacing6))
            NowcastBand(
                points = state.nowcast,
                currentHpa = hpa,
                horizonHours = com.krafttools.barokraft.core.HORIZON_HOURS,
                tint = Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BaroMetrics.NowcastMinHeight)
                    .clearAndSetSemantics {
                        contentDescription = nowcastCaption(
                            state.nowcast,
                            com.krafttools.barokraft.core.HORIZON_HOURS,
                        )
                    },
            )
            Spacer(Modifier.height(KraftSpacing.Spacing6))
            Text(
                nowcastCaption(state.nowcast, com.krafttools.barokraft.core.HORIZON_HOURS),
                style = label.copy(fontSize = KraftTypeScale.Badge, lineHeight = 14.sp), // @kraft-lint-ignore type.no-raw-sp — dense note leading, tighter than label 20
                color = TextMuted.copy(alpha = 0.85f),
            )
        }

        if (state.samples.size >= 4) {
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            PressureTrace(
                values = state.samples.map { it.hpa },
                scale = baroScale(state.samples.map { it.hpa }),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KraftSpacing.Spacing64)
                    .clearAndSetSemantics {
                        contentDescription = traceDescription(
                            values = state.samples.map { it.hpa },
                            spanMillis = state.samples.last().atMillis - state.samples.first().atMillis,
                        )
                    },
            )
            val span = state.samples.last().atMillis - state.samples.first().atMillis
            Spacer(Modifier.height(KraftSpacing.Spacing6))
            Text(
                if (span < 3_600_000L) {
                    "Last ${(span / 60_000L).coerceAtLeast(1)} minutes · " +
                        "${state.samples.size} readings"
                } else {
                    "Last ${span / 3_600_000L} hours · ${state.samples.size} readings"
                },
                style = label.copy(fontSize = KraftTypeScale.Badge),
                color = TextMuted.copy(alpha = 0.75f),
            )
        }

        if (state.capabilities.canShowAltitude && state.altitudeMetres != null) {
            KeyValue(
                "Altitude",
                "${fmt1(state.altitudeMetres!!)} m",
                caveat = if (state.staleness == ReferenceStaleness.AGING) "reference ageing" else null,
            )
            Spacer(Modifier.height(KraftSpacing.Spacing6))
            Text(
                "Adjust reference",
                style = label.copy(fontSize = KraftTypeScale.Caption1, color = Accent),
                modifier = Modifier
                    .clip(RoundedCornerShape(KraftRadius.Small))
                    .clickableNoRipple(onOpenReference)
                    .padding(vertical = KraftSpacing.Spacing4)
                    .semantics { contentDescription = "Adjust the sea-level reference" },
            )
            Spacer(Modifier.height(KraftSpacing.Spacing4))
        } else if (state.capabilities.canShowPressure) {
            // Tappable, because this used to be an instruction the app
            // offered no way to follow: "set a sea-level reference to get
            // one" with no control that set one.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KraftRadius.Small))
                    .clickableNoRipple(onOpenReference)
                    .semantics {
                        contentDescription = when (state.staleness) {
                            ReferenceStaleness.STALE ->
                                "The sea-level reference is too old. Tap to set it again."
                            else ->
                                "No altitude, because no sea-level reference is set. Tap to set one."
                        }
                    }
                    .padding(vertical = KraftSpacing.Spacing6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (state.staleness) {
                        ReferenceStaleness.STALE ->
                            "Reference too old — tap to set it again"
                        else -> "Set a sea-level reference for altitude"
                    },
                    style = label.copy(fontSize = KraftTypeScale.Caption1, color = Accent),
                )
            }
        }

        Spacer(Modifier.height(KraftSpacing.Spacing6))
        Text(
            Method.PRESSURE_SOURCE,
            style = label.copy(fontSize = KraftTypeScale.Badge, lineHeight = 14.sp), // @kraft-lint-ignore type.no-raw-sp — dense note leading, tighter than label 20
            color = TextMuted.copy(alpha = 0.8f),
        )
    }
}

/**
 * What a phone with no barometer shows instead of the instrument.
 *
 * A sentence rather than nothing, because "the app shows less on this
 * device" is a different fact from "the app is broken", and a user cannot
 * tell those apart from an empty screen.
 */
@Composable
private fun NoBarometerNote() {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(
                glyph = com.krafttools.barokraft.core.Glyph.CLOUD,
                tint = TextMuted,
                size = KraftSpacing.Spacing24,
                description = null,
            )
            Spacer(Modifier.width(KraftSpacing.Spacing8))
            Column {
                Text(
                    "No pressure sensor on this device",
                    style = label.copy(fontWeight = FontWeight.Medium),
                )
                Text(
                    // "Above", not "below": this card sits *under* the
                    // forecast, and a card pointing the wrong way is the
                    // kind of small wrongness that costs trust in the
                    // large ones.
                    "The forecast above is everything this app can tell you here.",
                    style = label.copy(fontSize = KraftTypeScale.Caption1),
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun VerdictStrip(verdict: Verdict, onPage: Color) {
    val accent = when (verdict.tone) {
        VerdictTone.BLOCKED -> Critical
        VerdictTone.WARNING -> Warning
        VerdictTone.CALIBRATION -> Warning
        VerdictTone.DIVERGENCE -> Accent
        VerdictTone.NOWCAST -> Accent
        VerdictTone.QUIET -> TextMuted
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(KraftSpacing.Spacing4)
                    .height(KraftSpacing.Spacing32)
                    .clip(RoundedCornerShape(KraftRadius.DragHandle))
                    .background(accent),
            )
            Spacer(Modifier.width(KraftSpacing.Spacing12))
            Column {
                Text(
                    verdict.headline,
                    style = label.copy(fontSize = KraftTypeScale.Callout, fontWeight = FontWeight.SemiBold),
                    color = onPage,
                    modifier = Modifier.semantics { heading() },
                )
                verdict.detail?.let {
                    Spacer(Modifier.height(KraftSpacing.Spacing2))
                    Text(
                        it,
                        style = label.copy(fontSize = KraftTypeScale.Caption1, lineHeight = 16.sp), // @kraft-lint-ignore type.no-raw-sp — dense note leading, tighter than label 20
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Hero))
            // Translucent rather than opaque, so the sky reads through
            // faintly. This is the whole reason the app looks like a weather
            // app and not a list of grey rectangles.
            .background(Surface1.copy(alpha = 0.72f))
            .border(KraftSpacing.BorderWidth, Hairline.copy(alpha = 0.6f), RoundedCornerShape(KraftRadius.Hero))
            .padding(KraftSpacing.Spacing16),
        content = content,
    )
}

@Composable
private fun FailureNotice(failure: Failure?, state: MeasureState) {
    if (failure == null || failure is Failure.NotNeeded) return
    val text = when (failure) {
        is Failure.Offline ->
            if (state.capabilities.canShowPressure) {
                "No connection. The barometer still works."
            } else {
                "No connection. Nothing can be fetched until there is one."
            }
        is Failure.RateLimited ->
            "The free API's daily allowance is used up. The barometer is unaffected."
        is Failure.Malformed ->
            "The forecast service sent something this app could not read. A bug here, not a weather problem."
        is Failure.Server ->
            "Forecast service unavailable (HTTP ${failure.statusCode})."
        is Failure.Unreachable -> "Could not reach the forecast service."
        is Failure.NoPlaceSelected -> "Choose a city to get a forecast."
        is Failure.NotNeeded -> ""
    }
    GlassCard {
        Text(text, style = label.copy(fontSize = KraftTypeScale.Footnote), color = Warning)
    }
}

@Composable
private fun Controls(
    state: MeasureState,
    onToggleSampling: () -> Unit,
    onOpenMethod: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        if (state.capabilities.canShowPressure) {
            Pill(
                text = if (state.sampling) "Reading" else "Read pressure",
                onClick = onToggleSampling,
                accent = state.sampling,
                description = if (state.sampling) "Stop reading the barometer" else "Start reading the barometer",
                modifier = Modifier.weight(1f),
            )
        }
        Pill("Method", onOpenMethod, accent = false,
            description = "How this app measures and what it cannot tell you",
            modifier = Modifier.weight(1f))
        Pill("About", onOpenAbout, accent = false,
            description = "About this app, its sources, and what it will not claim",
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Pill(
    text: String,
    onClick: () -> Unit,
    accent: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = label.copy(fontSize = KraftTypeScale.Footnote, color = if (accent) Accent else TextSecondary),
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(Surface1.copy(alpha = 0.72f))
            .border(KraftSpacing.BorderWidth, Hairline.copy(alpha = 0.6f), RoundedCornerShape(KraftRadius.Standard))
            .padding(vertical = KraftSpacing.Spacing12)
            .clickableNoRipple(onClick)
            .semantics { contentDescription = description },
    )
}