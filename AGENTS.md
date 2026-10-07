# JexPlayer — Agent Notes

Single-module Android app (`:app` only, `rootProject.name = "JexPlayer"`).

## Stack
- Kotlin 2.2.10, AGP 9.4.1, Compose BOM 2026.02.01, Material3.
- `namespace` / `applicationId`: `com.cloudjex.jexplayer`.
- `compileSdk` / `targetSdk = 37`, `minSdk = 24`, Java 11 (`VERSION_11`).
- `kotlin.code.style=official` (`gradle.properties`).
- Repos locked: `google()` + `mavenCentral()` only, `FAIL_ON_PROJECT_REPOS`.

## Entrypoints
- `app/src/main/java/com/cloudjex/jexplayer/MainActivity.kt` — only Activity, Compose `setContent` + `JexPlayerTheme`.
- `app/src/main/java/com/cloudjex/jexplayer/ui/theme/` — `Theme.kt`, `Color.kt`, `Type.kt`.

## Commands (run from repo root)
- Build debug APK: `./gradlew assembleDebug`
- Unit tests (all): `./gradlew :app:testDebugUnitTest`
- Single unit test class: `./gradlew :app:testDebugUnitTest --tests "com.cloudjex.jexplayer.ExampleUnitTest"`
- Instrumented tests (requires emulator/device): `./gradlew :app:connectedDebugAndroidTest`
- Lint: `./gradlew :app:lintDebug`

## Gotchas
- `local.properties` (`sdk.dir=/home/Cloudjex/Android/Sdk`) is machine-local, gitignored, auto-generated — do not edit or commit.
- No CI, no custom Gradle tasks, no flavors beyond default `debug`/`release`; `release` uses `optimization { packageScope = androidx/kotlin/kotlinx }`.
- Instrumented tests need a running emulator/device; prefer unit tests for fast verification.
- Use `./gradlew`, not system `gradle`; wrapper + `gradle/libs.versions.toml` are source of truth for versions.
