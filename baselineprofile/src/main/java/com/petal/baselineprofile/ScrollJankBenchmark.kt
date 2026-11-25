package com.petal.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
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
 * Macrobenchmark suite measuring frame timing (`FrameTimingMetric`: P50/P90/P95/P99 & frame overruns)
 * during active scrolling and toolbar collapse across representative web workloads, both with
 * Liquid Glass enabled and disabled.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollJankBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollGitHub_glassOn() = benchmarkPageScroll(
        url = URL_GITHUB,
        glassEnabled = true
    )

    @Test
    fun scrollGitHub_glassOff() = benchmarkPageScroll(
        url = URL_GITHUB,
        glassEnabled = false
    )

    @Test
    fun scrollLongArticle_glassOn() = benchmarkPageScroll(
        url = URL_LONG_ARTICLE,
        glassEnabled = true
    )

    @Test
    fun scrollLongArticle_glassOff() = benchmarkPageScroll(
        url = URL_LONG_ARTICLE,
        glassEnabled = false
    )

    @Test
    fun scrollYouTube_glassOn() = benchmarkPageScroll(
        url = URL_YOUTUBE,
        glassEnabled = true
    )

    @Test
    fun scrollYouTube_glassOff() = benchmarkPageScroll(
        url = URL_YOUTUBE,
        glassEnabled = false
    )

    private fun benchmarkPageScroll(url: String, glassEnabled: Boolean) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                setBarLiquidGlassPreference(glassEnabled)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(PACKAGE_NAME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivityAndWait(intent)
                device.waitForIdle(PAGE_LOAD_IDLE_MS)
            }
        ) {
            val content = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "main_content")),
                CONTENT_WAIT_MS
            )
            val w = device.displayWidth
            val h = device.displayHeight
            val marginX = w / 2

            // Smooth downward drag (triggers bar collapse) followed by upward return drag and flings.
            if (content != null) {
                content.setGestureMargin(w / 8)
                repeat(3) {
                    content.scroll(Direction.DOWN, 0.75f)
                    device.waitForIdle(250L)
                }
                content.fling(Direction.UP)
                device.waitForIdle(350L)
                content.fling(Direction.DOWN)
                device.waitForIdle(350L)
            } else {
                repeat(3) {
                    device.swipe(marginX, (h * 0.78f).toInt(), marginX, (h * 0.25f).toInt(), 28)
                    device.waitForIdle(250L)
                }
                device.swipe(marginX, (h * 0.28f).toInt(), marginX, (h * 0.78f).toInt(), 10)
                device.waitForIdle(350L)
                device.swipe(marginX, (h * 0.78f).toInt(), marginX, (h * 0.22f).toInt(), 10)
                device.waitForIdle(350L)
            }
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.setBarLiquidGlassPreference(enabled: Boolean) {
        val flag = if (enabled) "true" else "false"
        // Tries updating the default SharedPreferences XML on debuggable/profileable builds via run-as.
        val script = """
            prefs="/data/data/$PACKAGE_NAME/shared_prefs/${PACKAGE_NAME}_preferences.xml"
            if [ -f "${'$'}prefs" ]; then
              for key in sp_liquid_glass_enabled sp_liquid_glass_address_bar sp_liquid_glass_bottom_nav; do
                if grep -q "name=\"${'$'}key\"" "${'$'}prefs"; then
                  sed -i "s/<boolean name=\"${'$'}key\" value=\"[^\"]*\"/<boolean name=\"${'$'}key\" value=\"$flag\"/" "${'$'}prefs"
                else
                  sed -i "s/<\/map>/    <boolean name=\"${'$'}key\" value=\"$flag\" \/>\n<\/map>/" "${'$'}prefs"
                fi
              done
            fi
        """.trimIndent().replace("\n", " ; ")
        try {
            device.executeShellCommand("run-as $PACKAGE_NAME sh -c '$script'")
        } catch (_: Throwable) {
            // Non-debuggable release/benchmarkable builds ignore run-as; pre-configure device toggle if needed.
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.petal.browser"
        private const val PAGE_LOAD_IDLE_MS = 3_500L
        private const val CONTENT_WAIT_MS = 4_000L

        private const val URL_GITHUB = "https://github.com/mozilla-mobile/firefox-android"
        private const val URL_LONG_ARTICLE = "https://en.wikipedia.org/wiki/Rendering_(computer_graphics)"
        private const val URL_YOUTUBE = "https://m.youtube.com"
    }
}
