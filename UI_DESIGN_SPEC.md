# UI Design Specification (Integrated)

This document defines layout, navigation, components, and interaction behavior.

Primary target: Android phones/tablets now.
Secondary target: Desktop later with parity.

## 1. Overall Layout

The UI is divided into three persistent regions plus content:

1. Header (search, settings, context actions)
2. Main Content (mode/category dependent)
3. Mini Player (global playback strip)
4. Footer (mode switch)

## 2. Footer (Bottom Bar)

- Local/Device (left)
- Web (right)
- Active mode is highlighted.
- Footer is always visible.

## 3. Local/Device View

When Local/Device is active, show category tabs below header:

- Songs
- Albums
- Playlists
- Folders
- Genres
- Suggested
- Favourites

Tabs scroll horizontally on small screens.

### Shared Interactions

- Tap item: play immediately
- Long press: context menu
  - Play next
  - Add to queue
  - Add to playlist
  - View details
  - Delete (local only)
- Double tap (mobile): favourite

### Songs

- List with thumbnail, title, artist, album, duration
- Header sort/filter
- Header search (live filter)

### Albums

- Grid with artwork, name, artist
- Tap album: album detail
- Back returns to grid

### Playlists

- Playlist list with name, count, cover
- Tap playlist: detail
- Create playlist action in header
- Edit mode supports reorder/remove

### Folders

- Hierarchy/breadcrumb navigation
- Tap folder to drill down
- Tap file to play

### Genres

- Genre list
- Tap genre to filtered songs list

### Suggested

- Recommendations based on history/favourites
- Refresh action

### Favourites

- Starred tracks/albums
- Toggle favourite in place

## 4. Web View

### Global Header

- Search input
- Sort dropdown
- Instance settings action

### Results

- List: thumbnail, title, channel, duration
- Tap: play audio and queue
- Long press: Play next, Add to queue, View channel

## 5. Global Now Playing

### Mini Player (persistent)

- Album art thumbnail
- Title + artist
- Controls: Play/Pause, Next, Queue
- Tap strip area expands full Now Playing

### Expanded Now Playing

- Full content area presentation
- Top bar: minimize, title, more-actions
- Large artwork
- Metadata block
- Playback controls: shuffle, prev, play/pause, next, repeat
- Progress slider and time labels
- Action row: favourite, playlist, lyrics, queue
- Lyrics panel toggle
- EQ modal entry

### Behavior

- Play replacing queue: may auto-open expanded view (configurable)
- Add-to-queue: mini player updates only
- Expanded state may persist during session, resets on app restart/new queue

## 6. Additional Global UI

### Header

- Left: app name/logo
- Center: context-aware search
- Right: settings and sort/filter

### Settings Modal

- Library: folders, watch, scan
- YouTube/Web: enable toggle, instance URL, fallback provider
- Playback: crossfade, gapless, ReplayGain, EQ presets
- Appearance: theme/accent
- Accounts: rich presence toggle
- Handoff (future): server/sync

### Queue Panel

- Open from mini player or expanded view
- Upcoming tracks list
- Reorder/remove/clear
- Save as playlist

### Diagnostics

- Provider, instance, latency, cache, logs
- Export logs

## 7. Behavior Summary

- Local/Web switching preserves playback
- Search is context-aware
- Playback is global regardless of source
- Category tabs exist only in Local mode
- Now Playing is the central control point

## 8. Example Flow

1. Open app -> Local mode, Songs tab.
2. Switch to Albums -> open album -> play track.
3. Expanded Now Playing appears (per setting).
4. Favourite track, minimize to mini player.
5. Switch to Web, search/play result.
6. Mini player updates with new source metadata.

## 9. Implementation Mapping

This UI spec maps to the timeline in [ANDROID_IMPLEMENTATION_PLAN.md](ANDROID_IMPLEMENTATION_PLAN.md):

- Phase 1: Footer, Songs, mini player baseline
- Phase 2: Web search/results behavior
- Phase 3: Expanded Now Playing + queue panel + lyrics/EQ entry
- Phase 4: loading/empty/error polish and tablet refinement
- Phase 5: desktop parity using same UX contract
