# JexPlayer — Agent Notes

Single-module Android app (`:app` only, `rootProject.name = "JexPlayer"`). Local music player: SAF folder picker -> scan -> Media3/ExoPlayer playback.

## Stack
- Kotlin 2.2.10, AGP 9.4.1, Compose BOM 2026.02.01, Material3, media3 1.11.1 (ExoPlayer + MediaSession), Coil 3.3.0, jAudioTagger 3.0.1.
- `namespace` / `applicationId`: `com.cloudjex.jexplayer`. `compileSdk` / `targetSdk = 37` (AGP 9 DSL: `compileSdk { version = release(37) }`), `minSdk = 24`, Java 11.
- `org.gradle.configuration-cache=true` and `kotlin.code.style=official` (`gradle.properties`).
- Repos locked to `google()` + `mavenCentral()`, `FAIL_ON_PROJECT_REPOS` (`settings.gradle.kts`).
- Versions come from `gradle/libs.versions.toml`; add deps there, not inline.

## Entrypoints / architecture
- `MainActivity.kt` — only Activity. Single Compose tree, two screens switched by a `showPlayer` boolean (`HomeScreen`/`WelcomeScreen` vs `PlayerScreen`). No Navigation library.
- `viewmodel/MusicViewModel.kt` — the hub. Wires everything manually (no DI framework). Holds `StateFlow`s, connects to playback via async `MediaController` to `PlaybackService`, and calls `syncStateFromController` on connect because a `MediaController` does not replay existing state to late listeners.
- `playback/PlaybackService.kt` — foreground `MediaSessionService` (mediaPlayback). `CircularPlayer` wraps next/prev at list ends; `ReadAheadDataSource` is a custom `DataSource` for buffered reads.
- `data/repository/` — `SafMusicScanner` is the real scan path (SAF tree URIs from `OpenDocumentTree`). `ScanReusePolicy` reuses cache entries by file size + lastModified. `MusicRepository.kt` is an unused MediaStore leftover.
- `data/local/` — `LibraryCache` (atomic JSON file, tolerant read) uses hand-rolled `MiniJson`/`LibraryCacheCodec`; `AlbumArtStore` caches artwork; `FlacStreamReader` parses FLAC `STREAMINFO` because jAudioTagger's bitrate is wrong.
- `ui/player/FlacInfo.kt` is the jAudioTagger wrapper: it copies `content://` into a temp file first (jAudioTagger only accepts `File`).
- R8 rules live in `app/src/main/keepRules/rules.keep` (AGP 9 layout), not a classic `proguard-rules.pro`.

## Commands (run from repo root)
- Build debug APK: `./gradlew assembleDebug`
- Unit tests (all): `./gradlew :app:testDebugUnitTest`
- Single unit test class: `./gradlew :app:testDebugUnitTest --tests "com.cloudjex.jexplayer.ExampleUnitTest"`
- Instrumented tests (needs emulator/device): `./gradlew :app:connectedDebugAndroidTest`
- Lint: `./gradlew :app:lintDebug`

## Conventions
- Comments and user-facing strings are in Spanish (many UI strings are hardcoded in Kotlin, not in `strings.xml`). Match the surrounding language.
- New non-trivial logic under `data/` is expected to get a unit test (see `app/src/test/java/...`).

## Gotchas
- Use `./gradlew`, not system `gradle`. Prefer unit tests; instrumented tests need a running device/emulator.
- `local.properties` (`sdk.dir=/home/Cloudjex/Android/Sdk`) is machine-local, gitignored, auto-generated — do not edit or commit.
- No CI, no custom Gradle tasks, no flavors beyond `debug`/`release`.
- Manifest needs `READ_MEDIA_AUDIO` (+ legacy `READ_EXTERNAL_STORAGE` <=32), `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS`; keep them in sync with playback changes.
