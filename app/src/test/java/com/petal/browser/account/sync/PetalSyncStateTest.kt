package com.petal.browser.account.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetalSyncStateTest {

    private fun state(vararg e: Triple<String, Long, Boolean>) = SyncCategoryState().also { s -> e.forEach { s.put(it.first, it.second, it.third) } }

    @Test fun newerChangeWins() {
        val mine = state(Triple("a", 10L, true))
        mine.mergeFrom(state(Triple("a", 20L, false)))
        assertTrue(mine.isDeleted("a"))
    }

    @Test fun olderDeletionLoses() {
        val mine = state(Triple("a", 30L, true))
        mine.mergeFrom(state(Triple("a", 20L, false)))
        assertFalse(mine.isDeleted("a"))
    }

    @Test fun tieKeepsTheItem() {
        val mine = state(Triple("a", 20L, false))
        mine.mergeFrom(state(Triple("a", 20L, true)))
        assertFalse(mine.isDeleted("a"))
    }

    @Test fun jsonRoundTripKeepsAllCategories() {
        val s = SyncState(reading = state(Triple("r", 5L, true)), shortcuts = state(Triple("s", 6L, false)), rules = state(Triple("saved:x", 7L, true)))
        val back = SyncState.fromJson(s.toJson())
        assertFalse(back.reading.isDeleted("r"))
        assertTrue(back.shortcuts.isDeleted("s"))
        assertEquals(7L, back.rules.map["saved:x"]?.first)
    }

    @Test fun oldStateFileWithoutNewCategoriesStillLoads() {
        val back = SyncState.fromJson(org.json.JSONObject("{\"b\":{},\"h\":{},\"p\":{}}"))
        assertTrue(back.reading.map.isEmpty())
    }

    @Test fun pruneDropsOldEntries() {
        val s = SyncState(bookmarks = state(Triple("old", 1L, false), Triple("new", 1_000_000_000_000L, false)))
        s.prune(1_000_000_000_000L + 1000L)
        assertFalse(s.bookmarks.map.containsKey("old"))
        assertTrue(s.bookmarks.map.containsKey("new"))
    }

    @Test fun deletionIsRecordedForRemovedItem() {
        val st = SyncCategoryState()
        PetalFolderSync.noteLocalChanges(st, setOf("a", "b"), setOf("a"), 100L)
        assertTrue(st.isDeleted("b"))
        assertFalse(st.isDeleted("a"))
    }

    @Test fun newItemIsStampedSoOldDeletionCannotRemoveIt() {
        val st = SyncCategoryState()
        PetalFolderSync.noteLocalChanges(st, setOf("a"), setOf("a", "n"), 500L)
        st.mergeFrom(state(Triple("n", 100L, false)))
        assertFalse(st.isDeleted("n"))
    }

    @Test fun massDisappearanceIsNotSpreadAsDeletions() {
        val base = (1..20).map { "u$it" }.toSet()
        val st = SyncCategoryState()
        PetalFolderSync.noteLocalChanges(st, base, setOf("u1"), 100L)
        assertTrue(st.map.values.none { !it.second })
    }

    @Test fun smallSetDeletionsStillCount() {
        val st = SyncCategoryState()
        PetalFolderSync.noteLocalChanges(st, setOf("a", "b", "c"), emptySet(), 100L)
        assertTrue(st.isDeleted("a") && st.isDeleted("b") && st.isDeleted("c"))
    }
}
