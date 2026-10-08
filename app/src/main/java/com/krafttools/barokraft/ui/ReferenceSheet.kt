package com.krafttools.barokraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.barokraft.core.Method
import com.krafttools.barokraft.core.ReferenceStaleness
import com.krafttools.barokraft.core.SeaLevel
import com.krafttools.barokraft.core.fmt1
import com.krafttools.barokraft.core.seaLevelFromAltitude
import kotlin.math.abs
import kotlin.math.roundToInt
import com.kraft.ui.tokens.KraftTypeScale
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftRadius

/**
 * Setting the sea-level reference.
 *
 * ## Why this exists at all
 *
 * Because `calibrateFromAltitude` was implemented, unit-tested and called by
 * nothing. The screen said "No altitude — set a sea-level reference to get
 * one" and offered no way to do it, which is worse than not mentioning
 * altitude: the app gave an instruction it had no control for.
 *
 * ## Why altitude and not a QNH field
 *
 * Because a QNH is something a person reads off a chart or a flight plan
 * and most people have never seen one. Altitude is something they already
 * know: the building they are standing in, the floor of the car park. The
 * app does the barometric arithmetic from there.
 *
 * The QNH box is still offered, because for anyone who *does* have one it is
 * more accurate than an altitude estimate, and hiding it would make the app
 * worse for the people who know what they are doing.
 */
@Composable
fun ReferenceSheet(
    state: MeasureState,
    onCalibrateFromAltitude: (Float) -> Unit,
    onCalibrateFromQnh: (Float) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var altitudeText by remember { mutableStateOf("") }
    var qnhText by remember { mutableStateOf("") }
    var altitudeError by remember { mutableStateOf<String?>(null) }
    var qnhError by remember { mutableStateOf<String?>(null) }

    val station = state.currentHpa

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .clickableNoRipple(onDismiss),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = Pad, vertical = KraftSpacing.Spacing16),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
        ) {
            Text(
                "Sea-level reference",
                style = label.copy(fontSize = KraftTypeScale.Title3, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Altitude needs a reference: the sea-level pressure that corresponds " +
                    "to where you are. Without one there is no altitude, because the " +
                    "sensor cannot tell the difference between high ground and a low " +
                    "pressure system.",
                style = label.copy(fontSize = KraftTypeScale.Footnote, lineHeight = 19.sp), // @kraft-lint-ignore type.no-raw-sp — dense note leading, tighter than label 20
                color = TextSecondary,
            )

            if (station == null) {
                Text(
                    "Waiting for the first pressure reading.",
                    style = label.copy(fontSize = KraftTypeScale.Footnote),
                    color = Warning,
                )
            }

            // ── From altitude ──────────────────────────────────────────────
            Text(
                "IF YOU KNOW YOUR ALTITUDE",
                style = label.copy(fontSize = KraftTypeScale.Badge, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp), // @kraft-lint-ignore type.no-raw-sp — display micro-label tracking, off-scale by design
                color = TextMuted,
            )
            Text(
                "Meters above sea level — a known floor, a map contour, the number " +
                    "on a sign. This is the option most people can actually use.",
                style = label.copy(fontSize = KraftTypeScale.Caption1),
                color = TextSecondary,
            )
            NumberField(
                value = altitudeText,
                onValueChange = {
                    altitudeText = it
                    altitudeError = null
                },
                placeholder = "12",
                suffix = "m",
                fieldLabel = "Your altitude",
                error = altitudeError,
            )
            Action(
                text = "Set reference from altitude",
                enabled = station != null && altitudeText.isNotBlank(),
                onClick = {
                    val metres = altitudeText.trim().toFloatOrNull()
                    when {
                        metres == null ->
                            altitudeError = "That is not a number."
                        metres < -500f || metres > 9_000f ->
                            altitudeError = "That is outside the range this app can use."
                        seaLevelFromAltitude(station ?: 0f, metres) == null ->
                            altitudeError = "No reference can be derived from that."
                        else -> {
                            onCalibrateFromAltitude(metres)
                            onDismiss()
                        }
                    }
                },
            )

            // ── From QNH ──────────────────────────────────────────────────
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            Text(
                "IF YOU HAVE A QNH",
                style = label.copy(fontSize = KraftTypeScale.Badge, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp), // @kraft-lint-ignore type.no-raw-sp — display micro-label tracking, off-scale by design
                color = TextMuted,
            )
            Text(
                "The sea-level pressure from a chart or a flight plan. More accurate " +
                    "than an altitude estimate, if you have one.",
                style = label.copy(fontSize = KraftTypeScale.Caption1),
                color = TextSecondary,
            )
            NumberField(
                value = qnhText,
                onValueChange = {
                    qnhText = it
                    qnhError = null
                },
                placeholder = "1013.2",
                suffix = "hPa",
                fieldLabel = "QNH",
                error = qnhError,
            )
            Action(
                text = "Use this QNH",
                enabled = qnhText.isNotBlank(),
                onClick = {
                    val qnh = qnhText.trim().toFloatOrNull()
                    when {
                        qnh == null -> qnhError = "That is not a number."
                        qnh < 870f || qnh > 1085f ->
                            qnhError = "Sea-level pressure is always between 870 and 1085 hPa."
                        else -> {
                            onCalibrateFromQnh(qnh)
                            onDismiss()
                        }
                    }
                },
            )

            // ── What is set, and what it is worth ─────────────────────────
            state.reference?.let { ref ->
                Spacer(Modifier.height(KraftSpacing.Spacing4))
                ReferenceSummary(ref, state.staleness, station)
            }

            MethodParagraph(
                "How it is used",
                Method.ALTITUDE_DEPENDS_ON,
            )
        }

        if (state.reference != null) {
            Text(
                "Forget this reference",
                style = label,
                color = Critical,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Pad)
                    .clip(RoundedCornerShape(KraftRadius.Standard))
                    .background(Surface2)
                    .padding(vertical = KraftSpacing.Spacing12)
                    // Clickable before the description — see the note in
                    // AboutSheet.kt for why the other order is silent.
                    .clickableNoRipple {
                        onClear()
                        onDismiss()
                    }
                    .semantics { contentDescription = "Forget the sea-level reference" },
            )
        }

        Text(
            "Close",
            style = label.copy(fontSize = KraftTypeScale.Subheadline),
            color = Accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Pad, vertical = KraftSpacing.Spacing12)
                .clip(RoundedCornerShape(KraftRadius.Standard))
                .background(Surface2)
                .padding(vertical = KraftSpacing.Spacing12)
                .clickableNoRipple(onDismiss),
        )
    }
}

/**
 * What the stored reference is currently worth.
 *
 * ## Why the derived altitude is shown here
 *
 * Because setting a reference is a calculation, and a calculation whose
 * result is invisible cannot be checked by the person doing it. Showing the
 * altitude it produces, *before* they trust it, is the only way they can
 * notice it came out as 8,000 m.
 */
@Composable
private fun ReferenceSummary(
    reference: SeaLevel,
    staleness: ReferenceStaleness,
    station: Float?,
) {
    val derived = station?.let { altitudeFromPressureForDisplay(it, reference.hpa) }
    val hours = staleness

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Medium))
            .background(Surface1.copy(alpha = 0.7f))
            .padding(KraftSpacing.Spacing12),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Current reference", style = label.copy(fontSize = KraftTypeScale.Caption1), color = TextSecondary)
            Text("${fmt1(reference.hpa)} hPa", style = label.copy(fontSize = KraftTypeScale.Footnote))
        }
        if (derived != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gives an altitude of", style = label.copy(fontSize = KraftTypeScale.Caption1), color = TextSecondary)
                Text(
                    "${derived.roundToInt()} m",
                    style = label.copy(fontSize = KraftTypeScale.Footnote, fontWeight = FontWeight.Medium),
                    color = if (abs(derived) > 4_000f) Warning else TextPrimary,
                )
            }
            if (abs(derived) > 4_000f) {
                Text(
                    "That is not a plausible altitude. The reference is probably wrong.",
                    style = label.copy(fontSize = KraftTypeScale.Caption2),
                    color = Warning,
                )
            }
        }
        Text(
            stalenessWord(hours),
            style = label.copy(fontSize = KraftTypeScale.Caption2),
            color = when (hours) {
                ReferenceStaleness.FRESH -> TextMuted
                ReferenceStaleness.AGING -> Warning
                ReferenceStaleness.STALE -> Critical
                ReferenceStaleness.UNSET -> TextMuted
            },
        )
    }
}

/** How long a reference stays good, in words rather than a number. */
internal fun stalenessWord(staleness: ReferenceStaleness): String = when (staleness) {
    ReferenceStaleness.FRESH ->
        "Just set. Usable for ${com.krafttools.barokraft.core.REFERENCE_FRESH_HOURS.toInt()} hours."
    ReferenceStaleness.AGING ->
        "Ageing. Sea-level pressure moves with the weather, so it will drift."
    ReferenceStaleness.STALE ->
        "Too old to trust. An altitude from this would be plausible and wrong."
    ReferenceStaleness.UNSET ->
        "No reference set, so no altitude is shown."
}

/**
 * Altitude for display only.
 *
 * Uses the same barometric relation the app's own arithmetic uses, so the
 * number here cannot disagree with the number the screen shows afterwards.
 */
private fun altitudeFromPressureForDisplay(stationHpa: Float, seaLevelHpa: Float): Float? =
    com.krafttools.barokraft.core.altitudeFromPressure(stationHpa, seaLevelHpa)

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suffix: String,
    fieldLabel: String,
    error: String?,
) {
    Column {
        TextField(
            value = value,
            onValueChange = { input ->
                // Digits and one separator only. Accepting an arbitrary
                // string would mean parsing error text the user can see
                // and cannot act on.
                if (input.length <= 8 && input.all { it.isDigit() || it == '.' || it == '-' }) {
                    onValueChange(input)
                }
            },
            placeholder = { Text(placeholder, style = label, color = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedIndicatorColor = Hairline,
                unfocusedIndicatorColor = Hairline,
                cursorColor = Accent,
            ),
            trailingIcon = {
                Text(
                    suffix,
                    style = label.copy(fontSize = KraftTypeScale.Footnote),
                    color = TextMuted,
                    modifier = Modifier.padding(end = KraftSpacing.Spacing12),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$fieldLabel in $suffix" },
        )
        if (error != null) {
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            Text(
                error,
                style = label.copy(fontSize = KraftTypeScale.Caption1),
                color = Critical,
                modifier = Modifier.clearAndSetSemantics { contentDescription = error },
            )
        }
    }
}

@Composable
private fun Action(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = label.copy(color = if (enabled) Accent else TextMuted),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(if (enabled) Surface2 else Surface1.copy(alpha = 0.5f))
            .padding(vertical = KraftSpacing.Spacing12)
            .clickableNoRipple(if (enabled) onClick else ({ /* disabled */ }))
            .semantics { contentDescription = text },
    )
}

@Composable
private fun MethodParagraph(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2)) {
        Text(heading, style = label.copy(fontSize = KraftTypeScale.Footnote, fontWeight = FontWeight.Medium))
        Text(body, style = label.copy(fontSize = KraftTypeScale.Caption2, lineHeight = 16.sp), color = TextMuted) // @kraft-lint-ignore type.no-raw-sp — dense note leading, tighter than label 20
    }
}

/** Kept so a Box import is not dropped if the layout changes. */
private val boxRef: @Composable () -> Unit = { Box(Modifier) }