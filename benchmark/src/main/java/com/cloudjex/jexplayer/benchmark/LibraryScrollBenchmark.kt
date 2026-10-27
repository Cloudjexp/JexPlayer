package com.cloudjex.jexplayer.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks rapid bidirectional scrolling of the library.
 *
 * Prerequisites on the test device:
 * - JexPlayer must already have at least one SAF folder configured and a scanned
 *   library with enough songs to cover several screens (ideally thousands).
 * - Use a physical device. Emulator frame timings are not representative.
 *
 * Run:
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.cloudjex.jexplayer.benchmark.LibraryScrollBenchmark
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LibraryScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollWithColdArtwork() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        scrollLibrary(coldCache = true)
    }

    @Test
    fun scrollWithWarmArtwork() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            // Deja el proceso vivo y recorre varias pantallas para poblar la
            // caché en memoria de Coil antes de medir.
            startActivityAndWait()
            repeat(12) { index ->
                scrollOneScreen(index)
                device.waitForIdle(1_000)
            }
        }
    ) {
        startActivityAndWait()
        scrollLibrary(coldCache = false)
    }

    private fun MacrobenchmarkScope.scrollLibrary(coldCache: Boolean) {
        device.wait(Until.hasObject(By.res(LIST_TAG)), 15_000)
        val list = device.findObject(By.res(LIST_TAG))
            ?: error(
                "Library list not found. Configure a SAF folder and scan a library " +
                    "before running this benchmark."
            )
        list.setGestureMargin(device.displayWidth / 5)

        // Alternating flings exercise both directions and the artwork gate.
        repeat(if (coldCache) 6 else 10) { index ->
            list.fling(if (index % 2 == 0) Direction.DOWN else Direction.UP)
            device.waitForIdle(1_000)
        }
    }

    private fun MacrobenchmarkScope.scrollOneScreen(index: Int) {
        val list = device.findObject(By.res(LIST_TAG)) ?: return
        list.fling(if (index % 2 == 0) Direction.DOWN else Direction.UP)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.cloudjex.jexplayer"
        const val LIST_TAG = "library_list"
    }
}
