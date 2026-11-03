# JexPlayer — Agent Notes

Android local music player: SAF folder picker -> recursive scan -> Media3/ExoPlayer. Two Gradle modules: `:app` (product code) and `:benchmark` (macrobenchmarks). No backend, DI framework, or Navigation library.

## Build / stack
- Kotlin 2.2.10, AGP 9.4.1, Gradle 9.6.0, Compose BOM 2026.02.01, media3 1.11.1, DataStore Preferences 1.2.1, Coil 3.3.0, jAudioTagger 3.0.1.
- `namespace` / `applicationId`: `com.cloudjex.jexplayer`; version `0.1.0-beta.3`. `compileSdk` / `targetSdk = 37` (AGP 9 DSL: `compileSdk { version = release(37) }`), `minSdk = 24`, Java 11.
- `:app` build types: `debug`, `release`, and `benchmark` (release-like, debug-signed, consumed by `:benchmark`). Not product flavors.
- Versions live in `gradle/libs.versions.toml`; add dependencies there, not inline in `app/build.gradle.kts`.
- Repos are locked to `google()` + `mavenCentral()` with `FAIL_ON_PROJECT_REPOS` (`settings.gradle.kts`). Configuration cache is on.
- Release R8 is enabled through AGP 9's `optimization {}` block. Keep rules go in `app/src/main/keepRules/rules.keep`, not a classic `proguard-rules.pro`.
- The daemon JVM is pinned to 25 by the committed `gradle/gradle-daemon-jvm.properties` (foojay auto-provisioning); don't replace it with the system JDK.

## Entrypoints / architecture
- `MainActivity.kt` — only Activity. One Compose tree: `WelcomeScreen` when no folders, otherwise `HomeScreen` (tabs Música/Listas/Carpetas) or `PlayerScreen`; `SettingsScreen` is an overlay, and `LyricsScreen` overlays the player. `showPlayer: Boolean?` comes from `MusicViewModel`; `null` means the DataStore preference is still loading and the UI must stay on `ScreenLoading` (defaulting to `false` makes the player flash). It also persists the library scroll position (debounced after scrolling stops and on `ON_STOP`) and re-follows the current song when the library remounts.
- `viewmodel/MusicViewModel.kt` — the hub; manually constructs every dependency. It connects asynchronously to `PlaybackService` via `MediaController`, queues pre-connection actions in `pendingAction`, and calls `syncStateFromController` because a `MediaController` does not replay existing session state to late listeners. UI talks only to this ViewModel. Library sorting runs on `Dispatchers.Default`.
- `viewmodel/PlaybackQueue.kt` — queue derived from `PlaybackContext` (`Library`, `Favorites`, or a custom library). The context is stored in each `MediaItem`'s metadata (`METADATA_CONTEXT_TYPE` / `METADATA_LIBRARY_ID`) so a surviving `PlaybackService` can be re-adopted after the Activity dies; a deleted custom library falls back to `Library`.
- `data/repository/FolderRepository.kt` persists SAF tree URIs in SharedPreferences (`jexplayer_folders`). Persistable read permission is taken/checked separately; revoked folders are filtered out of scanning.
- `SafMusicScanner` is the real scan path (recursive SAF, max depth 20) and also collects lyrics sidecars (`.lrc`, `.ttml`, `.vtt`, `.srt`) per folder. `Song.id` is the document URI; folder membership and cache visibility assume `song.id.startsWith("$treeUri/")`; changing the ID scheme breaks the cache, artwork, and playback queue.
- `data/local/LibraryCache.kt` stores the library as atomic JSON in `filesDir/library_cache.json` using the hand-rolled `MiniJson`/`LibraryCacheCodec`. `ScanReusePolicy` reuses entries only when size + lastModified are reliable and `albumArtScanned == true`; `AlbumArtStore` keys artwork by song + size + lastModified and is pruned after scans.
- Favorites and custom libraries: `FavoritesStore` (DataStore set `favorite_song_ids`) and `CustomLibrariesStore` (`filesDir/custom_libraries.json`). `CustomLibraryRules` validates names (80 chars, "Favoritos" reserved, no duplicates) and edits; IDs in `songIds` may be missing from the current library and are filtered for display/queue, not deleted.
- `LastSongStore` (SharedPreferences `jexplayer_last_song`, JSON) restores the mini player and position without starting playback. `restorePlaybackIfNeeded` reuses the controller queue only when it matches the active context; otherwise it sets the queue and seeks. `PlaybackRestorePolicy.kt` only holds `findMediaIdIndex`.
- Lyrics: embedded tags come from `FlacInfo` (`buscarLetrasEnTag`); sidecars are matched by audio filename (not title), with the same folder scoring higher and format priority TTML > LRC > VTT > SRT. `LyricsRepository` reads sidecars with BOM handling (≤4 MB) and parses through `LyricsParser` (`LrcParser`, `TtmlParser`, `SubtitleParser`). `BilingualLyrics` only derives translations for simple LRC. Per-song source choice and style settings persist in SharedPreferences `jexplayer_lyrics`.
- `data/update` checks GitHub releases (`Cloudjexp/JexPlayer`, needs `INTERNET`) automatically at most every 72 h (retry after 30 min on failure) and persists the dismissed version in DataStore `jexplayer_updates`.
- Appearance lives in DataStore `jexplayer_appearance`; `AppearanceTheme` / `AppearanceAccent` map storage keys and migrate legacy keys (`minimalist_gray`, `amoled_black` -> `DARK`).
- Other stores are independent: `ScreenPreferenceStore` (`jexplayer_screen`), `LibrarySortPreferenceStore` (`jexplayer_library_sort`), `LibraryScrollPositionStore` (`jexplayer_library_scroll`). Don't assume a single unified settings store.
- `playback/PlaybackService.kt` is a foreground `MediaSessionService` (mediaPlayback). `CircularPlayer` wraps next/prev at list ends.
- Repeat gotcha: `RepeatMode.ALL` deliberately leaves ExoPlayer in `REPEAT_MODE_OFF` and advances manually in `onSongCompleted` (so `OFF` stops at the last track); only `ONE` maps to `REPEAT_MODE_ONE`. Don't "fix" ALL by enabling player repeat-all without checking end-of-track behavior.
- `ReadAheadDataSource` buffers only `.flac` URIs; other formats pass straight through. `ui/player/FlacInfo.kt` is the jAudioTagger wrapper for every supported format, not just FLAC: it uses `MediaExtractor` for quick info, reads FLAC `STREAMINFO` directly from SAF, copies `content://` to a cache file preserving the extension, falls back to `Mp4TagReader` for MP4/M4A with a video track, and looks up `LYRICS`/`UNSYNCEDLYRICS`/`LYRIC` tags (not only `FieldKey.LYRICS`).
- `data/repository/MusicRepository.kt` is an unused MediaStore leftover; don't wire new work through it.

## Commands (from repo root)
- Build debug APK: `./gradlew assembleDebug`
- All unit tests: `./gradlew :app:testDebugUnitTest`
- Single test class: `./gradlew :app:testDebugUnitTest --tests "com.cloudjex.jexplayer.data.local.LibraryCacheTest"`
- Instrumented tests (needs emulator/device): `./gradlew :app:connectedDebugAndroidTest`
- Macrobenchmark scroll test (physical device with a scanned library): `./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cloudjex.jexplayer.benchmark.LibraryScrollBenchmark`
- Lint: `./gradlew :app:lintDebug`

## Conventions / testing
- Comments and user-facing strings are Spanish; many UI strings are hardcoded in Kotlin, not `strings.xml`. Match the surrounding language.
- Unit tests are pure JVM (no Robolectric/Android runtime). Keep `data/` and `viewmodel/` logic testable via injected `File`, `DataStore`, `InputStream`, etc.; anything needing `Context` belongs in instrumented tests.
- New non-trivial logic under `data/` or pure viewmodel rules is expected to get a unit test (lyrics parsers, sorting, custom-library rules, playback queue/restore all have one).
- Keep `:app` free of test-only dependencies used by `:benchmark`; benchmark deps belong in `benchmark/build.gradle.kts`.

## Gotchas
- Use `./gradlew`, not system `gradle`.
- `local.properties` is machine-local, gitignored, and auto-generated — do not edit or commit.
- No CI, no custom Gradle tasks.
- Manifest needs `READ_MEDIA_AUDIO`, legacy `READ_EXTERNAL_STORAGE` (<=32), `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, and `INTERNET` (update checker); keep them in sync with playback/update changes.
- The app is offline-first except the GitHub release check; don't add a backend or network dependency for core library/playback features.
- `desing/` (sic) contains committed design mockups; it is not part of the build.
