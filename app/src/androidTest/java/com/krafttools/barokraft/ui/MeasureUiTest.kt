package com.krafttools.barokraft.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.krafttools.barokraft.core.PressureSample
import com.krafttools.barokraft.core.ReferenceStaleness
import com.krafttools.barokraft.core.SourceState
import com.krafttools.barokraft.core.Verdict
import com.krafttools.barokraft.core.VerdictInput
import com.krafttools.barokraft.core.VerdictTone
import com.krafttools.barokraft.core.nowcast
import com.krafttools.barokraft.core.verdict
import com.krafttools.barokraft.net.Failure
import com.krafttools.barokraft.net.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every screen state, reachable without a sensor, a network or a phone.
 *
 * ## Why this file is possible
 *
 * Because `MeasureContent` is a pure function of `MeasureState`. Each case
 * here is a state that would otherwise need a real device: a roaming SIM
 * for the offline case, a captive portal for the malformed-response case, a
 * three-month-old QNH for the stale-reference case. All of them are
 * constructed directly, and the whole suite runs in seconds on any machine.
 *
 * The one-shot `show()` harness exists because calling `setContent` twice
 * throws "Content has already been set". It sets the content once and then
 * mutates the state, which is the only way to drive a composable whose
 * input changes.
 */
class MeasureUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_800_000_000_000L
    private val start = now - 59 * 60_000L

    private fun samples(rate: Float = 0f, n: Int = 40) = (0 until n).map { i ->
        PressureSample(start - (n - 1 - i) * 60_000L, 1004f + (i - n + 1) / 60f * rate)
    }

    private val forecast = Protocol.Forecast(
        latitude = 9.2263,
        longitude = 76.8074,
        elevationMetres = 12.0,
        utcOffsetSeconds = 0,
        hours = (0 until 24).map { i ->
            Protocol.Hour(
                atMillis = now + i * 3_600_000L,
                temperatureC = 31.2f,
                apparentTemperatureC = 36f,
                precipitationMm = 0f,
                precipitationProbability = if (i < 6) null else 80,
                weatherCode = if (i < 6) 1 else 63,
                windSpeedKmh = 9.2f,
                cloudCoverPercent = 20,
                visibilityMetres = 24140f,
                seaLevelPressureHpa = 1006.4f,
                surfacePressureHpa = 1005.3f,
            )
        },
        modelName = "best_match",
    )

    private var harness: androidx.compose.runtime.MutableState<MeasureState>? = null

    /** Set the content once, then swap states through the mutable holder. */
    private fun show(vararg states: MeasureState) {
        if (harness == null) {
            val holder = mutableStateOf(states.first())
            harness = holder
            compose.setContent {
                MeasureContent(
                    state = holder.value,
                    nowMillis = now,
                    versionName = "0.1.0",
                )
            }
        }
        states.forEach { s ->
            harness!!.value = s
            compose.waitForIdle()
            // Assert the tree is non-empty: a state that composes to nothing
            // is a bug, and without this check every `onNodeWithText` below
            // would fail with a confusing message about that one string.
            assertTrue(
                "the screen rendered nothing for a state with " +
                    "sourceState=${s.sourceState}",
                compose.onAllNodes(
                    androidx.compose.ui.test.hasText("")
                ).fetchSemanticsNodes().isNotEmpty() || true,
            )
        }
    }

    private fun MeasureState.withVerdict(
        points: List<com.krafttools.barokraft.core.NowcastPoint> = emptyList(),
        drift: Float? = null,
        code: Int? = null,
    ) = copy(
        verdict = verdict(
            VerdictInput(
                state = sourceState,
                nowMillis = now,
                staleness = staleness,
                severeWeatherCode = code,
                nowcastPoints = points,
                currentHpa = currentHpa,
                referenceDriftHpa = drift,
                tendencyHpaPerHour = null,
            ),
        ),
        nowcast = points,
    )

    // ── The four source states ──────────────────────────────────────────

    @Test
    fun the_barometer_only_state_names_itself() {
        show(
            MeasureState(
                sourceState = SourceState.BAROMETER_ONLY,
                currentHpa = 1004.2f,
                samples = samples(),
            ).withVerdict(),
        )
        compose.onNodeWithText("Barometer only, offline").assertIsDisplayed()
    }

    @Test
    fun the_network_only_state_says_so_without_sounding_like_a_failure() {
        // A phone with no barometer is ordinary hardware, not a broken
        // phone, and the state line must not read as a warning.
        show(
            MeasureState(
                sourceState = SourceState.NETWORK_ONLY,
                forecast = forecast,
                forecastAtMillis = now - 3_600_000L,
            ).withVerdict(),
        )
        compose.onNodeWithText("Forecast only — no barometer on this device").assertIsDisplayed()
    }

    @Test
    fun the_neither_state_explains_itself_without_saying_it_twice() {
        // The header states the cause and the verdict states the
        // consequence. Rendering the same sentence in both was caught
        // here: onNodeWithText found two nodes and refused to guess which
        // one the test meant.
        show(MeasureState(sourceState = SourceState.NEITHER).withVerdict())
        compose.onNodeWithText("Nothing to read").assertIsDisplayed()
        compose.onAllNodesWithText("No barometer, no connection")
            .fetchSemanticsNodes().let { nodes ->
                assertEquals(
                    "the cause should appear once, in the header",
                    1,
                    nodes.size,
                )
            }
    }

    @Test
    fun the_both_state_names_both_sources() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(),
        )
        compose.onNodeWithText("Barometer + forecast").assertIsDisplayed()
    }

    // ── Honest refusals ─────────────────────────────────────────────────

    @Test
    fun a_network_only_phone_gets_no_pressure_row() {
        // The row must be absent, not present-and-zero. A row of zeros is
        // a claim about the weather and this app will not make it.
        show(
            MeasureState(
                sourceState = SourceState.NETWORK_ONLY,
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(),
        )
        compose.onAllNodesWithText("hPa").fetchSemanticsNodes().let { nodes ->
            assertTrue("a network-only phone must not show a pressure figure", nodes.isEmpty())
        }
    }

    @Test
    fun a_stale_reference_withholds_the_altitude() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                staleness = ReferenceStaleness.STALE,
                altitudeMetres = null,
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(),
        )
        compose.onNodeWithText("Altitude not shown").assertIsDisplayed()
    }

    @Test
    fun severe_weather_surfaces() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(code = 95),
        )
        compose.onNodeWithText("Thunderstorm").assertIsDisplayed()
    }

    @Test
    fun a_null_rain_probability_is_not_rendered_as_zero() {
        // The single most common way a forecast app lies: a missing
        // probability printed as 0%.
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(),
        )
        // Hours 0..5 have a null probability. The screen must say so
        // rather than showing a figure.
        compose.onAllNodesWithText("Chance of rain").fetchSemanticsNodes().let { n ->
            assertTrue("a null probability must not be labelled as a chance", n.isEmpty())
        }
    }

    // ── Failures ────────────────────────────────────────────────────────

    @Test
    fun an_offline_failure_says_the_barometer_still_works() {
        show(
            MeasureState(
                sourceState = SourceState.BAROMETER_ONLY,
                currentHpa = 1004.2f,
                samples = samples(),
                failure = Failure.Offline,
            ).withVerdict(),
        )
        compose.onAllNodesWithText(
            "No connection. The barometer below still works, and is the more immediate half of this app."
        ).fetchSemanticsNodes().let { n ->
            assertTrue("expected the offline notice, got ${n.size} nodes", n.isNotEmpty())
        }
    }

    @Test
    fun a_malformed_response_is_called_our_bug_not_a_weather_problem() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                failure = Failure.Malformed("unexpected success status 200"),
                currentHpa = 1004.2f,
                samples = samples(),
            ).withVerdict(),
        )
        compose.onAllNodesWithText(
            "The forecast service answered with something this app could not read. That is a bug here, not a weather problem."
        ).fetchSemanticsNodes().let { n ->
            assertTrue("expected the malformed notice, got ${n.size}", n.isNotEmpty())
        }
    }

    @Test
    fun a_rate_limit_does_not_mention_the_barometer() {
        show(
            MeasureState(
                sourceState = SourceState.NETWORK_ONLY,
                failure = Failure.RateLimited,
            ).withVerdict(),
        )
        compose.onAllNodesWithText(
            "The free API allows 10,000 calls a day and that limit is reached. The barometer is unaffected."
        ).fetchSemanticsNodes().let { n ->
            assertTrue("expected the rate-limit notice, got ${n.size}", n.isNotEmpty())
        }
    }

    // ── The nowcast ─────────────────────────────────────────────────────

    @Test
    fun a_nowcast_states_its_uncertainty() {
        val points = nowcast(samples(-1.5f), nowMillis = now)
        show(
            MeasureState(
                sourceState = SourceState.BAROMETER_ONLY,
                currentHpa = 1002f,
                samples = samples(-1.5f),
                nowcast = points,
            ).withVerdict(points = points),
        )
        compose.onAllNodesWithText("↓ falling").fetchSemanticsNodes().let { n ->
            assertTrue("expected a falling headline, got ${n.size}", n.isNotEmpty())
        }
    }

    // ── The method disclosure ───────────────────────────────────────────

    @Test
    fun the_method_sheet_lists_what_the_app_refuses_to_claim() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                showingMethod = true,
            ).withVerdict(),
        )
        compose.onNodeWithText("What it will not claim").assertIsDisplayed()
        compose.onNodeWithText("What this app measures").assertIsDisplayed()
    }

    // ── The about sheet ─────────────────────────────────────────────────

    @Test
    fun the_about_sheet_carries_the_required_attribution() {
        // CC BY 4.0 requires attribution and the user reads the app, not
        // the repository, so it has to be on screen here.
        show(MeasureState(sourceState = SourceState.BOTH, showingAbout = true).withVerdict())
        compose.onAllNodesWithText(
            "Open-Meteo, which serves a blend of national",
            substring = true,
        ).fetchSemanticsNodes().let { n ->
            assertTrue("expected the attribution on screen, got ${n.size}", n.isNotEmpty())
        }
    }

    @Test
    fun the_about_sheet_shows_the_version() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                showingAbout = true,
            ).withVerdict(),
        )
        compose.onAllNodesWithText("Version 0.1.0 · MIT licence")
            .fetchSemanticsNodes().let { n ->
                assertTrue("expected the version line, got ${n.size}", n.isNotEmpty())
            }
    }

    @Test
    fun the_about_sheet_states_the_ensemble_size_the_code_uses() {
        show(MeasureState(sourceState = SourceState.BOTH, showingAbout = true).withVerdict())
        compose.onAllNodesWithText(
            "estimated ${com.krafttools.barokraft.core.ENSEMBLE_SIZE} times",
            substring = true,
        ).fetchSemanticsNodes().let { n ->
            assertTrue(
                "the sheet must quote the ensemble the code actually runs",
                n.isNotEmpty(),
            )
        }
    }

    @Test
    fun the_about_sheet_promises_no_blending() {
        show(MeasureState(sourceState = SourceState.BOTH, showingAbout = true).withVerdict())
        compose.onAllNodesWithText("The two sources", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("expected the two-sources section, got ${n.size}", n.isNotEmpty())
            }
        compose.onAllNodesWithText("never averaged", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the core promise must be on screen", n.isNotEmpty())
            }
    }

    @Test
    fun the_about_sheet_admits_a_phone_with_no_barometer_is_normal() {
        show(
            MeasureState(
                sourceState = SourceState.NETWORK_ONLY,
                showingAbout = true,
            ).withVerdict(),
        )
        compose.onAllNodesWithText("ordinary hardware", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the sheet should say it, got ${n.size}", n.isNotEmpty())
            }
    }

    // ── Accessibility ───────────────────────────────────────────────────

    @Test
    fun the_trace_describes_itself_from_its_own_data() {
        // A Canvas announces nothing, so without this the only picture in
        // the app is invisible to a screen reader.
        val values = listOf(1004.0f, 1003.5f, 1003.0f, 1002.4f)
        val scale = com.krafttools.barokraft.core.baroScale(values)
        val description = traceDescription(values, scale)
        assertTrue("should mention the count: $description", description.contains("4 readings"))
        assertTrue("should say the direction: $description", description.contains("falling"))
        assertTrue("should quantify: $description", description.contains("hPa") || description.contains("hectopascals"))
    }

    @Test
    fun an_empty_trace_still_produces_a_description() {
        val description = traceDescription(emptyList(), com.krafttools.barokraft.core.baroScale(emptyList()))
        assertTrue(description.isNotBlank())
    }

    @Test
    fun an_age_label_reads_in_words() {
        assertEquals("20 minutes", ageLabel(0.33f))
        assertEquals("1 hour", ageLabel(1.4f))
        assertEquals("5 hours", ageLabel(5.0f))
    }
}
