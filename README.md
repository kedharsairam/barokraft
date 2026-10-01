# BaroKraft

An Android weather app that reads your phone's pressure sensor and a
trusted network forecast, and refuses to average them.

A barometer measures pressure. Pressure falls before weather arrives, and
the size of the fall says something about what is coming. That is a real
signal, available offline, from hardware you already own. It is also much
narrower than what people want from a weather app, and most apps that use
it quietly bolt a network forecast onto the same screen so the gap is not
visible.

BaroKraft shows both, separately, and tells you when they disagree.

## What it does

**Shows conditions, hours and days first, and the barometer second.** The
barometer is what makes this app different; it is not what someone opens the
app to see. It gets the most considered treatment on the screen, not the
largest.

**Reads the barometer offline.** Sampling every 10 minutes while the screen
is open. A pressure sensor is good to about 0.012 hPa and a day's weather
is 10–40 hPa, so a faster interval collects noise, costs battery, and
improves nothing readable.

**Forecasts over the network**, from Open-Meteo, which blends ECMWF, NOAA,
DWD, Météo-France and the UK Met Office models and picks the highest-
resolution one for your coordinates. No API key. No account.

**Never blends the two.** They answer different questions on different
timescales. A blend would be less accurate than either while looking as
confident as both. When they disagree, the app says they disagree — and
says which way.

**Audits its own calibration.** A sea-level reference is a *current value
that moves with the weather*, so a stored one goes stale and starts
producing a plausible wrong altitude. When a forecast is available, BaroKraft
compares the two and reports the disagreement. No offline-only app can do
this, because it has nothing to check itself against.

**Works on a phone with no barometer.** Every budget handset and most
tablets have no pressure sensor. That is ordinary hardware, not a broken
phone, so the app shows the forecast and says plainly that there is no
local reading — rather than displaying zeros or hiding the difference.

## Permissions

Two. That is the whole list of anything a user is ever asked for.

| Permission | Why |
| --- | --- |
| `INTERNET` | Fetching the forecast |
| `ACCESS_NETWORK_STATE` | Telling a dead network from a rate limit |

**No location.** You type a city and pick it from a list. There is no
"allow while using the app" dialog because there is no location permission
to ask for.

**No background service, no foreground service, no boot receiver.** The
barometer is read only while the screen is open. A background barometer
means a permanent notification, three more permissions and a standing
complaint, in exchange for watching the weather change in a pocket.

The barometer is declared `required="false"`, so the app installs on
devices without one.

The merged manifest also contains
`com.krafttools.barokraft.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which
AndroidX generates for every app. It is signature-level, app-private, and
never appears in a permission prompt. Worth saying here so the claim above
is not read as "the manifest has nothing else in it" — a claim that would
itself be false.

## The nowcast

The next six hours are estimated 64 times over. Pressure rate and
acceleration are each perturbed within limits, and the middle 80% of those
runs is reported as a band.

**The band is the product. The centre line is a convenience.**

A wide band means the pressure trace is not yet constraining the answer,
and the app says so rather than picking a number out of the middle anyway.
Below about 4 hPa of spread the band is treated as too wide to mean
anything.

The perturbation limits are measured, not chosen for looks:

| Term | Value |
| --- | --- |
| Velocity error | 0.08 hPa/hour |
| Acceleration error | 0.012 hPa/hour² |

<details>
<summary>Why an ensemble rather than a fitted line</summary>

A regression through the last few hours produces one number with no honest
error estimate. The temptation is to fit it and print the result, and the
result will be wrong in a way that looks like a measurement.

Perturbing the two quantities that actually drive the extrapolation —
the rate and its derivative — and running the ensemble 64 times produces an
honest spread. It also degrades gracefully: as the window widens the band
widens on its own, because the extrapolation genuinely gets less certain.
A fitted line has no such property; it gets confidently wrong.

</details>

## Building

Requires JDK 17. Everything else the build fetches.

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # 224 unit tests
./gradlew :app:assembleRelease      # release APK, debug-signed
```

The release APK is deliberately signed with the debug key. There is no
release keystore to leak, and GitHub releases do not need one.

## Tests

**224 unit tests and 26 instrumented tests, all passing.**

| Suite | Tests | What it holds |
| --- | --- | --- |
| `ProtocolTest` | 23 | Request construction, response pairing |
| `MeasureViewModelTest` | 26 | The whole state machine, and the factory that crashed |
| `MethodTest` | 22 | Every published sentence, tied to its arithmetic |
| `VerdictTest` | 21 | The six-branch precedence |
| `NowcastTest` | 20 | The ensemble |
| `JsonTest` | 20 | Absent versus zero, and Float precision |
| `AboutTest` | 17 | The About sheet cannot contradict the code |
| `BaroTest` | 17 | Tendency, altitude, the plot scale |
| `PolicyTest` | 15 | Which source may be shown at all |
| `SeaLevelTest` | 14 | Reference staleness and drift |
| `SkyTest` | 23 | Sky palettes, glyphs, and the hourly and day labels |
| `EdgeContractTest` | 6 | The live API's actual contract |

`EdgeContractTest` hits the real Open-Meteo endpoint. Every other test
parses a fixture, and a fixture cannot tell you the contract changed. It
asserts that the arrays are parallel, that `unixtime` really is epoch
seconds, that `pressure_msl` is present, and that a failure is JSON rather
than a proxy's HTML. It skips rather than fails without a route, so a build
on a plane is a green build.

<details>
<summary>Bugs these tests caught</summary>

**Timestamps were 32 seconds wrong.** The parser read every number as a
`Float`. A `Float` holds integers exactly only to 2^24, and epoch seconds
are about 1.76e9 — so `1_759_264_800f.toLong()` is not 1759264800, it is
1759264768. Every hour label in the app was half a minute early, and
because the result still looked like a plausible time, nobody would have
noticed by eye.

**The drift audit could not fail.** It derived an altitude from the
reference, fed that altitude back through the sea-level reduction, and
compared the result with the model. That round trip returns the reference
exactly, so the drift was 0.0 at every possible reference value. A check
that cannot detect anything is worse than no check, because it reads as
reassurance. Caught by one test expecting agreement and one expecting a
real disagreement, both failing in opposite directions.

**The app crashed on launch with every test green.** `Cannot create an
instance of MeasureViewModel` — five constructor parameters, four
defaulted, and the default factory reflects for a single-argument
`(Application)` constructor that Kotlin does not generate. Nothing caught it
because no test constructed the ViewModel. The unit tests test pure
functions; the instrumented tests drive the screen as a function of a state
record. It was a green build and a crash on open, and only installing the
APK found it.

**The header was drawn under the status bar.** `targetSdk 37` enforces
edge-to-edge, so the window no longer insets itself. The city chip's bounds
were `y=40..166` on a device with a 63px status bar — drawn, and completely
untappable, because the system ate the tap. Found by dumping the real view
hierarchy on a Pixel.

**A missing rain probability rendered as 0%.** The API returns `null` for
every model without an ensemble. Treating that as zero produces "0% chance
of rain", which is a confident false statement about the weather rather
than a formatting bug. `JsonValue.asFloatOrNull` is now the only way out of
the JSON reader, so a null cannot become a zero by accident.

</details>

## Design notes

**The screen is a pure function of a state record.** `MeasureContent(state,
nowMillis)` reads nothing else. The sensor, the HTTP client, storage and the
clock are constructor parameters of the ViewModel, so all four source
states, a stale reference, a divergence, a rate limit and a malformed
response are constructible in a test in a millisecond.

**The trace is not zero-based.** A barometer chip is good to 0.012 hPa and
a day's weather is 10–40 hPa. Drawn against a zero axis, 1004.0 hPa is a
flat line pinned at the top of the panel. The scale is centred on the
median and sized to the variation.

**Measurements are drawn heavier than the line between them.** One is a
measurement and the other is an interpolation. Drawing them at the same
weight states both as the same kind of claim, which is the same overstatement
as printing a fitted forecast with no error band.

**Severity is carried by words, not colour.** Trend accents are a
blue-to-violet spread rather than red/green, because the conventional
pairing is invisible to roughly one man in twelve and a weather app that
signals severity with a colour half its users cannot see has failed at its
one job. The warning accent is amber, not red, so it does not pair against
an "improving" green.

**A hand-written JSON reader.** One endpoint, one response shape. The size
argument is real but secondary; the reason that matters is that a weather
API's `null` is *data*, and a general parser makes it easy to lose.

## Project layout

```
core/    Pure Kotlin. No Android. The arithmetic, fully unit-tested.
net/     JSON reading, request construction, the one HTTP client.
ui/      Compose. MeasureContent(state) and nothing else.
```

`core/` has no Android dependency, which is why the ensemble, the verdict
precedence and the reference policy can be tested without a device.

## License

MIT. No analytics, no advertising, no account, no Play Store.

Forecasts come from [Open-Meteo](https://open-meteo.com/), whose data is
licensed CC BY 4.0 and requires attribution. The attribution is in the app
itself, not only here, because the user reads the app.

If you enjoy BaroKraft, buy me a coffee:

<p align="center">
  <a href="https://buymeacoffee.com/kedhartech"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="182"></a>
</p>