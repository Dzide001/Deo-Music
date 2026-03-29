# Deo Music

A feature-rich Android music player built with Kotlin and Jetpack Compose, supporting local library playback and web streaming.

## Features

- **Local Library Playback**: Browse and play music from your device (MediaStore)
- **Web Streaming**: Tab-based web playback for browser-like streaming mode
- **Queue Management**: Drag-to-reorder queue, persistent playback state
- **Settings**: Customize app behavior and playback preferences
- **Performance**: Optimized for smooth UI with lazy loading and background filtering
- **Signed Release**: Production-ready signed APK and AAB bundles

## Current Version

**v0.1.0** — Initial release with core playback features

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Playback Engine**: Android Media3 (ExoPlayer)
- **Persistence**: DataStore Preferences
- **Web Integration**: Android WebView with custom client (ad/tracker blocking)

## Installation

### From Source

1. Clone the repository
2. Open in Android Studio
3. Let Gradle sync complete
4. Run on Android device/emulator (API 26+)

### Pre-built Release

Download the signed APK or AAB from the [releases](releases/) folder:

- **APK**: Direct install on your device
- **AAB**: For publishing to Google Play Store

## Building a Release

### Prerequisites

A signing keystore must be set up locally. See [keystore.properties.example](keystore.properties.example) for configuration.

### Build Commands

```bash
# Build unsigned debug APK
./gradlew assembleDebug

# Build signed release APK + AAB
./scripts/build_signed_release_android.sh
```

Artifacts will be in `app/build/outputs/`.

## Project Structure

```text
app/
├── src/main/
│   ├── java/com/deox9/musicplayer/
│   │   ├── MainActivity.kt (main UI shell)
│   │   ├── library/ (local music library features)
│   │   ├── playback/ (playback state and services)
│   │   ├── settings/ (app preferences)
│   │   └── web/ (web playback features)
│   └── res/ (resources, icons, strings)
└── build.gradle.kts (app config)
```

## License

Proprietary — Deo Music
