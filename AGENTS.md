# JexPlayer — Agent Notes

Single-module Android app (`:app`, `rootProject.name = "JexPlayer"`). Local music player: SAF folder picker -> recursive scan -> Media3/ExoPlayer playback. No backend, DI framework, or Navigation library.

## Build / stack
- Kotlin 2.2.10, AGP 9.4.1, Gradle 9.6.0, Compose BOM 2026.02.01, media3 1.11.1, DataStore Preferences 1.2.1, Coil 3.3.0, jAudioTagger 3.0.1.
- `namespace` / `applicationId`: `com.cloudjex.jexplayer`. `compileSdk` / `targetSdk = 37` (AGP 9 DSL: `compileSdk { version = release(37) }`), `minSdk = 24`, Java 11.
- Versions live in `gradle/libs.versions.toml`; add dependencies there, not inline in `app/build.gradle.kts`.
- Repos are locked to `google()` + `mavenCentral()` with `FAIL_ON_PROJECT_REPOS` (`settings.gradle.kts`). Configuration cache is on.
- Release R8 is enabled through AGP 9's `optimization {}` block. Keep rules go in `app/src/main/keepRules/rules.keep`, not a classic `proguard-rules.pro`.
- The daemon JVM is pinned to 25 by the committed `gradle/gradle-daemon-jvm.properties` (foojay auto-provisioning); don't replace it with the system JDK.

## Entrypoints / architecture
- `MainActivity.kt` — only Activity. One Compose tree: `WelcomeScreen` when no folders, otherwise `HomeScreen` or `PlayerScreen`. `showPlayer: Boolean?` comes from `MusicViewModel`; `null` means the DataStore preference is still loading and the UI must stay on `ScreenLoading` (defaulting to `false` makes the player flash).
- `viewmodel/MusicViewModel.kt` — the hub; manually constructs every dependency. It connects asynchronously to `PlaybackService` via `MediaController`, queues pre-connection actions in `pendingAction`, and calls `syncStateFromController` because a `MediaController` does not replay existing session state to late listeners. UI talks only to this ViewModel.
- `data/repository/FolderRepository.kt` persists SAF tree URIs in SharedPreferences (`jexplayer_folders`). Persistable read permission is taken/checked separately; revoked folders are filtered out of scanning.
- `SafMusicScanner` is the real scan path (recursive SAF, max depth 20). `Song.id` is the document URI. Folder membership and cache visibility assume `song.id.startsWith("$treeUri/")`; changing the ID scheme breaks the cache, artwork, and playback queue.
- `data/local/LibraryCache.kt` stores the library as atomic JSON in `filesDir/library_cache.json` using the hand-rolled `MiniJson`/`LibraryCacheCodec`. `ScanReusePolicy` reuses entries only when size + lastModified are reliable and `albumArtScanned == true`; `AlbumArtStore` keys artwork by song + size + lastModified and is pruned after scans.
- `ScreenPreferenceStore` uses DataStore (`jexplayer_screen`) for the last screen; folders and library cache use SharedPreferences / JSON instead.
- `playback/PlaybackService.kt` is a foreground `MediaSessionService` (mediaPlayback). `CircularPlayer` wraps next/prev at list ends.
- Repeat gotcha: `RepeatMode.ALL` deliberately leaves ExoPlayer in `REPEAT_MODE_OFF` and advances manually in `onSongCompleted` (so `OFF` stops at the last track); only `ONE` maps to `REPEAT_MODE_ONE`. Don't "fix" ALL by enabling player repeat-all without checking end-of-track behavior.
- `ReadAheadDataSource` buffers only `.flac` URIs; other formats pass straight through. `ui/player/FlacInfo.kt` is the jAudioTagger wrapper for every supported format, not just FLAC: it copies `content://` to a cache file preserving the extension, prefers FLAC `STREAMINFO`, and looks up `LYRICS`/`UNSYNCEDLYRICS`/`LYRIC` tags (not only `FieldKey.LYRICS`).
- `ui/player/LyricsScreen.kt` is an overlay opened from the artwork, not a navigation destination. `data/repository/MusicRepository.kt` is an unused MediaStore leftover; don't wire new work through it.

## Commands (from repo root)
- Build debug APK: `./gradlew assembleDebug`
- All unit tests: `./gradlew :app:testDebugUnitTest`
- Single test class: `./gradlew :app:testDebugUnitTest --tests "com.cloudjex.jexplayer.data.local.LibraryCacheTest"`
- Instrumented tests (needs emulator/device): `./gradlew :app:connectedDebugAndroidTest`
- Lint: `./gradlew :app:lintDebug`

## Conventions / testing
- Comments and user-facing strings are Spanish; many UI strings are hardcoded in Kotlin, not `strings.xml`. Match the surrounding language.
- Unit tests are pure JVM (no Robolectric/Android runtime). Keep `data/` logic testable via injected `File`, `DataStore`, `InputStream`, etc.; anything needing `Context` belongs in instrumented tests.
- New non-trivial logic under `data/` is expected to get a unit test (see `app/src/test/java/...`).

## Gotchas
- Use `./gradlew`, not system `gradle`.
- `local.properties` is machine-local, gitignored, and auto-generated — do not edit or commit.
- No CI, no custom Gradle tasks, no flavors beyond `debug`/`release`.
- Manifest needs `READ_MEDIA_AUDIO`, legacy `READ_EXTERNAL_STORAGE` (<=32), `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS`; keep them in sync with playback changes.
- `desing/` (sic) contains committed design mockups; it is not part of the build.
