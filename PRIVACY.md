# Privacy Policy — Deo Music

_Last updated: 8 August 2026_

Deo Music is an offline-first music player. The short version: it does not have
accounts, does not have analytics, and does not send your library anywhere.

## What the app stores

Everything below stays in the app's private storage on your device. None of it is
transmitted to us, because there is no server to transmit it to.

- Playback state: current track, position, queue, shuffle and repeat mode, volume
- Favourites
- Play and skip counts, used only to generate local suggestions
- Settings: theme, crossfade, ReplayGain, equaliser bands, web home URL
- A cache of previously fetched lyrics

Uninstalling the app removes all of it.

## What the app reads

- **Your audio files and their metadata**, via the Android MediaStore, in order to
  show and play your library. This requires the `READ_MEDIA_AUDIO` permission
  (`READ_EXTERNAL_STORAGE` on Android 12 and below). Files are read only; nothing
  is uploaded.

## Network requests

Deo Music makes network requests in exactly these cases:

1. **Lyrics lookup (LRCLIB).** When you open the lyrics panel, the track title,
   artist, album and duration are sent to `lrclib.net` to find matching lyrics.
   LRCLIB requires no account and no API key. Results are cached locally so the
   same track is not requested twice. If you never open the lyrics panel, this
   request never happens.

2. **Web playback mode** (available only in the `full` build, not the F-Droid
   build). This mode embeds a web browser view. While you use it, the sites you
   visit receive requests from your device the same way any browser would, and
   are subject to their own privacy policies — not this one.

There is no telemetry, no crash reporting service, no advertising SDK, and no
Google Play Services dependency in the `foss` build.

## Permissions and why

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | Find and play your music |
| `POST_NOTIFICATIONS` | Show the playback notification and lock-screen controls |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing when the app is not on screen |
| `WAKE_LOCK` | Prevent the CPU sleeping mid-track |
| `INTERNET` | Lyrics lookup, and web playback mode in the `full` build |

## Children

Deo Music is not directed at children and collects no personal information from
anyone.

## Changes

Changes to this policy are committed to the project repository, so the full
history is public and auditable.

## Contact

Open an issue on the project's issue tracker.
