package com.krafttools.barokraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.barokraft.core.ACCELERATION_ERROR_HPA_PER_HOUR2
import com.krafttools.barokraft.core.About
import com.krafttools.barokraft.core.fmtThreshold
import com.krafttools.barokraft.core.ENSEMBLE_SIZE
import com.krafttools.barokraft.core.HORIZON_HOURS
import com.krafttools.barokraft.core.Method
import com.krafttools.barokraft.core.MIN_CERTAINTY_HPA
import com.krafttools.barokraft.core.REFERENCE_DRIFT_STALE_HPA
import com.krafttools.barokraft.core.REFERENCE_USABLE_HOURS
import com.krafttools.barokraft.core.VELOCITY_ERROR_HPA_PER_HOUR

/**
 * The About sheet.
 *
 * ## Why this is its own file rather than a longer method sheet
 *
 * The method sheet answers "how does this work". This one answers "who
 * wrote it, what is it for, and what does it owe its sources" — and it
 * carries the attribution the forecast licence requires, which has to be
 * visible *in the app* because the user reads the app and never sees the
 * repository.
 *
 * ## Why the numbers come from constants
 *
 * Every figure in here is interpolated from the constant that defines the
 * arithmetic it describes. There is no place where a number is written
 * twice, so the ensemble can change size without the About sheet
 * becoming a lie — which is the failure this project has now hit three
 * times in documentation and once in code.
 */
@Composable
fun AboutSheet(
    versionName: String,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.96f))
            .clickableNoRipple(onDismiss),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Gap * 1.5f),
        ) {
            Text(
                About.NAME,
                style = label.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                About.TAGLINE,
                style = label.copy(fontSize = 14.sp),
                color = TextSecondary,
            )
            Text(
                "Version $versionName · MIT licence",
                style = label.copy(fontSize = 11.sp),
                color = TextMuted,
                modifier = Modifier.semantics {
                    contentDescription = "Version $versionName, MIT licence"
                },
            )

            Section("What it is for", About.PURPOSE)

            Section(
                "The nowcast",
                About.NOWCAST +
                    "\n\nThe perturbation limits are measured rather than " +
                    "guessed: " +
                    "${com.krafttools.barokraft.core.fmtThreshold(VELOCITY_ERROR_HPA_PER_HOUR)} hPa per hour on " +
                    "the rate and " +
                    "${com.krafttools.barokraft.core.fmtThreshold(ACCELERATION_ERROR_HPA_PER_HOUR2)} hPa per " +
                    "hour² on the acceleration. Below about " +
                    "${com.krafttools.barokraft.core.fmtThreshold(MIN_CERTAINTY_HPA)} hPa of spread the band is " +
                    "treated as too wide to mean anything and the app says " +
                    "so rather than drawing a confident line.",
            )

            Section("The two sources", About.TWO_SOURCES_NEVER_BLENDED)

            Section("When they disagree", About.DIVERGENCE_MEANS)

            Section(
                "The sea-level reference",
                About.REFERENCE_AUDIT +
                    "\n\nA reference is considered usable for " +
                    "${REFERENCE_USABLE_HOURS.toInt()} hours and is " +
                    "considered to have drifted beyond " +
                    "${com.krafttools.barokraft.core.fmtThreshold(REFERENCE_DRIFT_STALE_HPA)} hPa of " +
                    "disagreement with the model.",
            )

            Section("The forecast", About.FORECAST_SOURCE + "\n\n" + About.GRID_CELL)

            Section("What it does not do", About.NO_BACKGROUND)

            Section("Reading rate", About.SAMPLING)

            Section("If there is no barometer", About.NO_BAROMETER)

            Section("Limits", About.WILL_NOT_SUMMARY)

            Section("Open source", About.LICENCE)

            Spacer(Modifier.height(Gap))
        }

        Text(
            "Close",
            style = label.copy(fontSize = 15.sp),
            color = Accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Pad, vertical = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .padding(vertical = 14.dp)
                .semantics { contentDescription = "Close the about screen" }
                .clickableNoRipple(onDismiss),
        )
    }
}

@Composable
private fun Section(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            heading,
            style = label.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = TextPrimary,
            // Marked as a heading so a screen reader can jump between
            // sections rather than reading eleven paragraphs flat.
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = label.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = TextSecondary,
        )
    }
}

/** Exposed for the tests that assert the sheet's figures match the core. */
internal val ensembleSize: Int get() = ENSEMBLE_SIZE
internal val horizonHours: Float get() = HORIZON_HOURS