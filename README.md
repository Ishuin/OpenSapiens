# open_sapien

Open-source, privacy-first voice capture for Android + Wear OS. Speak anywhere — the app transcribes **entirely on device**, keeps only the text, and (optionally) mirrors your transcripts to your own Google Drive.

## Principles

- **Offline by design.** Capture and transcription never require internet. Audio never leaves your device and is deleted the moment transcription succeeds.
- **Text is yours.** One plain markdown file per transcription in `open_sapien/` — trivially exportable, synced 1:1 to a Drive folder of the same name (optional, `drive.file` scope only).
- **Zero-friction capture.** Home/lock-screen widget and a Wear OS app + tile: record without unlocking the phone or taking it out of your pocket.

## Modules

| Module | Purpose |
|---|---|
| `:app` | Phone app (Compose), Glance widget, Wear Data Layer listener |
| `:wear` | Wear OS app, tile, watch-side recorder |
| `:core:recording` | Foreground mic service (`RecordingService`), PCM capture |
| `:core:transcription` | ASR abstraction; Vosk engine (default, offline) + one-time model downloader; whisper.cpp JNI stub for later |
| `:core:data` | Room index + files-as-truth transcript store |
| `:core:sync` | Drive one-way sync via WorkManager (WIP: auth) |

## Building

Requires **JDK 17** and the **Android SDK** (compileSdk 35). Open in Android Studio, or:

```bash
gradle wrapper --gradle-version 8.9   # once, to generate the wrapper
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug
./gradlew test
```

## Status / Roadmap

- [x] Project scaffold, data layer, recording service, widget, wear skeleton
- [x] Real on-device ASR: Vosk small-en model (~40 MB), one-time in-app download, offline thereafter
- [ ] Optional: whisper.cpp native build (git submodule + CMake) as higher-accuracy engine
- [ ] Google Drive auth + upload implementation
- [ ] Wear audio transfer (ChannelClient) + offline queue
- [ ] Wear tile one-tap record
- [ ] Transcript detail/edit/search UI, settings screen
- [ ] F-Droid release

## License

[Apache-2.0](LICENSE)
