package com.petal.browser.view

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.petal.browser.R
import com.petal.browser.database.FaviconHelper
import com.petal.browser.database.Record
import com.petal.browser.unit.PetalAndroidUtils
import java.text.SimpleDateFormat
import java.util.Locale

open class RecordListAdapter(
    private val context: Context,
    private val list: List<Record>
) : ArrayAdapter<Record>(context, R.layout.item_list, list) {

    private class Holder {
        var title: TextView? = null
        var time: TextView? = null
        var favicon: ImageView? = null
        var cardView: MaterialCardView? = null
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: Holder
        val view: View

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_list, parent, false)
            holder = Holder().apply {
                title = view.findViewById(R.id.titleView)
                time = view.findViewById(R.id.dateView)
                favicon = view.findViewById(R.id.iconView)
                cardView = view.findViewById(R.id.item_CardViewItem)
            }
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as Holder
        }

        val record = list.getOrNull(position)
        if (record != null) {
            holder.title?.text = record.title
            val timeMs = record.time
            if (timeMs > 0) {
                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                holder.time?.text = sdf.format(timeMs)
            } else {
                holder.time?.text = record.url
            }
            PetalAndroidUtils.setHighLightedText(context, holder.time, holder.time?.text?.toString() ?: "", PetalAndroidUtils.domain(record.url))
            holder.cardView?.let { FaviconHelper.setFavicon(context, it, record.url, R.id.iconView, R.drawable.icon_bookmark) }
        }

        return view
    }
}
