package com.petal.browser.activity

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petal.browser.R
import com.petal.browser.unit.HelperUnit
import com.petal.browser.view.AdapterSettingsMenu
import com.petal.browser.view.MenuItem
import java.util.ArrayList
import java.util.Collections

class Settings_Menu : AppCompatActivity() {

    private lateinit var masterList: MutableList<MenuItem>
    private lateinit var adapter: AdapterSettingsMenu
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HelperUnit.initTheme(this)
        EdgeToEdge.enable(this)
        setContentView(R.layout.activity_settings_menu)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadList()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewSettings)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AdapterSettingsMenu(masterList)
        recyclerView.adapter = adapter

        val simpleCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                Collections.swap(masterList, fromPosition, toPosition)
                recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        saveList()
    }

    private fun saveList() {
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(masterList)
        editor.putString(KEY_LIST, json)
        editor.apply()
    }

    private fun loadList() {
        val gson = Gson()
        val json = sharedPreferences.getString(KEY_LIST, null)
        val type = object : TypeToken<ArrayList<MenuItem>>() {}.type

        if (json != null) {
            val loadedList: ArrayList<MenuItem>? = gson.fromJson(json, type)
            if (loadedList != null) {
                masterList = loadedList
            } else {
                initDefaultList()
            }
        } else {
            initDefaultList()
        }
    }

    private fun initDefaultList() {
        masterList = ArrayList()
        masterList.add(MenuItem(getString(R.string.menu_overview_bookmarks), R.drawable.icon_bookmarks, true))
        masterList.add(MenuItem(getString(R.string.menu_history), R.drawable.icon_history, true))
        masterList.add(MenuItem(getString(R.string.menu_overview_history), R.drawable.icon_history_plus, true))
        masterList.add(MenuItem(getString(R.string.menu_overview_downloads), R.drawable.icon_download, true))
        masterList.add(MenuItem(getString(R.string.menu_share), R.drawable.icon_share, true))
        masterList.add(MenuItem(getString(R.string.menu_find), R.drawable.icon_search, true))
        masterList.add(MenuItem(getString(R.string.menu_save_as), R.drawable.icon_save, true))
        masterList.add(MenuItem(getString(R.string.menu_screenshot), R.drawable.icon_screenshot, true))
        masterList.add(MenuItem(getString(R.string.menu_open_with), R.drawable.icon_open_in_browser, true))
        masterList.add(MenuItem(getString(R.string.menu_overview_settings), R.drawable.icon_settings, true))
    }

    companion object {
        const val PREF_NAME = "MenuPreferences"
        const val KEY_LIST = "MenuList"
    }
}
