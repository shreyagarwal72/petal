package com.petal.browser.ui.components

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.adapter.AdapterCustomSearches
import com.petal.browser.adapter.GridAdapter
import com.petal.browser.database.CustomSearchesHelper
import com.petal.browser.item.CustomRedirect
import com.petal.browser.item.GridItem
import com.petal.browser.unit.HelperUnit
import org.json.JSONException
import java.util.LinkedList
import java.util.Objects

/**
 * Kotlin manager handling legacy filter and custom searches dialogs for BrowserActivity.
 */
object BrowserDialogManager {

    private const val TAG = "BrowserDialogManager"

    @JvmStatic
    fun showDialogFilter(activity: BrowserActivity) {
        val context: Context = activity
        val builder = MaterialAlertDialogBuilder(context)
        val dialogView = View.inflate(context, R.layout.dialog_menu, null)
        builder.setTitle(R.string.setting_filter)
        builder.setIcon(R.drawable.icon_filter)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.show()
        HelperUnit.setupDialog(context, dialog)

        val cardView = dialogView.findViewById<CardView>(R.id.item_CardViewItem)
        cardView?.visibility = View.GONE

        val menuGrid = dialogView.findViewById<GridView>(R.id.menu_grid)
        val gridList: MutableList<GridItem> = LinkedList()
        activity.sp.edit().putString("showFilterDialogX", "true").apply()
        HelperUnit.addFilterItems(activity, gridList)

        val gridAdapter = GridAdapter(context, gridList)
        menuGrid.numColumns = 2
        menuGrid.horizontalSpacing = 20
        menuGrid.verticalSpacing = 20
        menuGrid.adapter = gridAdapter

        if (menuGrid.layoutParams is ViewGroup.MarginLayoutParams) {
            val p = menuGrid.layoutParams as ViewGroup.MarginLayoutParams
            p.setMargins(56, 56, 56, 56)
            menuGrid.requestLayout()
        }

        gridAdapter.notifyDataSetChanged()
        menuGrid.setOnItemClickListener { _, _, position, _ ->
            activity.filter = true
            activity.filterBy = gridList[position].data
            dialog.cancel()
            activity.bottom_navigation.selectedItemId = R.id.page_2
        }
        dialog.setOnCancelListener {
            activity.sp.edit().putString("showFilterDialogX", "false").apply()
        }
    }

    @JvmStatic
    fun showDialogCustomSearches(activity: BrowserActivity, initialUrl: String) {
        activity.search_input?.clearFocus()
        if (activity.dialogOverview != null && activity.dialogOverview.isShowing) {
            activity.dialogOverview.cancel()
        }
        activity.ninjaWebView?.stopLoading()

        val context: Context = activity
        val builder = MaterialAlertDialogBuilder(context)
        val dialogView = View.inflate(context, R.layout.custom_redirects_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.redirects_recycler)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        var redirects = ArrayList<CustomRedirect>()
        try {
            redirects = CustomSearchesHelper.getRedirects(sp)
        } catch (e: JSONException) {
            Log.e(TAG, "Searches parsing error", e)
        }

        var displayUrl = initialUrl
        val adapter = AdapterCustomSearches(context, displayUrl, redirects)
        recyclerView.adapter = adapter

        if (displayUrl.length > 150) {
            displayUrl = displayUrl.substring(0, 150) + " [...]"
        }
        val text = "-> $displayUrl"
        builder.setTitle(R.string.custom_searches_title)
        builder.setMessage(text)
        builder.setIcon(R.drawable.icon_search)

        var dialogCustomSearches: AlertDialog? = null

        builder.setNegativeButton(R.string.create_new) { _, _ ->
            val builderAddCustom = MaterialAlertDialogBuilder(context)
            val dialogViewAddCustom = View.inflate(context, R.layout.create_new_searches, null)
            val source = dialogViewAddCustom.findViewById<TextInputEditText>(R.id.source)
            val target = dialogViewAddCustom.findViewById<TextInputEditText>(R.id.target)
            builderAddCustom.setTitle(R.string.custom_searches_title)
            builderAddCustom.setIcon(R.drawable.icon_search)
            builderAddCustom.setPositiveButton(R.string.app_cancel, null)
            builderAddCustom.setNegativeButton(R.string.app_ok) { _, _ ->
                val sourceText = source.text?.toString() ?: ""
                val targetText = target.text?.toString() ?: ""
                if (targetText.isEmpty() || sourceText.isEmpty()) return@setNegativeButton
                adapter.addRedirect(CustomRedirect(sourceText, targetText))
                try {
                    CustomSearchesHelper.saveRedirects(adapter.redirects)
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }
            }
            builderAddCustom.setView(dialogViewAddCustom)
            val dialogCustomSearchesNew = builderAddCustom.create()
            dialogCustomSearchesNew.show()
            HelperUnit.setupDialog(context, dialogCustomSearchesNew)
        }

        builder.setPositiveButton(R.string.app_cancel) { _, _ ->
            dialogCustomSearches?.cancel()
        }
        builder.setView(dialogView)
        dialogCustomSearches = builder.create()
        dialogCustomSearches.show()
        dialogCustomSearches.setCancelable(false)
        HelperUnit.setupDialog(context, dialogCustomSearches)
    }
}
