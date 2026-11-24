package com.petal.browser.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BarEnumPreferenceTest {
    @Test fun heightDefaultsToCompact() {
        assertEquals(AddressBarHeight.COMPACT, AddressBarHeight.DEFAULT)
        assertEquals(AddressBarHeight.COMPACT, AddressBarHeight.fromPref(null))
        assertEquals(AddressBarHeight.COMPACT, AddressBarHeight.fromPref("garbage"))
        assertEquals(AddressBarHeight.STANDARD, AddressBarHeight.fromPref("standard"))
    }

    @Test fun roundTripsStoredStrings() {
        AddressBarHeight.entries.forEach { assertEquals(it, AddressBarHeight.fromPref(it.pref)) }
        AddressBarPosition.entries.forEach { assertEquals(it, AddressBarPosition.fromPref(it.pref)) }
        BarCollapseStyle.entries.forEach { assertEquals(it, BarCollapseStyle.fromPref(it.pref)) }
        AddressBarStyle.entries.forEach { assertEquals(it, AddressBarStyle.fromPref(it.pref)) }
        MergeLayout.entries.forEach { assertEquals(it, MergeLayout.fromPref(it.pref)) }
    }

    @Test fun legacyRawStringsStillMap() {
        assertEquals(AddressBarPosition.BOTTOM, AddressBarPosition.fromPref("BOTTOM"))
        assertEquals(AddressBarPosition.TOP, AddressBarPosition.fromPref(null))
        assertEquals(BarCollapseStyle.HIDE, BarCollapseStyle.fromPref("HIDE"))
        assertEquals(BarCollapseStyle.PILL, BarCollapseStyle.fromPref(""))
        assertEquals(MergeLayout.STACKED, MergeLayout.fromPref("stacked"))
    }
}
