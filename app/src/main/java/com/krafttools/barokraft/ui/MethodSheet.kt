package com.krafttools.barokraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.barokraft.core.Method
import com.kraft.ui.tokens.KraftTypeScale
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftRadius

/**
 * The method disclosure.
 *
 * ## Why this is separate from About
 *
 * Because they answer different questions. This one is "how does this
 * compute what it shows", and it is the sheet a curious user opens having
 * just seen a number move. About is "who wrote this and where does the data
 * come from", and it carries the licence attribution.
 *
 * Splitting them also keeps this sheet short enough to read. When they were
 * one sheet it was long enough that nobody read either half.
 */
@Composable
fun MethodSheet(onDismiss: () -> Unit) {
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
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = Pad, vertical = KraftSpacing.Spacing16),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
        ) {
            Text(
                "How this app measures",
                style = label.copy(fontSize = KraftTypeScale.Title3, fontWeight = FontWeight.SemiBold),
            )
            MethodParagraph("Pressure", Method.PRESSURE_SOURCE)
            MethodParagraph("Altitude", Method.ALTITUDE_DEPENDS_ON)
            MethodParagraph("Tendency", Method.TENDENCY_DEFINITION)
            MethodParagraph("Nowcast", Method.NOWCAST_DEFINITION)
            MethodParagraph("The forecast", Method.FORECAST_DEFINITION)
            MethodParagraph("Both sources", Method.TWO_SOURCES)

            Spacer(Modifier.height(KraftSpacing.Spacing6))
            Text(
                "What it will not claim",
                style = label.copy(fontSize = KraftTypeScale.Subheadline, fontWeight = FontWeight.SemiBold),
                color = Warning,
            )
            MethodParagraph("Temperature", Method.WillNot.TEMPERATURE)
            MethodParagraph("Rainfall", Method.WillNot.RAINFALL_AMOUNT)
            MethodParagraph("Which day", Method.WillNot.WHICH_DAY)
            MethodParagraph("Accuracy", Method.WillNot.BEAT_THE_MODEL)
            MethodParagraph("Location", Method.WillNot.LOCATION)
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

@Composable
private fun MethodParagraph(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2)) {
        Text(heading, style = label.copy(fontSize = KraftTypeScale.Footnote, fontWeight = FontWeight.Medium))
        Text(body, style = label.copy(fontSize = KraftTypeScale.Caption1, lineHeight = 17.sp), color = TextSecondary) // @kraft-lint-ignore type.no-raw-sp — dense note leading, tighter than label 20
    }
}
