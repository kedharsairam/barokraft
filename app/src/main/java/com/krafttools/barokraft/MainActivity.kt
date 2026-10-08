package com.krafttools.barokraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.krafttools.barokraft.net.OpenMeteo
import com.krafttools.barokraft.ui.Accent
import com.krafttools.barokraft.ui.Hairline
import com.krafttools.barokraft.ui.Ink
import com.krafttools.barokraft.ui.MeasureContent
import com.krafttools.barokraft.ui.Surface1
import com.krafttools.barokraft.ui.Surface2
import com.krafttools.barokraft.ui.TextMuted
import com.krafttools.barokraft.ui.TextPrimary
import com.krafttools.barokraft.ui.TextSecondary
import com.krafttools.barokraft.ui.clickableNoRipple
import com.krafttools.barokraft.ui.label
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.barokraft.ui.BaroTheme
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftRadius
import com.krafttools.barokraft.ui.BaroMetrics

class MainActivity : ComponentActivity() {

    /**
     * Built through an explicit factory rather than `by viewModels()`.
     *
     * The default factory reflects for a single-argument
     * `(Application)` constructor, and a ViewModel with injected
     * dependencies has no such constructor — so the app crashed on
     * launch while every unit and instrumented test passed, because none
     * of them constructed the ViewModel. The crash was only visible by
     * installing the APK.
     */
    private val viewModel: MeasureViewModel by viewModels {
        MeasureViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = viewModel.state
            // The theme wrapper. Without it every MaterialTheme.colorScheme and typography
            // reference below resolves to Material defaults silently — which is how this
            // app shipped for months, inconsistent with itself, reporting nothing.
            BaroTheme {
                MeasureContent(
                    state = state,
                    nowMillis = System.currentTimeMillis(),
                    onToggleSampling = viewModel::toggleSampling,
                    onOpenPlacePicker = viewModel::openPlacePicker,
                    onOpenMethod = viewModel::openMethod,
                    onDismissMethod = viewModel::dismissMethod,
                    onOpenAbout = viewModel::openAbout,
                    onDismissAbout = viewModel::dismissAbout,
                    onOpenReference = viewModel::openReference,
                    onDismissReference = viewModel::dismissReference,
                    onCalibrateFromAltitude = { viewModel.calibrateFromAltitude(it) },
                    onCalibrateFromQnh = viewModel::calibrateFromQnh,
                    onClearReference = viewModel::clearReference,
                    versionName = BuildConfig.VERSION_NAME,
                )
                if (state.pickingPlace) {
                    PlacePicker(
                        query = state.searchQuery,
                        results = state.searchResults,
                        searching = state.searching,
                        empty = state.searchEmpty,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onChoose = viewModel::choosePlace,
                        onDismiss = viewModel::dismissPlacePicker,
                    )
                }
            }
        }
    }

    override fun onStop() {
        // Sampling stops with the screen. Not a lifecycle nicety: the
        // app has no foreground service by design, so this is the only
        // thing that stops the sensor, and leaving it registered would
        // mean a barometer draining a battery behind a closed app.
        viewModel.toggleSamplingIfRunning()
        super.onStop()
    }
}

/**
 * The city picker.
 *
 * No location permission, and therefore no "allow while using the app"
 * dialog. The user types a name and picks from a list, which is one extra
 * tap and no permission at all — and it is the reason this app can claim
 * [Method.WillNot.LOCATION] truthfully.
 */
@Composable
private fun PlacePicker(
    query: String,
    results: List<OpenMeteo.Place>,
    searching: Boolean,
    empty: Boolean,
    onQueryChange: (String) -> Unit,
    onChoose: (OpenMeteo.Place) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.95f))
            .clickableNoRipple(onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = KraftSpacing.Spacing20, topEnd = KraftSpacing.Spacing20))
                .background(Surface1)
                .safeDrawingPadding()
                .padding(KraftSpacing.Spacing20),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Text(
                "Choose a city",
                style = label.copy(fontSize = KraftTypeScale.Title3),
            )
            Text(
                "Typed, not read from the device. This app holds no location permission.",
                style = label.copy(fontSize = KraftTypeScale.Caption1),
                color = TextMuted,
            )
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                // A neutral prompt, not an example place. A named example in a
                // placeholder reads as a default the app has chosen for you, and this
                // app holds no location of any kind until the user picks one.
                placeholder = { Text("Type a city name", style = label, color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = Hairline,
                    unfocusedIndicatorColor = Hairline,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            if (searching) {
                Text("Searching…", style = label.copy(fontSize = KraftTypeScale.Footnote), color = TextSecondary)
            } else if (empty) {
                Text(
                    "No city matched \"$query\". Try a different spelling.",
                    style = label.copy(fontSize = KraftTypeScale.Footnote),
                    color = TextSecondary,
                )
            }
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .height(BaroMetrics.PlacePickerHeight),
                verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
            ) {
                items(results, key = { "${it.latitude},${it.longitude}" }) { place ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(KraftRadius.Small))
                            .background(Surface2)
                            .padding(KraftSpacing.Spacing16)
                            .clickableNoRipple { onChoose(place) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            place.displayName,
                            style = label,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Pick",
                            style = label.copy(fontSize = KraftTypeScale.Footnote),
                            color = Accent,
                        )
                    }
                }
            }
            Text(
                "Close",
                style = label,
                color = Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = KraftSpacing.Spacing12)
                    .clickableNoRipple(onDismiss),
            )
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)
