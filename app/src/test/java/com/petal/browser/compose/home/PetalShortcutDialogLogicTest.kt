package com.petal.browser.compose.home

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetalShortcutDialogLogicTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun `validateShortcutUrl rejects empty whitespace and unsafe schemes`() {
        assertFalse(validateShortcutUrl("").isValid)
        assertFalse(validateShortcutUrl("   ").isValid)
        assertFalse(validateShortcutUrl("not a url").isValid)
        assertFalse(validateShortcutUrl("javascript:alert(1)").isValid)
        assertFalse(validateShortcutUrl("localhost_no_dot").isValid)
    }

    @Test
    fun `validateShortcutUrl normalizes valid domains and preserves https or http`() {
        val bare = validateShortcutUrl("petalbrowser.org")
        assertTrue(bare.isValid)
        assertEquals("https://petalbrowser.org", bare.normalizedUrl)
        assertNull(bare.errorRes)

        val explicitHttp = validateShortcutUrl("http://localhost:8080/test")
        assertTrue(explicitHttp.isValid)
        assertEquals("http://localhost:8080/test", explicitHttp.normalizedUrl)
    }

    @Test
    fun `saveHomeShortcuts and loadHomeShortcuts preserve id color and shape`() {
        val list = listOf(
            PetalShortcut(label = "First", url = "https://same.example", siteId = "globe", containerColor = Color(0xFF34A853), shapeName = "Flower", id = "id-1"),
            PetalShortcut(label = "Second", url = "https://same.example", siteId = "globe", containerColor = Color(0xFFEA4335), shapeName = "Burst", id = "id-2"),
        )
        saveHomeShortcuts(context, list)
        val restored = loadHomeShortcuts(context)
        assertEquals(2, restored.size)
        assertEquals("id-1", restored[0].id)
        assertEquals("id-2", restored[1].id)
        assertNotEquals(restored[0].containerColor, restored[1].containerColor)
        assertEquals(Color(0xFF34A853), restored[0].containerColor)
        assertEquals(Color(0xFFEA4335), restored[1].containerColor)
    }
}
