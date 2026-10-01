# BaroKraft

An Android weather app that reads your phone's pressure sensor and a
trusted network forecast, and refuses to average them.

Pressure falls before weather arrives, and the size of the fall says
something about what is coming. That signal is real, available offline, and
much narrower than what people want from a weather app — so most apps that
use it bolt a forecast onto the same screen and hope the seam is not
noticed.

BaroKraft shows both, separately, and tells you when they disagree.

## What it does

- **Conditions, hours and days first**, the barometer second. It gets the
  most considered treatment on the screen, not the largest.
- **Never blends the two sources.** They answer different questions on
  different timescales, so an average would be less accurate than either
  while looking as confident as both.
- **Reports its own nowcast as a band**, not a number. The band is the
  product.
- **Audits its own calibration** against the forecast. No offline-only app
  can, because it has nothing to check itself against.
- **Works on a phone with no barometer** — verified on a Realme RMX3998,
  which has none.

## Permissions

Two, and nothing else a user is ever asked for.

| Permission | Why |
| --- | --- |
| `INTERNET` | Fetching the forecast |
| `ACCESS_NETWORK_STATE` | Telling a dead network from a rate limit |

No location — you type a city. No background or foreground service: the
barometer is read only while the screen is open. The manifest also carries
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which AndroidX generates for
every app; it is app-private and never appears in a prompt.

## Build

Needs JDK 17.

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:assembleRelease      # release APK, debug-signed on purpose
```

## Download

Releases are on [GitHub](https://github.com/kedharsairam/barokraft/releases).
No Play Store, no ads, no analytics, no account.

<details>
<summary><b>How the nowcast works</b></summary>

The next six hours are estimated 64 times over. Pressure rate and
acceleration are each perturbed within **measured** limits — 0.08 hPa/hour
and 0.012 hPa/hour² — and the middle 80% is reported as a band.

It is an ensemble rather than a fitted line because a regression through the
last few hours gives one number with no honest error estimate, and the
temptation is to print it anyway. Perturbing the two quantities that drive
the extrapolation produces a spread that also widens on its own as the
window grows, because the extrapolation genuinely gets less certain.

Below about 4 hPa of spread the band is treated as too wide to mean
anything and the app says so rather than drawing a confident line.

</details>

<details>
<summary><b>Tests</b></summary>

**243 unit tests and 40 instrumented tests, all passing.** Six of the unit
tests hit the live Open-Meteo endpoint, because every other test parses a
fixture and a fixture cannot tell you the contract changed. They skip
without a route, so a build offline is a green build.

| Suite | Tests |
| --- | --- |
| `ProtocolTest` | 23 |
| `MeasureViewModelTest` | 26 |
| `MethodTest` | 22 |
| `VerdictTest` | 28 |
| `NowcastTest` | 20 |
| `JsonTest` | 20 |
| `AboutTest` | 19 |
| `SkyTest` | 23 |
| `UiTextTest` | 10 |
| `BaroTest` | 17 |
| `PolicyTest` | 15 |
| `SeaLevelTest` | 14 |
| `EdgeContractTest` | 6 |

</details>

<details>
<summary><b>Twelve defects, none caught by a test</b></summary>

Every one of these was invisible to a fully passing suite and visible within
seconds of opening the app on real hardware. They are listed because the
list is the argument for running the thing rather than only building it.

- **Timestamps 32 seconds wrong.** A `Float` holds integers exactly only to
  2^24, and epoch seconds are about 1.76e9.
- **The drift audit could not fail.** It round-tripped the reference through
  itself, so the drift was 0.0 at every possible value.
- **A crash on launch with every test green**, because no test constructed
  the ViewModel.
- **The headline number blank for ten minutes** — the sampling period was
  also passed as the batching window.
- **A sensor that told nobody.** Readings were stored and never displayed.
- **A 30-point trace from one second** of catch-up burst data.
- **":30" on every hourly row**, from a UTC timestamp rendered in a
  half-hour offset.
- **Dawn at 3 a.m.** Two "near an edge" checks ORed together.
- **A dash where a measurement should be**, on a phone with no barometer.
- **"Reading the barometer" on a phone with no barometer.**
- **976.3 hPa of rise in six hours**, from a two-minute history, stated with
  a ±1.6 hPa band.
- **Six buttons that rendered, were described to a screen reader, and did
  nothing when tapped.**

</details>

<details>
<summary><b>Design notes</b></summary>

**The screen is a pure function of a state record.** The sensor, the HTTP
client, storage and the clock are constructor parameters of the ViewModel,
so all four source states — a stale reference, a divergence, a rate limit, a
malformed response — are constructible in a test in a millisecond.

**The trace is not zero-based.** A barometer chip is good to 0.012 hPa and a
day's weather is 10–40 hPa. Drawn against a zero axis, 1004.0 hPa is a flat
line pinned at the top of the panel.

**Measurements are drawn heavier than the line between them.** One is a
measurement and the other is an interpolation. Drawing them at the same
weight states both as the same kind of claim.

**Severity is carried by words, not colour.** The accents are a
blue-to-violet spread rather than red/green, because the conventional
pairing is invisible to roughly one man in twelve, and the warning accent
is amber so it does not pair against an "improving" green.

**A hand-written JSON reader.** One endpoint, one response shape. The size
argument is real but secondary; the reason that matters is that a weather
API's `null` is *data*, and a general parser makes it easy to lose. A missing
rain probability rendered as 0% is a confident false statement about the
weather.

</details>

<details>
<summary><b>Layout and continuous integration</b></summary>

```
core/    Pure Kotlin. No Android. The arithmetic, fully unit-tested.
net/     JSON reading, request construction, the one HTTP client.
ui/      Compose. MeasureContent(state) and nothing else.
```

`core/` has no Android dependency, which is why the ensemble, the verdict
precedence and the reference policy are testable without a device.

Three CI jobs on every push: unit tests, APK assembly with a size assertion,
and instrumented tests on an emulator.

</details>

## Open source

MIT. Forecast data from [Open-Meteo](https://open-meteo.com/), licensed
CC BY 4.0, with the attribution in the app as well as here because the user
reads the app.

If you enjoy BaroKraft, buy me a coffee:

<p align="center">
  <a href="https://buymeacoffee.com/kedhartech"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="182"></a>
</p>

## License

[MIT](LICENSE)