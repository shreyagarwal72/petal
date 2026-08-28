package com.petal.browser.activity

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.petal.browser.R
import com.petal.browser.browser.List_standard
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.RecordUnit
import com.petal.browser.view.AdapterProfileList
import com.petal.browser.view.NinjaToast

class Settings_ProfileList : AppCompatActivity() {

    private lateinit var list: MutableList<String>
    private lateinit var listStandard: List_standard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HelperUnit.initTheme(this)
        EdgeToEdge.enable(this)
        setContentView(R.layout.activity_settings_profile_list)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listStandard = List_standard(this)
        val action = RecordAction(this)
        action.open(false)
        list = action.listDomains(RecordUnit.TABLE_STANDARD).toMutableList()
        action.close()
        val listView = findViewById<ListView>(R.id.whitelist)
        listView.emptyView = findViewById(R.id.whitelist_empty)

        val adapter = AdapterProfileList(this, list)
        listView.adapter = adapter
        adapter.notifyDataSetChanged()

        listView.setOnItemClickListener { _, _, position, _ ->
            val domain = list[position]
            val recordAction = RecordAction(this)
            recordAction.open(true)
            recordAction.deleteDomain(domain, RecordUnit.TABLE_STANDARD)
            recordAction.close()
            list.removeAt(position)
            adapter.notifyDataSetChanged()
            Snackbar.make(listView, R.string.deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                    val restoreAction = RecordAction(this)
                    restoreAction.open(true)
                    restoreAction.addDomain(domain, RecordUnit.TABLE_STANDARD)
                    restoreAction.close()
                    list.add(position, domain)
                    adapter.notifyDataSetChanged()
                }.show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_whitelist, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        when (menuItem.itemId) {
            android.R.id.home -> finish()
            R.id.menu_help -> {
                val webpage = Uri.parse("https://github.com/shreyagarwal72/petal")
                BrowserUnit.intentURL(this, webpage)
            }
            R.id.menu_clear -> {
                val action = RecordAction(this)
                action.open(true)
                action.clearTable(RecordUnit.TABLE_STANDARD)
                action.close()
                list.clear()
                val listView = findViewById<ListView>(R.id.whitelist)
                (listView.adapter as? AdapterProfileList)?.notifyDataSetChanged()
                NinjaToast.show(this, R.string.toast_delete_successful)
            }
        }
        return true
    }
}
