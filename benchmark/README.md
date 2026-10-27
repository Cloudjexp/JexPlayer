# Library scroll Macrobenchmark

Measures `FrameTimingMetric` for rapid bidirectional library scrolling.

## Prerequisites

- Physical Android device (API 24+). Emulator numbers are not representative.
- Unlock the device and keep it connected over ADB.
- Install/configure JexPlayer once with at least one SAF folder and a library large
  enough to require several screens of scrolling (ideally 1,000+ songs).
- Leave the app on the **library** screen; the benchmark looks for the
  `library_list` semantics tag.

The benchmark uses the minified `benchmark` build type (`initWith(release)`) and
the debug signing key, so it measures a release-like APK.

## Run

```bash
adb devices
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cloudjex.jexplayer.benchmark.LibraryScrollBenchmark
```

The two tests are:

- `scrollWithColdArtwork` — process is killed before measurement, so Coil's
  memory cache is cold; the disk artwork library cache remains warm.
- `scrollWithWarmArtwork` — the setup block scrolls through several screens to
  populate the in-memory artwork cache before measuring.

Results and Perfetto traces land under:

```
benchmark/build/outputs/connected_android_test_additional_output/
```

Open the `.perfetto-trace` files in Android Studio or <https://ui.perfetto.dev>
to inspect `Choreographer#doFrame`, `RecyclerView`/`LazyColumn` composition,
bitmap decode slices, and GC activity.
