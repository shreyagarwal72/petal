package com.petal.browser.browser

import android.content.Context
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.unit.PetalAndroidUtils
import java.util.ArrayList

class StandardDomainWhitelist(private val context: Context) {

    init {
        loadDomains(context)
    }

    companion object {
        private val listStandard: MutableList<String> = ArrayList()

        @Synchronized
        private fun loadDomains(context: Context) {
            val action = HistoryBookmarkRepository(context)
            action.open(false)
            listStandard.clear()
            listStandard.addAll(action.listDomains(HistoryBookmarkContract.TABLE_STANDARD))
            action.close()
        }
    }

    fun isWhite(url: String?): Boolean {
        if (url == null) return false
        val targetDomain = PetalAndroidUtils.domain(url)
        return listStandard.any { it == targetDomain }
    }

    @Synchronized
    fun addDomain(domain: String) {
        val action = HistoryBookmarkRepository(context)
        action.open(true)
        action.addDomain(domain, HistoryBookmarkContract.TABLE_STANDARD)
        action.close()
        listStandard.add(domain)
    }

    @Synchronized
    fun removeDomain(domain: String) {
        val action = HistoryBookmarkRepository(context)
        action.open(true)
        action.deleteDomain(domain, HistoryBookmarkContract.TABLE_STANDARD)
        action.close()
        listStandard.remove(domain)
    }

    @Synchronized
    fun clearDomains() {
        val action = HistoryBookmarkRepository(context)
        action.open(true)
        action.clearTable(HistoryBookmarkContract.TABLE_STANDARD)
        action.close()
        listStandard.clear()
    }
}
