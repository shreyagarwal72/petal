package com.petal.browser.view

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.card.MaterialCardView
import com.petal.browser.R
import com.petal.browser.database.Record
import com.petal.browser.unit.HelperUnit
import java.util.ArrayList
import java.util.Comparator
import java.util.HashSet

class AdapterSearch(
    private val context: Context,
    private val layoutResId: Int,
    recordList: List<Record>
) : BaseAdapter(), Filterable {

    private val liveSuggestions: MutableList<String> = ArrayList()
    private val originalList: MutableList<CompleteItem> = ArrayList()
    private var resultList: List<CompleteItem> = ArrayList()
    private var count: Int = 0
    private val filter: CompleteFilter = CompleteFilter()

    init {
        getRecordList(recordList)
    }

    @Synchronized
    fun setLiveSuggestions(suggestions: List<String>?) {
        liveSuggestions.clear()
        if (suggestions != null) {
            liveSuggestions.addAll(suggestions)
        }
        filter.refilter()
    }

    private fun getRecordList(recordList: List<Record>) {
        for (record in recordList) {
            val title = record.title
            val url = record.getURL()
            if (!title.isNullOrEmpty() && !url.isNullOrEmpty()) {
                originalList.add(CompleteItem(title, url))
            }
        }

        val set: Set<CompleteItem> = HashSet(originalList)
        originalList.clear()
        originalList.addAll(set)
    }

    override fun getCount(): Int {
        return if (count > 0) {
            resultList.size
        } else {
            0
        }
    }

    override fun getFilter(): Filter {
        return filter
    }

    override fun getItem(position: Int): Any {
        return resultList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: Holder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(layoutResId, null, false)
            holder = Holder()
            holder.titleView = view.findViewById(R.id.titleView)
            holder.urlView = view.findViewById(R.id.dateView)
            holder.favicon = view.findViewById(R.id.item_icon)
            holder.albumCardView = view.findViewById(R.id.item_CardViewItem)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as Holder
        }

        val item = resultList[position]
        holder.titleView.text = item.title
        holder.urlView.text = item.url

        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true)
        val color = typedValue.data
        holder.albumCardView.setCardBackgroundColor(color)

        holder.favicon.setImageResource(R.drawable.icon_image_broken)
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val s = sp.getString("searchInput", "") ?: ""
        if (s.isNotEmpty()) {
            HelperUnit.setHighLightedTextSearch(context, holder.urlView, s)
            HelperUnit.setHighLightedTextSearch(context, holder.titleView, s)
        }
        return view
    }

    private class CompleteItem(val title: String, val url: String) {
        var index: Int = Int.MAX_VALUE

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CompleteItem) return false
            return title == other.title && url == other.url
        }

        override fun hashCode(): Int {
            return title.hashCode() xor url.hashCode()
        }
    }

    private class Holder {
        lateinit var favicon: ImageView
        lateinit var titleView: TextView
        lateinit var urlView: TextView
        lateinit var albumCardView: MaterialCardView
    }

    private inner class CompleteFilter : Filter() {
        private var lastConstraint: CharSequence = ""

        fun refilter() {
            filter(lastConstraint)
        }

        override fun performFiltering(prefix: CharSequence?): FilterResults {
            if (prefix == null) {
                return FilterResults()
            }
            lastConstraint = prefix

            val workList: MutableList<CompleteItem> = ArrayList()
            val addedTitles: MutableSet<String> = HashSet()

            // 1. Add Live Google Search Recommendations
            synchronized(this@AdapterSearch) {
                for (suggestion in liveSuggestions) {
                    if (suggestion.isNotBlank() && !addedTitles.contains(suggestion.lowercase())) {
                        val sugItem = CompleteItem(suggestion, suggestion)
                        sugItem.index = -1 // Top priority
                        workList.add(sugItem)
                        addedTitles.add(suggestion.lowercase())
                    }
                }
            }

            // 2. Add matching local history & bookmarks
            for (item in originalList) {
                val titleLower = item.title.lowercase()
                val urlLower = item.url.lowercase()
                val prefixLower = prefix.toString().lowercase()

                if (titleLower.contains(prefixLower) || urlLower.contains(prefixLower)) {
                    if (!addedTitles.contains(titleLower)) {
                        var index = titleLower.indexOf(prefixLower)
                        if (index < 0) index = urlLower.indexOf(prefixLower)
                        item.index = if (index >= 0) index + 10 else 100
                        workList.add(item)
                        addedTitles.add(titleLower)
                    }
                }
            }

            workList.sortBy { it.index }
            val results = FilterResults()
            results.values = workList
            results.count = workList.size
            return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            count = results?.count ?: 0
            if (results != null && results.count > 0) {
                resultList = results.values as List<CompleteItem>
                notifyDataSetChanged()
            } else {
                resultList = ArrayList()
                notifyDataSetInvalidated()
            }
        }
    }
}
