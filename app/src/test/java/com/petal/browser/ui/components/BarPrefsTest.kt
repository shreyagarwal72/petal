package com.petal.browser.ui.components

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class BarPrefsTest {

    private fun createFakeSharedPreferences(initialValues: Map<String, Any?>): SharedPreferences {
        val map = initialValues.toMutableMap()
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method: Method, args: Array<out Any?>? ->
            when (method.name) {
                "getString" -> {
                    val key = args?.get(0) as String
                    val def = args?.get(1) as? String
                    map[key] as? String ?: def
                }
                "getBoolean" -> {
                    val key = args?.get(0) as String
                    val def = args?.get(1) as? Boolean ?: false
                    map[key] as? Boolean ?: def
                }
                "toString" -> "FakeSharedPreferences"
                "hashCode" -> 42
                "equals" -> args?.get(0) === args?.get(0)
                else -> null
            }
        } as SharedPreferences
    }

    @Test
    fun testBarActionParserValid() {
        val actions = BarAction.parseActionList("TABS,NEW_TAB,MENU")
        assertEquals(3, actions.size)
        assertEquals(BarAction.TABS, actions[0])
        assertEquals(BarAction.NEW_TAB, actions[1])
        assertEquals(BarAction.MENU, actions[2])
    }

    @Test
    fun testBarActionParserIgnoresUnknownIds() {
        val actions = BarAction.parseActionList("TABS,INVALID_FOO_ACTION,MENU,UNKNOWN_BAR")
        assertEquals(2, actions.size)
        assertEquals(BarAction.TABS, actions[0])
        assertEquals(BarAction.MENU, actions[1])
    }

    @Test
    fun testBarActionParserRemovesDuplicatesKeepingOrder() {
        val actions = BarAction.parseActionList("TABS,NEW_TAB,TABS,SHARE,NEW_TAB,MENU")
        assertEquals(4, actions.size)
        assertEquals(listOf(BarAction.TABS, BarAction.NEW_TAB, BarAction.SHARE, BarAction.MENU), actions)
    }

    @Test
    fun testBarActionParserEmptyAndNull() {
        assertTrue(BarAction.parseActionList(null).isEmpty())
        assertTrue(BarAction.parseActionList("").isEmpty())
        assertTrue(BarAction.parseActionList("   ").isEmpty())
        assertTrue(BarAction.parseActionList(",, ,").isEmpty())
    }

    @Test
    fun testBarActionSerializer() {
        val actions = listOf(BarAction.TABS, BarAction.NEW_TAB, BarAction.MENU)
        assertEquals("TABS,NEW_TAB,MENU", BarAction.serializeActionList(actions))
    }

    @Test
    fun testBarPrefsEnforcesMenuPresence() {
        // SharedPreferences has no MENU in leading or trailing
        val sp = createFakeSharedPreferences(
            mapOf(
                BarPrefs.KEY_MERGE_LEADING to "TABS",
                BarPrefs.KEY_MERGE_TRAILING to "NEW_TAB,SHARE"
            )
        )
        val (leading, trailing) = BarPrefs.getSanitizedMergeActions(sp)
        assertTrue("Trailing must contain MENU when omitted", trailing.contains(BarAction.MENU))
        assertEquals(BarAction.TABS, leading[0])
        assertTrue(trailing.contains(BarAction.NEW_TAB))
    }

    @Test
    fun testBarPrefsEnforcesMax5ActionsTotal() {
        // Provide 7 actions across leading and trailing
        val sp = createFakeSharedPreferences(
            mapOf(
                BarPrefs.KEY_MERGE_LEADING to "TABS,HOME,SEARCH,BACK",
                BarPrefs.KEY_MERGE_TRAILING to "NEW_TAB,SHARE,MENU"
            )
        )
        val (leading, trailing) = BarPrefs.getSanitizedMergeActions(sp)
        val total = leading.size + trailing.size
        assertTrue("Total actions must not exceed ${BarPrefs.MAX_MERGE_ACTIONS_TOTAL}", total <= BarPrefs.MAX_MERGE_ACTIONS_TOTAL)
        assertTrue("MENU must never be removed during trimming", leading.contains(BarAction.MENU) || trailing.contains(BarAction.MENU))
    }

    @Test
    fun testBarPrefsDefaults() {
        val sp = createFakeSharedPreferences(emptyMap())
        val (leading, trailing) = BarPrefs.getSanitizedMergeActions(sp)
        assertEquals(listOf(BarAction.TABS), leading)
        assertEquals(listOf(BarAction.NEW_TAB, BarAction.MENU), trailing)
    }

    @Test
    fun testBarPrefsCaseInsensitiveIds() {
        val actions = BarAction.parseActionList("tabs,new_tab,menu")
        assertEquals(listOf(BarAction.TABS, BarAction.NEW_TAB, BarAction.MENU), actions)
    }
}
