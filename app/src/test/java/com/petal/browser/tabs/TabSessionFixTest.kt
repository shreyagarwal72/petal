package com.petal.browser.tabs

import com.petal.browser.browser.HygieneTab
import com.petal.browser.browser.PetalTabArchiver
import com.petal.browser.browser.PetalTabDuplicates
import com.petal.browser.browser.PetalTabMetadata
import com.petal.browser.browser.PetalTabMetadataMigration
import com.petal.browser.browser.PetalTabMetadataStore
import com.petal.browser.browser.SessionMerge
import com.petal.browser.engine.gecko.PetalSessionRules
import com.petal.browser.browser.ClosedTabBuffer
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import com.petal.browser.engine.gecko.PetalTabSuspender
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TabSessionFixTest {

    private fun tab(id: String, url: String, priv: Boolean = false, sel: Boolean = false,
                    pinned: Boolean = false, last: Long = 0L, media: Boolean = false) =
        HygieneTab(id, url, priv, sel, pinned, last, media)

    // ── Suspension eligibility ──────────────────────────────────────────────
    @Test fun `selected tab is never suspended`() =
        assertFalse(PetalTabSuspender.isEligible(true, true, false, false, true))

    @Test fun `media tab is never suspended`() =
        assertFalse(PetalTabSuspender.isEligible(false, true, true, false, false))

    @Test fun `pinned tab protected only when setting is on`() {
        assertFalse(PetalTabSuspender.isEligible(false, true, false, true, true))
        assertTrue(PetalTabSuspender.isEligible(false, true, false, true, false))
    }

    @Test fun `already sleeping tab is not a candidate`() =
        assertFalse(PetalTabSuspender.isEligible(false, false, false, false, true))

    @Test fun `plain background tab is eligible`() =
        assertTrue(PetalTabSuspender.isEligible(false, true, false, false, true))

    @Test fun `ui hidden trim level is not memory pressure`() {
        assertFalse(PetalTabSuspender.isRealPressure(20)) // TRIM_MEMORY_UI_HIDDEN
        assertTrue(PetalTabSuspender.isRealPressure(15)) // RUNNING_CRITICAL
        assertTrue(PetalTabSuspender.isRealPressure(80)) // COMPLETE
    }

    // ── Restore merge (store already has tabs) ──────────────────────────────
    @Test fun `cold process with intent tab merges saved tabs and keeps intent tab selected`() {
        val plan = SessionMerge.plan(listOf("intent"), "intent", listOf("a", "b"), "b")
        assertEquals(listOf("a", "b"), plan.restoreIds)
        assertEquals("intent", plan.selectId)
        assertTrue(plan.restoreBefore)
    }

    @Test fun `empty store restores saved selection after nothing`() {
        val plan = SessionMerge.plan(emptyList(), null, listOf("a", "b"), "b")
        assertEquals("b", plan.selectId)
        assertFalse(plan.restoreBefore)
    }

    @Test fun `saved tabs already in store are skipped`() {
        val plan = SessionMerge.plan(listOf("a"), "a", listOf("a", "b"), "a")
        assertEquals(listOf("b"), plan.restoreIds)
    }

    // ── Restore with history (engine state kept) ────────────────────────────
    @Test fun `saved state with several entries gives back history`() {
        val json = JSONObject("""{"history":{"entries":[{"url":"a"},{"url":"b"},{"url":"c"}],"index":3}}""")
        assertTrue(PetalSessionRules.historyHasBack(json))
    }

    @Test fun `saved state with one entry has no back history`() {
        val json = JSONObject("""{"history":{"entries":[{"url":"a"}],"index":1}}""")
        assertFalse(PetalSessionRules.historyHasBack(json))
    }

    @Test fun `unknown state layout is trusted`() =
        assertTrue(PetalSessionRules.historyHasBack(JSONObject("""{"x":1}""")))

    // ── Metadata prune / migration ──────────────────────────────────────────
    @Test fun `prune keeps live and recently closed ids`() {
        assertEquals(setOf("a", "b", "z"), PetalTabMetadataStore.retainedIds(listOf("a", "b"), listOf("z")))
    }

    @Test fun `prune is skipped when live list is empty`() =
        assertNull(PetalTabMetadataStore.retainedIds(emptyList(), listOf("z")))

    @Test fun `legacy prefs json migrates to model map`() {
        val json = """{"t1":{"tabId":"t1","customTitle":"Work","isPinned":true,"isPinnedGlobal":true},
                       "t2":{"tabId":"t2","tabGroupId":"g"}}"""
        val m = PetalTabMetadataMigration.parseLegacyJson(json)
        assertEquals(2, m.size)
        assertEquals("Work", m["t1"]?.customTitle)
        assertTrue(m["t1"]!!.isPinned)
        assertEquals("g", m["t2"]?.tabGroupId)
    }

    @Test fun `corrupt or blank legacy json migrates nothing`() {
        assertTrue(PetalTabMetadataMigration.parseLegacyJson("{not json").isEmpty())
        assertTrue(PetalTabMetadataMigration.parseLegacyJson(null).isEmpty())
    }

    @Test fun `legacy row with mismatched key is dropped`() {
        val m = PetalTabMetadataMigration.parseLegacyJson("""{"a":{"tabId":"b"}}""")
        assertTrue(m.isEmpty())
    }

    // ── Duplicates ──────────────────────────────────────────────────────────
    @Test fun `duplicates keep selected tab and ignore fragments and trailing slash`() {
        val ids = PetalTabDuplicates.idsToClose(listOf(
            tab("1", "https://Example.com/a/"), tab("2", "https://example.com/a#x", sel = true), tab("3", "https://example.com/a")
        ))
        assertEquals(listOf("1", "3"), ids)
    }

    @Test fun `private and normal tabs are never duplicates of each other`() {
        val ids = PetalTabDuplicates.idsToClose(listOf(
            tab("1", "https://example.com"), tab("2", "https://example.com", priv = true)
        ))
        assertTrue(ids.isEmpty())
    }

    @Test fun `home and blank tabs are not duplicates`() {
        val ids = PetalTabDuplicates.idsToClose(listOf(tab("1", "about:home"), tab("2", "about:home"), tab("3", "about:blank"), tab("4", "about:blank")))
        assertTrue(ids.isEmpty())
    }

    // ── Auto-archive ────────────────────────────────────────────────────────
    @Test fun `archive skips selected pinned media private and unknown-age tabs`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val ids = PetalTabArchiver.idsToArchive(listOf(
            tab("old", "u", last = now - 10 * day), tab("sel", "u", sel = true, last = now - 10 * day),
            tab("pin", "u", pinned = true, last = now - 10 * day), tab("med", "u", media = true, last = now - 10 * day),
            tab("priv", "u", priv = true, last = now - 10 * day), tab("unk", "u", last = 0L),
            tab("fresh", "u", last = now - 2 * day)
        ), now, 7)
        assertEquals(listOf("old"), ids)
    }

    @Test fun `archive is off when days is zero`() =
        assertTrue(PetalTabArchiver.idsToArchive(listOf(tab("old", "u", last = 1L)), 10_000_000_000L, 0).isEmpty())

    // ── Incognito flush never clears the normal profile ─────────────────────
    @Test fun `incognito cleanup does not call StorageController clearData`() {
        val f = listOf(
            "src/main/java/com/petal/browser/compose/incognito/PetalIncognitoSessionManager.kt",
            "app/src/main/java/com/petal/browser/compose/incognito/PetalIncognitoSessionManager.kt"
        ).map { File(it) }.firstOrNull { it.exists() }
        assertNotNull("PetalIncognitoSessionManager.kt not found from ${File(".").absolutePath}", f)
        val code = f!!.readText().lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertFalse("clearData would wipe the shared/normal profile", code.contains("clearData("))
    }

    // ── Ephemeral (Custom Tab / PWA) tabs ───────────────────────────────────
    private fun st(id: String) = TabSessionState(id = id, content = ContentState(url = "https://x/$id"))

    @Test fun `ephemeral ids are recognised`() {
        assertTrue(PetalSessionRules.isEphemeralTabId("tab_custom_123"))
        assertTrue(PetalSessionRules.isEphemeralTabId("pwa_9"))
        assertFalse(PetalSessionRules.isEphemeralTabId("tab_1"))
    }

    @Test fun `persisted state drops ephemeral tabs and fixes the selection`() {
        val state = BrowserState(tabs = listOf(st("a"), st("tab_custom_1"), st("b")), selectedTabId = "tab_custom_1")
        val out = PetalSessionRules.withoutEphemeralTabs(state, fallbackSelectedId = "b")
        assertEquals(listOf("a", "b"), out.tabs.map { it.id })
        assertEquals("b", out.selectedTabId)
    }

    @Test fun `state without ephemeral tabs is returned unchanged`() {
        val state = BrowserState(tabs = listOf(st("a")), selectedTabId = "a")
        assertSame(state, PetalSessionRules.withoutEphemeralTabs(state))
    }

    // ── Undo close buffer ───────────────────────────────────────────────────
    @Test fun `undo returns a snapshot once`() {
        val b = ClosedTabBuffer<String>(25, 60_000L)
        b.put("t1", "snap", 0L)
        assertEquals("snap", b.take("t1"))
        assertNull(b.take("t1"))
    }

    @Test fun `undo buffer evicts oldest beyond max and expired entries`() {
        val b = ClosedTabBuffer<Int>(2, 1_000L)
        b.put("a", 1, 0L); b.put("b", 2, 10L); b.put("c", 3, 20L)
        assertNull(b.take("a"))
        assertEquals(2, b.take("b"))
        b.put("old", 9, 100L); b.put("new", 10, 5_000L)
        assertNull(b.take("old"))
        assertEquals(10, b.take("new"))
    }
}
