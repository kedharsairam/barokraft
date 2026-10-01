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
            .background(Ink.copy(alpha = 0.94f))
            .clickableNoRipple(onDismiss),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = Pad, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "How this app measures",
                style = label.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            MethodParagraph("Pressure", Method.PRESSURE_SOURCE)
            MethodParagraph("Altitude", Method.ALTITUDE_DEPENDS_ON)
            MethodParagraph("Tendency", Method.TENDENCY_DEFINITION)
            MethodParagraph("Nowcast", Method.NOWCAST_DEFINITION)
            MethodParagraph("The forecast", Method.FORECAST_DEFINITION)
            MethodParagraph("Both sources", Method.TWO_SOURCES)

            Spacer(Modifier.height(6.dp))
            Text(
                "What it will not claim",
                style = label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
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
            style = label.copy(fontSize = 15.sp),
            color = Accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Pad, vertical = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .padding(vertical = 13.dp)
                .clickableNoRipple(onDismiss),
        )
    }
}

@Composable
private fun MethodParagraph(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading, style = label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
        Text(body, style = label.copy(fontSize = 12.sp, lineHeight = 17.sp), color = TextSecondary)
    }
}
