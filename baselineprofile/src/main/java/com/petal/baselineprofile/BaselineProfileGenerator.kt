package com.petal.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test

/** Records cold start, home scroll, opening a page, scrolling it (bars hide/show) and the tab switcher. */
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.petal.browser") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.apply {
            setGestureMargin(device.displayWidth / 5)
            fling(Direction.DOWN); fling(Direction.UP)
        }
        device.waitForIdle()
    }
}
