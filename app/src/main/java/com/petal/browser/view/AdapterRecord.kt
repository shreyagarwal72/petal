package com.petal.browser.view

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.card.MaterialCardView
import com.petal.browser.R
import com.petal.browser.database.FaviconHelper
import com.petal.browser.database.Record
import com.petal.browser.unit.HelperUnit
import java.text.SimpleDateFormat
import java.util.Locale

class AdapterRecord(
    private val context: Context,
    private val list: List<Record>
) : ArrayAdapter<Record>(context, R.layout.item_list, list) {

    private val layoutResId: Int = R.layout.item_list

    private class Holder {
        lateinit var title: TextView
        lateinit var time: TextView
        lateinit var favicon: ImageView
        lateinit var cardView: MaterialCardView
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: Holder
        val view: View

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(layoutResId, parent, false)
            holder = Holder()
            holder.title = view.findViewById(R.id.titleView)
            holder.time = view.findViewById(R.id.dateView)
            holder.favicon = view.findViewById(R.id.item_icon)
            holder.cardView = view.findViewById(R.id.item_CardViewItem)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as Holder
        }

        val record = list[position]
        val title = record.title
        val url = record.getURL() ?: ""
        val time = record.time

        val iconColor = record.iconColor

        if (iconColor == 1L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.red, null))
        else if (iconColor == 2L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.pink, null))
        else if (iconColor == 3L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
        else if (iconColor == 4L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.blue, null))
        else if (iconColor == 5L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.teal, null))
        else if (iconColor == 6L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.green, null))
        else if (iconColor == 7L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.lime, null))
        else if (iconColor == 8L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.yellow, null))
        else if (iconColor == 9L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.orange, null))
        else if (iconColor == 10L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.brown, null))
        else if (iconColor == 11L) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.grey, null))
        else {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true)
            holder.cardView.setCardBackgroundColor(typedValue.data)
        }

        if (title.isNullOrEmpty()) {
            holder.title.text = url
        } else {
            holder.title.text = title
        }

        if (time != 0L) {
            val sdf = SimpleDateFormat("HH:mm - dd.MM.yyyy", Locale.getDefault())
            holder.time.text = sdf.format(time)
        } else {
            holder.time.text = url
            HelperUnit.setHighLightedText(context, holder.time, url, HelperUnit.domain(url))
        }

        FaviconHelper.setFavicon(context, holder.favicon, url, R.id.item_icon, R.drawable.icon_preview)

        return view
    }
}
