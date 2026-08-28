package com.petal.browser.browser

import android.content.Context
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.RecordUnit
import java.util.ArrayList

class List_standard(private val context: Context) {

    init {
        loadDomains(context)
    }

    fun isWhite(url: String?): Boolean {
        if (url == null) return false
        val d = HelperUnit.domain(url)
        for (domain in listStandard) {
            if (d == domain) {
                return true
            }
        }
        return false
    }

    @Synchronized
    fun addDomain(domain: String?) {
        if (domain == null) return
        val action = RecordAction(context)
        action.open(true)
        action.addDomain(domain, RecordUnit.TABLE_STANDARD)
        action.close()
        listStandard.add(domain)
    }

    @Synchronized
    fun removeDomain(domain: String?) {
        if (domain == null) return
        val action = RecordAction(context)
        action.open(true)
        action.deleteDomain(domain, RecordUnit.TABLE_STANDARD)
        action.close()
        listStandard.remove(domain)
    }

    @Synchronized
    fun clearDomains() {
        val action = RecordAction(context)
        action.open(true)
        action.clearTable(RecordUnit.TABLE_STANDARD)
        action.close()
        listStandard.clear()
    }

    companion object {
        private val listStandard: MutableList<String> = ArrayList()

        @Synchronized
        @JvmStatic
        private fun loadDomains(context: Context) {
            val action = RecordAction(context)
            action.open(false)
            listStandard.clear()
            listStandard.addAll(action.listDomains(RecordUnit.TABLE_STANDARD))
            action.close()
        }
    }
}
