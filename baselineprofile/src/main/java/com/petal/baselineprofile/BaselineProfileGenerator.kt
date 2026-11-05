package com.petal.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/** Records cold start, home scroll, opening a page, tab switcher open/close, and omnibox open. */
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.petal.browser") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // 1. Home scroll fling
        device.findObject(By.scrollable(true))?.apply {
            setGestureMargin(device.displayWidth / 5)
            fling(Direction.DOWN)
            fling(Direction.UP)
        }
        device.waitForIdle()

        // 2. Tab switcher open and close
        val tabsButton = device.findObject(By.desc("Tabs"))
            ?: device.findObject(By.text("Tabs"))
        if (tabsButton != null) {
            tabsButton.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.desc("Tab")), 1000)

            val tabCard = device.findObject(By.desc("Tab"))
            if (tabCard != null) {
                tabCard.click()
            } else {
                device.pressBack()
            }
            device.waitForIdle()
        }

        // 3. Omnibox open and close
        val searchPill = device.findObject(By.desc("Search"))
            ?: device.findObject(By.textContains("Search"))
            ?: device.findObject(By.desc("Privacy Shield"))
        if (searchPill != null) {
            searchPill.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.desc("Back")), 1000)

            val omniboxBack = device.findObject(By.desc("Back"))
            if (omniboxBack != null) {
                omniboxBack.click()
            } else {
                device.pressBack()
            }
            device.waitForIdle()
        }
    }
}
