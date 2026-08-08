# Deo Music

A local-first music player for Android, built with Kotlin, Jetpack Compose and
Media3. The goal is a player that handles a large library correctly and does not
quietly degrade the audio on its way to the DAC.

**Licence: GPL-3.0-or-later.** See [LICENSE](LICENSE).

## Status

Early. The playback engine, library browsing and queue work; most of the audio
and ecosystem work described in the roadmap is not built yet.

| Area | State |
|---|---|
| Playback engine (Media3 `MediaSessionService`) | Working; migrating from intent control to a bound `MediaController` |
| Library (MediaStore: songs, albums, playlists, folders, genres) | Working |
| Queue: reorder, play next, add, save as playlist | Working |
| Favourites and local play-history suggestions | Working |
| Synced lyrics via [LRCLIB](https://lrclib.net) with offline cache | Working |
| Web playback mode (WebView) | Working; moving to the `full` flavour only |
| Tag parsing beyond MediaStore, Room library index, FTS search | Not started |
| Gapless, crossfade, ReplayGain, parametric EQ | Placeholder implementations, not yet correct |
| Android Auto, Cast, Wear, scrobbling, widgets | Not started |
| Bit-perfect USB output, native DSP | Not started |

See [ANDROID_IMPLEMENTATION_PLAN.md](ANDROID_IMPLEMENTATION_PLAN.md) for the
phased roadmap.

## Building

Requires **JDK 21** and the Android SDK with **platform 37** installed.

```bash
./gradlew :app:assembleDebug
```

The Gradle wrapper pins the toolchain — Gradle 9.7, AGP 9.3.1, Kotlin 2.3.21.
Do not build with a system `gradle`.

Dependency versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml).

### Release build

Signing values are read from `local.properties` or the environment. See
[keystore.properties.example](keystore.properties.example).

```bash
./scripts/build_signed_release_android.sh
```

## Configuration

- `compileSdk 37`, `targetSdk 36`, `minSdk 26` (Android 8.0)
- `targetSdk 36` is what Google Play requires for uploads from 31 August 2026

## Privacy

No accounts, no analytics, no crash reporting, no Play Services. The only network
requests are lyrics lookups to LRCLIB, and whatever you browse in web playback
mode. See [PRIVACY.md](PRIVACY.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Note the null-test requirement for any
change to the audio signal path.

## Licence

Copyright (C) 2026 Deo Music contributors.

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

Third-party attribution is in [NOTICE](NOTICE).
