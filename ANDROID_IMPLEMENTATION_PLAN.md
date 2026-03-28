# Android-First Implementation Plan

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

### Phase 1 (Weeks 2-3) — Local Playback MVP
- Media3 `PlaybackService` + media session
- Local file scan (MediaStore first, custom folders second)
- Queue persistence and resume on app restart

### Phase 2 (Weeks 4-5) — Streaming Provider Layer
- Provider interface + resolver + retry/backoff
- Health scoring, timeout, cancellation
- Web playback mode UI with explicit user acknowledgment

### Phase 3 (Weeks 6-7) — UX and Audio Features
- Gapless/crossfade guardrails
- 10-band EQ
- Lyrics and diagnostics panel

### Phase 4 (Weeks 8-9) — Hardening
- Soak tests, memory/battery/network profiling
- Crash recovery and telemetry controls
- Beta release build

### Phase 5 (Weeks 10-12) — Desktop Start
- Extract shared core contracts from Android app
- Begin desktop shell using same resolver/queue contracts

## Immediate Task List (this week)

1. Build and run app shell.
2. Add navigation architecture and state container.
3. Add `Track`/`PlaybackState` models.
4. Implement MediaStore query for local tracks.
5. Connect selected track to `PlaybackService`.
