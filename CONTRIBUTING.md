# Contributing to Deo Music

## Licensing

Deo Music is **GPL-3.0-or-later**. By contributing you agree your work is
released under that licence.

Every source file carries an SPDX header on its first line:

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
```

Adding a dependency means checking its licence first. Apache-2.0, MIT, BSD and
LGPL are all fine to link into a GPLv3 project. Anything with a
non-commercial or field-of-use restriction is not.

**F-Droid constraint:** the `foss` product flavour must not depend on Google Play
Services, Firebase, or any proprietary library. Google-dependent features
(Cast, Android Auto) and the WebView playback mode belong in the `full` flavour.

## Building

Requires JDK 21 and the Android SDK with platform 37 installed.

```bash
./gradlew :app:assembleFossDebug
```

The Gradle wrapper pins the toolchain — do not run builds with a system `gradle`.

## Before opening a pull request

```bash
./gradlew detekt lint testDebugUnitTest
```

CI runs the same checks and will fail the PR if they do not pass locally.

## Commit style

Conventional commits: `feat:`, `fix:`, `perf:`, `refactor:`, `docs:`, `chore:`,
`test:`. The subject line is imperative and under 72 characters.

## Audio changes

Any change to the playback signal path — decoders, `AudioProcessor`s, the
equaliser, ReplayGain, crossfade — must be accompanied by a null test:

1. Play a known WAV with all DSP bypassed, captured digitally
2. Align sample-accurately against the source
3. Invert one, sum the two
4. Anything above the noise floor means the signal path is altering audio it
   should be passing through untouched

State the result in the pull request. This is routine practice in audio software
and it is the main thing keeping the player honest.

## Architecture

- Kotlin only, coroutines and `Flow` throughout — no RxJava, no Java sources
- Jetpack Compose with Material 3; no XML layouts
- MVVM: `ui` / `domain` / `data`, unidirectional flow, `StateFlow` from ViewModels
- Hilt for dependency injection
- Room for library and history data; DataStore for preferences only

Playback is driven through a bound `MediaController`. Do not add
`startService` intent actions to control the player — the `Player` interface
already covers transport and playlist commands, and the session forwards them.
