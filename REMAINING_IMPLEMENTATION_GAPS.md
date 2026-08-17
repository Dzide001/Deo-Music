# Remaining work

This file replaces a much longer one that had become actively misleading. The old
version claimed album artwork in the expanded player was an unimplemented
placeholder, in a section directly below another one marking it complete; its
"Priority Fixes" said everything was done while its own body listed three broken
things, none of which were broken. It also cited
`app/src/main/java/com/deox9/musicplayer/library/LocalMusicRepository.kt` and line
numbers in a `MainActivity.kt` twice the size of the current one — paths from
before the code was split into modules.

Two lessons are baked into the format below. A status matrix listing every
implemented feature is the part that rots, because nobody revisits a row once it
says ✅ — so this file lists **only what is not done**, and anything absent from it
is either finished or was never planned. And a claim here is only worth what
verified it, so each entry says how it would be checked.

Last verified against the code on 2026-08-17.

## Not built yet

| Item | Notes |
| --- | --- |
| Web screen screenshots | The queue sidebar and the settings sheet's controls are covered now. The web screen is not: it is a WebView with an overlay, and Robolectric cannot render a WebView — only the ad-filter overlay could be pinned, and that lives in the app module's `full` flavour, which has no screenshot setup. |
| Seeded large library | A generated 30k-track library to test scanning and scrolling against. The real device library is ~150 tracks. |

## Waiting on the repository owner

| Item | What is needed |
| --- | --- |
| Renovate | `renovate.json5` is written and its rules check out against the real dependency catalogue, but nothing runs it. Install the Renovate GitHub App on `Dzide001/Deo-Music` (free for public repositories) and it picks the config up on its own. No pull requests appear until then. |

## Built but not verifiable here

These are implemented and unverified because verifying them needs hardware or
input this machine does not have. They are not known-broken; they are unproven.

| Item | What would prove it |
| --- | --- |
| Lyrics timing nudge | A track with embedded synced lyrics. The controls only appear for synced lyrics, and nothing in the test device's library has any, so the buttons have never been pressed on hardware. The shift itself is unit-tested and the controls have reference images. |
| Keyboard shortcuts | A physical keyboard. |
| Foldable hinge layout | A foldable, or a hinge-emulating device profile. |
| Resume after a phone call | A real incoming call. `resumeAfterInterruption` is wired through `onPlayWhenReadyChanged`, and audio-focus loss is distinguishable from a user pause, but the call path itself is untested. |

## Blocked on hardware the user is bringing

Android Auto, Cast, and Wear. Not skipped — waiting.

## Declined, and why

| Item | Reason |
| --- | --- |
| Scrobbling (Last.fm / ListenBrainz) | Declined by the user: nobody outside the device needs to know what they are listening to. The listening statistics screen is the on-device answer to the same want. |
| `.lrc` sidecar files | Reading a file beside the track needs broad storage permission on API 33+, which this app has no other use for. Embedded lyrics come from inside the audio file, which the app may already open. |
| `folder.jpg` album art | Same reason. Embedded artwork is read from the audio file instead. |
| Bluetooth codec awareness | Needs `BLUETOOTH_CONNECT`, a permission whose prompt is hard to justify for a display-only feature. Reversible if the user wants it. |
