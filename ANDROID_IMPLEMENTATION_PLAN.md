# Android-First Implementation Plan

> Status: This plan now incorporates the formal UI specification and defines when each UI block is implemented.

## Current Status Snapshot (2026-03-28)

### Completed

- Phase 0 core foundation is in place (Compose app shell + playback architecture baseline).
- Phase 1 core playback is working:
  - `PlaybackService` + Media3 session + queue persistence.
  - Local songs playback from MediaStore.
  - Mini-player controls and queue actions.
  - Expanded Now Playing screen with minimize/back handling and action row controls.
  - Queue sidebar overlay implemented.
  - Queue sidebar now supports richer actions: play-now on item, move-to-top/end, swap up/down, remove.
  - Queue sidebar now supports long-press drag-reorder gesture parity with live queue sync.
  - Context menu actions for track rows include **Play next** with true insert-next queue behavior.
  - Crossfade now uses transition fade workflow (end-of-track monitor + fade-out/next/fade-in).
  - Playback service now applies basic replay-gain normalization and 10-band EQ band mapping.
- UI spec integration started and applied in navigation:
  - Footer now matches spec mode switch: **Local/Device** and **Web** only.
  - Local category tabs moved to top area.
  - Top tabs now present: **Songs, Albums, Playlists, Folders, Genres, Suggested, Favourites** (initial scaffolds for non-core tabs).

### In Progress

- Phase 1 UI parity depth for Local tabs:
  - Data-backed implementation now in place for **Playlists, Folders, Genres, Favourites**.
  - **Suggested** now uses playback-context/favourites ranking with actionable track rows and refresh rotation.
  - Recommendation quality improved with persisted history signals (artist/track plays, skips) and explicit user feedback (like/hide).
- Header parity implementation started:
  - Top app header now includes app logo, context-aware search field, and settings/sort actions.
  - Search is wired for local filtering and basic Web query loading.
  - Sort is wired for **Songs, Albums, Playlists, Folders, Genres, Favourites**.
  - Settings sheet now persists preferences (web home URL, playback toggles, suggestions toggle).
  - Persisted theme preference now drives app Material theme (dark/light).
  - Persisted playback toggles are applied in `PlaybackService` (gapless behavior + transition fade crossfade).
  - Now Playing audio settings dialog can toggle crossfade/gapless preferences in-app.
  - Now Playing audio settings dialog includes replay-gain and 10-band EQ controls persisted to settings.
- Web playback now includes a hardened `WebViewClient` with ad/tracker filtering, request-block telemetry, and main-frame error retry/fallback handling.

### Not Started / Partial

- Full header contract is **partial** (core settings now applied; deeper playback DSP/theme polish still pending).
- Phase 2 web results/search flow is not yet implemented (web container exists).
- Phase 3 advanced Now Playing features are partial:
  - Favourite action is wired; lyrics dialog now shows embedded lyric credits + web search fallback; dedicated EQ surface with presets is available.

### Gate Progress

- Gate A (end Phase 1): **~99% complete**
  - ✅ Songs playable
  - ✅ Mini-player visible + interactive
  - ✅ No crash on basic playback flows
  - ✅ Local top category row fully present
    - ✅ Data-backed non-core local tabs (Playlists/Folders/Genres/Favourites)
    - ✅ Header sorting across local tabs
    - ✅ Context menu + double-tap favourite on track rows
    - ✅ Suggested recommendation ranking + interactions
    - ✅ Richer queue interactions in sidebar
    - ⚠️ Remaining: advanced playback polish (DSP safety/presets tuning + polish)

### Next 3 Execution Blocks

1. DSP polish pass (EQ presets tuning, limiter/safety behavior, replay-gain tuning).
2. Web provider abstraction pass (search provider strategy and resolver structure).
3. UX hardening pass (empty/loading/error consistency across screens).

## Why not design around ad blocking?

Building explicit "strong ad blocking for YouTube" into product scope creates high legal, distribution, and policy risk:

1. It can conflict with platform terms and content provider terms.
2. It can create Play Store review/rejection risk.
3. It shifts engineering effort from core playback quality to evasion behavior.
4. It increases maintenance volatility because breakage is frequent.

Safer scope: implement a **user-enabled web playback mode** and a **provider abstraction** while keeping compliant defaults.

## Phases and Timeline

### Phase 0 (Week 1) — Foundation

- Android project scaffold (Kotlin + Compose + Media3)
- Security baseline (network config, URL validation rules)
- CI basics, lint, unit test skeleton
- UI contract baseline:
  - global regions: Header, Main Content, Mini Player, Footer tabs
  - state matrix: idle, loading, playing, paused, error
  - navigation rules for Local/Device vs Web mode

### Phase 1 (Weeks 2-3) — Local Playback MVP

- Media3 `PlaybackService` + media session
- Local file scan (MediaStore first, custom folders second)
- Queue persistence and resume on app restart
- Local/Device UI foundation:
  - category tabs (Songs, Albums, Playlists, Folders, Genres, Suggested, Favourites)
  - Songs list first (tap to play, queue action)
  - mini-player always visible (idle + active states)

### Phase 2 (Weeks 4-5) — Streaming Provider Layer

- Provider interface + resolver + retry/backoff
- Health scoring, timeout, cancellation
- Web playback mode UI with explicit user acknowledgment
- Web UI:
  - context-sensitive header search in Web mode
  - results list interaction model (play now, add to queue)
  - preserve playback when switching Local ↔ Web

### Phase 3 (Weeks 6-7) — UX and Audio Features

- Gapless/crossfade guardrails
- 10-band EQ
- Lyrics and diagnostics panel
- Expanded Now Playing UI:
  - full-screen Now Playing sheet/page
  - progress slider + time labels + core controls
  - action row (favourite, playlist, lyrics, queue)
  - queue sidebar and reorder support

### Phase 4 (Weeks 8-9) — Hardening

- Soak tests, memory/battery/network profiling
- Crash recovery and telemetry controls
- Beta release build
- UX hardening:
  - empty/error/loading states for every major view
  - orientation and tablet layout validation
  - interaction reliability (tap/long-press/double-tap where applicable)

### Phase 5 (Weeks 10-12) — Desktop Start

- Extract shared core contracts from Android app
- Begin desktop shell using same resolver/queue contracts
- Desktop adaptation of the same UI contract:
  - three-region layout (Header/Main/Footer) with mini-player strip
  - category/navigation parity with Android
  - context menu parity for right-click interactions

## Integrated UI Definition (authoritative)

Primary UI spec source: [UI_DESIGN_SPEC.md](UI_DESIGN_SPEC.md)

### Functional Zones

1. Header (context-aware search + settings + sort/filter)
2. Main content (Local categories or Web view)
3. Mini-player (global playback controls)
4. Footer mode switch (Local/Device and Web)

### State Rules

- Idle: mini-player visible with placeholder text.
- Playing/Paused: mini-player shows metadata + controls.
- Local mode: category tabs visible.
- Web mode: category tabs hidden.
- Mode switch: playback must continue; only content context changes.

### UI Acceptance Gates by Phase

- Gate A (end Phase 1):
  - Songs tab playable.
  - Mini-player visible and interactive.
  - No crash on track tap and tab switch.
- Gate B (end Phase 2):
  - Web results playable.
  - Queue actions available from Local + Web.
  - Mode switch keeps playback state.
- Gate C (end Phase 3):
  - Expanded Now Playing complete.
  - Queue sidebar reorder/remove works.
  - Lyrics/EQ open reliably.
- Gate D (end Phase 4):
  - All views have consistent loading/empty/error treatment.
  - Tablet/rotation behavior validated.

## Immediate Task List (this week)

1. Build and run app shell.
2. Add navigation architecture and state container.
3. Add `Track`/`PlaybackState` models.
4. Implement MediaStore query for local tracks.
5. Connect selected track to `PlaybackService`.
6. Add dedicated Now Playing screen scaffold (no full feature set yet).
7. Add queue drawer/sheet scaffold and bind to service queue actions.
