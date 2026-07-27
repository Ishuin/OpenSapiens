# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

**Scaffolded.** All six modules exist and follow the architecture below. Remaining WIP (also in README roadmap): whisper.cpp native build (`WhisperEngine` JNI stubs exist; `FakeEngine` backs dev), Drive auth/upload (`DriveClient` TODOs), Wear audio transfer + tile rendering, transcript detail/settings UI. License: Apache-2.0.

## What This Project Is

**open_sapien** — an open-source Android app (NeoSapien-style always-available voice capture) with these core requirements:

1. **On-device transcription, fully offline.** Either real-time streaming transcription, or record-then-transcribe. Audio files are deleted after transcription; only text is kept. No internet required for capture or transcription.
2. **Google Drive sync (optional).** User links a Google account; transcriptions sync opportunistically when online. Layout: an `open_sapien/` directory, one file per transcription, mirrored between local storage and Drive.
3. **Home/lock-screen widget** to start/stop recording without unlocking the phone or opening the app.
4. **Wear OS companion app + watch widget (tile/complication)** to record from the watch without touching the phone.
5. Open-source project — license, dependency choices, and privacy posture must stay compatible with public release (no proprietary SDKs where an OSS alternative exists; transcription must not leave the device).

## Intended Architecture (decisions to honor when scaffolding)

- **Language/stack:** Kotlin, Jetpack Compose for UI, Gradle (Kotlin DSL). Multi-module Gradle project:
  - `:app` — phone app (Compose UI, settings, transcript browser)
  - `:wear` — Wear OS app + tile
  - `:core:recording` — audio capture (foreground service, `MediaRecorder`/`AudioRecord`)
  - `:core:transcription` — on-device ASR engine abstraction
  - `:core:data` — Room DB + transcript file store
  - `:core:sync` — Drive sync (WorkManager-driven)
- **ASR engine:** on-device only. Primary candidate: whisper.cpp via JNI (OSS, offline). Keep behind an interface so engines (e.g., Vosk) are swappable. Model files downloaded on first run or bundled per ABI — decide and document when implemented.
- **Recording:** foreground service with microphone type; must survive screen-off and app-swipe. Widget (Glance) and Wear tile both talk to this service via intents, not to their own recorders on the phone side. Watch records locally when phone unreachable, transfers via Wearable Data Layer for transcription on phone.
- **Audio lifecycle:** audio is transient. Delete source audio immediately after successful transcription; never sync audio to Drive.
- **Drive sync:** Google Drive REST via `drive.file` scope only (least privilege), WorkManager with network constraint, one-way local→Drive by default. Sync must be fully optional — app is 100% functional offline with no Google account.
- **Storage:** transcripts as plain files (Markdown or txt, timestamp-named) under app storage in `open_sapien/`, with a Room index for search/metadata. Files-as-truth keeps Drive sync and user export trivial.

## Verbatim naming

- Local + Drive directory: `open_sapien`
- One file per transcription.

## Build Commands

Requires JDK 17 + Android SDK (compileSdk 35). Gradle wrapper not yet committed — generate once with `gradle wrapper --gradle-version 8.9` (or open in Android Studio).

```bash
./gradlew :app:assembleDebug          # phone APK
./gradlew :wear:assembleDebug         # watch APK
./gradlew test                        # all unit tests
./gradlew :core:data:testDebugUnitTest --tests "org.opensapien.core.data.SomeTest"  # single test
./gradlew lint
./gradlew :app:installDebug           # install to connected device
```

## Key Cross-Module Contracts

- `RecordingService` (`:core:recording`) is the only phone-side recorder. Toggle from anywhere: `RecordingService.toggle(context, source)`; observe `RecordingService.state` (StateFlow). Widget (`RecordWidget`) and Wear listener both go through it.
- `TranscriptionEngine` (`:core:transcription`) — swap engines here; `WhisperEngine` expects 16 kHz mono PCM16 (`PcmRecorder` emits exactly that).
- `TranscriptFileStore.DIR_NAME` / `DriveClient.FOLDER_NAME` are both `open_sapien` — verbatim, do not rename.
- Wear→phone audio path: `WearRecordingService.AUDIO_CHANNEL_PATH` = `/open_sapien/audio`, received by `WearAudioListenerService` in `:app`.

## Open-Source Considerations

- Choose a license before first public commit (whisper.cpp is MIT; Vosk is Apache-2.0 — keep compatibility).
- No analytics/telemetry by default.
- OAuth client IDs must not be committed; document contributor setup for Drive credentials in README when sync lands.
