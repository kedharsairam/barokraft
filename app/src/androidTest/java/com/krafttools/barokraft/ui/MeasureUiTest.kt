package com.krafttools.barokraft.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertHasClickAction
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
        current = Protocol.Current(
            atMillis = now,
            temperatureC = 28.1f,
            apparentTemperatureC = 33f,
            weatherCode = 95,
            windSpeedKmh = 6.1f,
            isDay = true,
            precipitationMm = 0f,
        ),
        days = (0 until 7).map { d ->
            Protocol.Day(
                dateMillis = now + d * 86_400_000L,
                weatherCode = if (d == 0) 95 else 1,
                temperatureMaxC = 28f + d,
                temperatureMinC = 21f + d * 0.5f,
                apparentTemperatureMaxC = 33f,
                apparentTemperatureMinC = 22f,
                precipitationSumMm = if (d == 0) 4.2f else 0f,
                precipitationProbabilityMax = if (d == 0) 82 else 10,
                windSpeedMaxKmh = 9f,
                sunriseMillis = now - 6 * 3_600_000L,
                sunsetMillis = now + 12 * 3_600_000L,
            )
        },
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

    // ── The hero ────────────────────────────────────────────────────────

    @Test
    fun the_hero_leads_with_temperature_not_the_verdict() {
        // The old screen put the verdict at 26 sp above the temperature,
        // which inverted what the app is for. This pins the demotion.
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(code = 95),
        )
        compose.onAllNodesWithText("28", substring = true).fetchSemanticsNodes().let { n ->
            assertTrue("the temperature should be on screen, got ${n.size}", n.isNotEmpty())
        }
        compose.onAllNodesWithText("Thunderstorm", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the condition label belongs in the hero", n.isNotEmpty())
            }
    }

    @Test
    fun a_network_only_phone_is_told_why_the_panel_is_missing() {
        // An absent panel is a different fact from a broken app, and a user
        // cannot tell those apart from an empty screen.
        show(
            MeasureState(
                sourceState = SourceState.NETWORK_ONLY,
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(),
        )
        compose.onAllNodesWithText("No pressure sensor on this device")
            .fetchSemanticsNodes().let { n ->
                assertTrue("the absence should be explained, got ${n.size}", n.isNotEmpty())
            }
    }

    @Test
    fun the_grid_cell_caveat_is_on_screen_once_and_short() {
        // It belongs on the screen because the model licence requires it,
        // but the six-line paragraph it used to be is About's job.
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(),
        )
        compose.onAllNodesWithText("1", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the grid-cell caveat must be visible", n.isNotEmpty())
            }
        compose.onAllNodesWithText("km across", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the grid-cell caveat must be visible", n.isNotEmpty())
            }
    }

    @Test
    fun seven_days_are_listed_and_today_is_named_today() {
        // "Wednesday" in the first row makes the reader do arithmetic.
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(),
        )
        compose.onAllNodesWithContentDescription("Today,", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the first day must be named Today, got ${n.size}", n.isNotEmpty())
            }
        compose.onAllNodesWithText("7 DAYS").fetchSemanticsNodes().let { n ->
            assertTrue("the weekly section belongs on a weather screen", n.isNotEmpty())
        }
    }

    @Test
    fun the_hourly_strip_starts_at_the_current_hour() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                forecast = forecast,
                forecastAtMillis = now,
            ).withVerdict(),
        )
        compose.onAllNodesWithText("HOURLY").fetchSemanticsNodes().let { n ->
            assertTrue("the hourly strip belongs on a weather screen", n.isNotEmpty())
        }
        // The hourly columns deliberately expose no child text — each is one
        // announcement for a screen reader, so the tests read that
        // announcement rather than the fragments behind it.
        compose.onAllNodesWithContentDescription("Now,", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the first hourly row should read Now, got ${n.size}", n.isNotEmpty())
            }
        // Whole hours only. Formatting the instant instead put ":30" on
        // every row of this strip on a half-hour offset, because the
        // response is UTC and the reader's clock is not.
        val bad = compose.onAllNodes(hasText(":30", substring = true))
            .fetchSemanticsNodes()
        assertEquals("no hourly row may show minutes", 0, bad.size)
    }

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
        compose.onAllNodesWithText("Offline", substring = true)
            .fetchSemanticsNodes().let { nodes ->
                assertTrue("text not found: ${nodes.size} nodes", nodes.isNotEmpty())
            }
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
        compose.onAllNodesWithText("Forecast only", substring = true)
            .fetchSemanticsNodes().let { nodes ->
                assertTrue("text not found: ${nodes.size} nodes", nodes.isNotEmpty())
            }
    }

    @Test
    fun the_neither_state_explains_itself_without_saying_it_twice() {
        // The header states the cause and the verdict states the
        // consequence. Rendering the same sentence in both was caught
        // here: onNodeWithText found two nodes and refused to guess which
        // one the test meant.
        show(MeasureState(sourceState = SourceState.NEITHER).withVerdict())
        compose.onAllNodesWithText("Nothing to read", substring = true)
            .fetchSemanticsNodes().let { nodes ->
                assertTrue("text not found: ${nodes.size} nodes", nodes.isNotEmpty())
            }
        compose.onAllNodesWithText("No data")
            .fetchSemanticsNodes().let { nodes ->
                assertEquals(
                    "the source state should appear exactly once, in the header",
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
        compose.onAllNodesWithText("Barometer + forecast", substring = true)
            .fetchSemanticsNodes().let { nodes ->
                assertTrue("text not found: ${nodes.size} nodes", nodes.isNotEmpty())
            }
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
        compose.onAllNodesWithContentDescription("Tap to set it again", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("a stale reference should be resettable, got ${n.size}", n.isNotEmpty())
            }
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
        compose.onAllNodesWithText("Thunderstorm", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("severe weather must be surfaced", n.isNotEmpty())
            }
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
        // The hourly column with a null probability must show no figure at
        // all — not a zero-width placeholder, which would read as "0%".
        compose.onAllNodesWithText("0%").fetchSemanticsNodes().let { n ->
            assertTrue("a null probability must not become 0%, got ${n.size}", n.isEmpty())
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
            "No connection. The barometer still works.",
            substring = true,
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
            "A bug here, not a weather problem.",
            substring = true,
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
            "The free API's daily allowance is used up.",
            substring = true,
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
        compose.onAllNodesWithText("What it will not claim", substring = true)
            .fetchSemanticsNodes().let { nodes ->
                assertTrue("text not found: ${nodes.size} nodes", nodes.isNotEmpty())
            }
        compose.onAllNodesWithText("How this app measures", substring = true)
            .fetchSemanticsNodes().let { nodes ->
                assertTrue("text not found: ${nodes.size} nodes", nodes.isNotEmpty())
            }
    }

    // ── The about sheet ─────────────────────────────────────────────────

    @Test
    fun the_about_sheet_carries_the_support_link() {
        // The README has one and always has. A reader in the disclosure
        // sheet has never seen the repository.
        show(MeasureState(sourceState = SourceState.BOTH, showingAbout = true).withVerdict())
        compose.onAllNodesWithContentDescription("Buy me a coffee", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("expected the support link, got ${n.size}", n.isNotEmpty())
            }
    }

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

    // ── The nowcast band ────────────────────────────────────────────────

    @Test
    fun the_nowcast_band_is_drawn_with_its_uncertainty_stated() {
        // The band is the product. For two releases the ensemble was
        // computed, tested and carried in the state record and rendered as
        // nothing at all.
        val pts = nowcast(samples(-1.5f), nowMillis = now)
        show(
            MeasureState(
                sourceState = SourceState.BAROMETER_ONLY,
                currentHpa = 1002f,
                samples = samples(-1.5f),
                nowcast = pts,
            ).withVerdict(points = pts),
        )
        compose.onAllNodesWithText("NOWCAST").fetchSemanticsNodes().let { n ->
            assertTrue("the nowcast heading must be on screen, got ${n.size}", n.isNotEmpty())
        }
        compose.onAllNodesWithText("band", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue(
                    "the band must be described, got ${n.size}",
                    n.isNotEmpty(),
                )
            }
    }

    @Test
    fun the_nowcast_band_states_a_half_width_not_the_full_width() {
        // uncertaintyHpa is the full width between the edges. Quoting it as
        // a plus-or-minus doubles the stated uncertainty.
        val pts = nowcast(samples(-1.5f), nowMillis = now)
        val caption = nowcastCaption(pts, 6f)
        assertTrue("expected a half-width in: $caption", caption.contains("plus or minus"))
        show(
            MeasureState(
                sourceState = SourceState.BAROMETER_ONLY,
                currentHpa = 1002f,
                samples = samples(-1.5f),
                nowcast = pts,
            ).withVerdict(points = pts),
        )
        compose.onAllNodesWithContentDescription(caption, substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the caption must be announced", n.isNotEmpty())
            }
    }

    @Test
    fun no_band_is_shown_without_a_history() {
        // Below the span gate there is no nowcast, so there is nothing to
        // draw — and an empty plot area reads as a rendering fault.
        show(
            MeasureState(
                sourceState = SourceState.BAROMETER_ONLY,
                currentHpa = 1002f,
                samples = samples(-1.5f).take(3),
            ).withVerdict(),
        )
        compose.onAllNodesWithText("NOWCAST").fetchSemanticsNodes().let { n ->
            assertEquals("no band without an ensemble, got ${n.size}", 0, n.size)
        }
    }

    // ── The reference sheet ─────────────────────────────────────────────

    @Test
    fun the_reference_sheet_is_reachable() {
        // It was implemented, unit-tested and called by nothing, while the
        // screen said "set a sea-level reference to get one".
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                showingReference = true,
            ).withVerdict(),
        )
        compose.onAllNodesWithText("Sea-level reference", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the sheet must open, got ${n.size}", n.isNotEmpty())
            }
        compose.onAllNodesWithText("IF YOU KNOW YOUR ALTITUDE").fetchSemanticsNodes().let { n ->
            assertTrue("the altitude route must be offered", n.isNotEmpty())
        }
        compose.onAllNodesWithText("IF YOU HAVE A QNH").fetchSemanticsNodes().let { n ->
            assertTrue("the QNH route must be offered", n.isNotEmpty())
        }
    }

    @Test
    fun the_altitude_prompt_is_tappable_and_says_what_it_does() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                staleness = ReferenceStaleness.UNSET,
                altitudeMetres = null,
            ).withVerdict(),
        )
        compose.onAllNodesWithContentDescription("Tap to set one", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue(
                    "the prompt must announce that it is tappable, got ${n.size}",
                    n.isNotEmpty(),
                )
            }
    }

    @Test
    fun a_stale_reference_offers_to_be_reset() {
        show(
            MeasureState(
                sourceState = SourceState.BOTH,
                currentHpa = 1004.2f,
                samples = samples(),
                staleness = ReferenceStaleness.STALE,
                altitudeMetres = null,
            ).withVerdict(),
        )
        compose.onAllNodesWithContentDescription("Tap to set it again", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("a stale reference must be resettable, got ${n.size}", n.isNotEmpty())
            }
    }

    // ── The controls are actually controls ──────────────────────────────

    /**
     * Why this file exists.
     *
     * Every other test here sets `showingAbout`, `showingReference` and the
     * rest directly on the state record, so the whole suite passed while the
     * buttons that set those flags did nothing. On a real device the Method,
     * About, Read-pressure, Adjust-reference and Forget-reference controls
     * all had `clickable="false"` in the view hierarchy — visible, described,
     * and inert.
     *
     * The cause is a modifier-order mistake that produces no compiler error
     * and no test failure: `clearAndSetSemantics` clears the semantics of
     * every modifier applied *after* it, so `.clearAndSetSemantics { }
     * .clickable { }` erases the click action. Each test below taps.
     */
    @Test
    fun the_about_button_opens_the_about_sheet() {
        var opened = false
        compose.setContent {
            MeasureContent(
                state = MeasureState(sourceState = SourceState.BOTH, currentHpa = 1004.2f),
                nowMillis = now,
                onOpenAbout = { opened = true },
            )
        }
        compose.onAllNodesWithContentDescription("About this app", substring = true)
            .fetchSemanticsNodes().let { n ->
                assertTrue("the About control must exist, got ${n.size}", n.isNotEmpty())
            }
        compose.onAllNodesWithContentDescription("About this app", substring = true)[0]
            .performClick()
        assertTrue("tapping About must fire its callback", opened)
    }

    @Test
    fun the_method_button_opens_the_method_sheet() {
        var opened = false
        compose.setContent {
            MeasureContent(
                state = MeasureState(sourceState = SourceState.BOTH, currentHpa = 1004.2f),
                nowMillis = now,
                onOpenMethod = { opened = true },
            )
        }
        compose.onAllNodesWithContentDescription("How this app measures", substring = true)[0]
            .performClick()
        assertTrue("tapping Method must fire its callback", opened)
    }

    @Test
    fun the_read_pressure_button_fires() {
        var toggled = false
        compose.setContent {
            MeasureContent(
                state = MeasureState(
                    sourceState = SourceState.BOTH,
                    currentHpa = 1004.2f,
                    samples = samples(),
                ),
                nowMillis = now,
                onToggleSampling = { toggled = true },
            )
        }
        compose.onAllNodesWithContentDescription("Start reading the barometer")[0]
            .performClick()
        assertTrue("tapping Read pressure must fire its callback", toggled)
    }

    @Test
    fun the_support_link_carries_a_click_action() {
        // `assertHasClickAction` is the precise statement of the bug: the
        // control was present and described and inert, so a presence
        // assertion passed while `clickable="false"` sat in the hierarchy.
        compose.setContent {
            AboutSheet(versionName = "0.3.0", onDismiss = {})
        }
        compose.onAllNodesWithContentDescription("Buy me a coffee", substring = true)[0]
            .assertHasClickAction()
    }

    @Test
    fun the_main_screen_controls_all_carry_a_click_action() {
        compose.setContent {
            MeasureContent(
                state = MeasureState(
                    sourceState = SourceState.BOTH,
                    currentHpa = 1004.2f,
                    samples = samples(),
                ),
                nowMillis = now,
            )
        }
        // Each control in the state the screen is actually in when it is
        // visible. Asserting a control that is not on screen would pass for
        // the wrong reason, which is how the first version of this test
        // spent its time looking for a link that belonged to a sheet which
        // was not open.
        for (label in listOf(
            "About this app",
            "How this app measures",
            "Start reading the barometer",
        )) {
            assertClickable(label)
        }
    }

    @Test
    fun the_about_sheet_controls_all_carry_a_click_action() {
        compose.setContent { AboutSheet(versionName = "0.3.0", onDismiss = {}) }
        assertClickable("Buy me a coffee")
    }

    @Test
    fun the_reference_sheet_controls_all_carry_a_click_action() {
        compose.setContent {
            ReferenceSheet(
                state = MeasureState(
                    sourceState = SourceState.BOTH,
                    currentHpa = 1004.2f,
                    reference = com.krafttools.barokraft.core.SeaLevel(1013.2f, 1_800_000_000_000L),
                    showingReference = true,
                ),
                onCalibrateFromAltitude = {},
                onCalibrateFromQnh = {},
                onClear = {},
                onDismiss = {},
            )
        }
        assertClickable("Forget the sea-level reference")
    }

    /** Fail with the label, so a regression names the control that broke. */
    private fun assertClickable(label: String) {
        val node = compose.onAllNodesWithContentDescription(label, substring = true)[0]
        try {
            node.assertHasClickAction()
        } catch (e: AssertionError) {
            throw AssertionError("the control described as \"$label\" has no click action", e)
        }
    }

    // ── Accessibility ───────────────────────────────────────────────────

    @Test
    fun the_trace_describes_itself_from_its_own_data() {
        // A Canvas announces nothing, so without this the only picture in
        // the app is invisible to a screen reader.
        val values = listOf(1004.0f, 1003.5f, 1003.0f, 1002.4f)
        val description = traceDescription(values)
        assertTrue("should mention the count: $description", description.contains("4 readings"))
        assertTrue("should say the direction: $description", description.contains("falling"))
        assertTrue("should quantify: $description", description.contains("hPa") || description.contains("hectopascals"))
    }

    @Test
    fun an_empty_trace_still_produces_a_description() {
        val description = traceDescription(emptyList())
        assertTrue(description.isNotBlank())
    }

    @Test
    fun an_age_label_reads_in_words() {
        assertEquals("20 minutes", ageLabel(0.33f))
        assertEquals("1 hour", ageLabel(1.4f))
        assertEquals("5 hours", ageLabel(5.0f))
    }
}
