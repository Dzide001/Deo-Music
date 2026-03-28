# Music Player (Android First)

Initial Android implementation scaffold using free/open-source components:

- Kotlin + Jetpack Compose
- Android Media3 (ExoPlayer + MediaSessionService)
- Web playback tab for browser-like streaming mode

## Current Status

- App shell with bottom navigation (`Library`, `Web`)
- `PlaybackService` scaffold using Media3
- Basic WebView playback screen
- Build system configured for Android Studio

## Open in Android Studio

1. Open this folder as a project.
2. Let Gradle sync complete.
3. Run on Android phone/tablet emulator (API 26+).

## Next Steps

- Implement local library scan via MediaStore
- Wire `Library` screen to play selected tracks through `PlaybackService`
- Add queue persistence and resume
- Add provider contracts and resolver module
