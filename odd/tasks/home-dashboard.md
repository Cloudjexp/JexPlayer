# Feature: Home Dashboard (Inicio)

Objective: new app-level home destination (Inicio/Buscar/Biblioteca) exposing greeting, recently played, quick access (Álbumes/Artistas/Favoritos/Historial), two statistics cards and recently added songs, without touching playback, scanning, cache format, sorting, artwork or permissions.
Branch: working tree (no commits authorized). Delivery: single working set, no PR.
Test runner: `./gradlew :app:testDebugUnitTest` (JUnit4, pure JVM).

## Specs

- S1 — Bottom navigation exposes exactly `Inicio`, `Buscar`, `Biblioteca`. Inicio opens the dashboard; Buscar opens the library with search activated; Biblioteca opens the library.
- S2 — Dashboard header: dynamic greeting (`Buenos días` / `Buenas tardes` / `Buenas noches` by local hour), title `Tu música`, subtitle `¿Qué quieres escuchar hoy?`, search button and settings button.
- S3 — Section `Escuchado recientemente`: horizontal carousel from real playback history, most recent first; empty state when there is no history; no fabricated items.
- S4 — Interaction contract MIXED: song -> play; album -> open details; artist -> open discography when available. Album/artist taps never start playback.
- S5 — Section `Explora tu música`: 2x2 grid with `Álbumes`, `Artistas`, `Favoritos`, `Historial`, each navigating to a real destination (no prefilled searches).
- S6 — Section `Estadísticas`: exactly two cards, total unique songs and total unique artists, from real library data; missing artist metadata handled consistently (single `Artista desconocido` bucket); no `0` shown while the library is still restoring.
- S7 — Section `Añadido recientemente`: compact vertical list (artwork, title, artist) ordered by `CachedSong.lastModified` descending; nullable timestamps excluded; visible caption `Según la fecha de modificación del archivo`; no dates displayed; no extra scan.
- S8 — `PlaybackHistoryStore`: independent persistence, max 20 unique songs, replay updates position and timestamp, ids no longer in the library are kept on disk and filtered at display, writes off the main thread.
- S9 — Biblioteca preserves Música/Listas/Carpetas completely: scroll position, sorting, search state and back navigation.
- S10 — MiniPlayer keeps current behavior and stays visible above the bottom bar; `MiniPlayerHost` extraction is a literal move.
- S11 — Library restoration keeps working: no artificial full-screen loader, no premature empty-library message; dashboard usable while restoring.

## Tasks

| ID | Spec | Route | Status | Evidence |
|----|------|-------|--------|----------|
| T1 | S8 | inline | done | `PlaybackHistoryStoreTest` 9/9 |
| T2 | S3,S6,S7 | inline | done | `DashboardDataTest` 26/26 |
| T3 | S10 | inline | done | literal move; `MiniPlayer` untouched |
| T4 | S1 | inline | done | `JexBottomBarItem` + 4 drawables |
| T5 | S2,S5 | inline | done | `DashboardScreen.kt` |
| T6 | S4,S5 | inline | done | `BrowseScreens.kt` |
| T7 | S9,S11 | inline | done | `TabRow` + pending intents; `testTag("library_list")` and `rememberScrollIdleSignal` intact |
| T8 | S1,S11 | inline | done | `appTab` shell + `SaveableStateHolder` per destination |
| T9 | S3,S7 | inline | done | additive flows; no playback/scan path touched |
| T10 | all | inline | done | `assembleDebug` OK; `:app:testDebugUnitTest` 258/258 |

## Log

- L1 (verbatim, key fragments): "Buscar abre Biblioteca con la búsqueda activada." / "Álbumes, Artistas e Historial tendrán pantallas reales de exploración, no simples búsquedas prellenadas." / "Mantén el comportamiento MIXED: canción → reproducir, álbum → detalles, artista → discografía cuando esté disponible." / "Añadido recientemente utilizará lastModified como fallback, sin presentarlo como fecha real de incorporación." / "Preserva las subpestañas Música, Listas y Carpetas, junto con sorting, scroll y navegación atrás." / "PlaybackHistoryStore tendrá persistencia independiente, máximo 20 canciones únicas y manejo correcto de reproducciones repetidas y canciones eliminadas." / "No hagas commits ni pushes."
- L2: Approved Stage B plan. Decisions: no DashboardViewModel (MusicViewModel is the hub), history recorded from `playerListener.onMediaItemTransition` and `playSong`, dashboard song taps reuse the active queue when the song is already in it.
- L3: Audio icons for Home/Album/Artist/History did not exist; four Material vector drawables added and wired into `JexSymbol`.
- L4: `Song` cannot be built in pure JVM tests (holds `android.net.Uri`; the constructor null-check forbids `null`), and no existing test does it. The pure layer was rewritten over `SongEntry` (no Android types); UI resolves `Song` through a `songsById` map. This is why S3/S6/S7 tests are real instead of skipped.
- L5: `HomeScreen` lost its `onOpenPlayer` parameter and two imports when the bottom bar and `MiniPlayerHost` moved to the app shell; both removed instead of left dead.
- L6: `rememberSaveable` inside `HomeScreen` is discarded when the destination unmounts, so switching Inicio <-> Biblioteca lost the search text and filters (S9). Fixed with a `SaveableStateHolder` keyed by destination in `MainActivity`.
- L7: Grouping albums/artists was running on the main thread inside `remember`. Moved to `MusicViewModel` as `albumGroups` / `artistGroups` with `flowOn(Dispatchers.Default)`, same pattern as library sorting.
- L8: The carousel/history cap was 10 while the store persists 20; aligned to `PlaybackHistoryStore.MAX_ENTRIES` so the Historial screen shows everything that is stored.
- L9: Verification (observed, no commits): `./gradlew assembleDebug` BUILD SUCCESSFUL; `./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL, 258 tests, 0 failures, 0 errors (33 new: `DashboardDataTest` 26, `PlaybackHistoryStoreTest` 9).
