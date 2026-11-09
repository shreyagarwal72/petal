package com.petal.browser.view

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import com.petal.browser.R

class GridAdapter(
    private val context: Context,
    private val list: List<GridItem>
) : BaseAdapter() {

    private class Holder {
        var title: TextView? = null
        var cardView: CardView? = null
        var iconMenu: ImageView? = null
    }

    override fun getCount(): Int = list.size
    override fun getItem(position: Int): Any = list[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val holder: Holder
        val view: View

        if (convertView == null) {
            val item = list[position]
            val text = item.title

            view = LayoutInflater.from(context).inflate(R.layout.item_menu, parent, false)
            holder = Holder().apply {
                title = view.findViewById(R.id.menuEntry)
                title?.text = text
                cardView = view.findViewById(R.id.menuCardView)
                iconMenu = view.findViewById(R.id.iconMenu)
            }

            try {
                holder.iconMenu?.setImageResource(item.data)
            } catch (e: Exception) {
                Log.i("Petal", "Exception:$e")
            }

            if (sp.getString("showFilterDialogX", "false") == "true") {
                when (text) {
                    sp.getString("icon_01", context.getString(R.string.color_red)) ->
                        holder.cardView?.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.red, null))
                    sp.getString("icon_02", context.getString(R.string.color_pink)) ->
                        holder.cardView?.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.pink, null))
                    sp.getString("icon_03", context.getString(R.string.color_purple)) ->
                        holder.cardView?.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
                    sp.getString("icon_04", context.getString(R.string.color_blue)) ->
                        holder.cardView?.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.blue, null))
                }
            }
            view.tag = holder
        } else {
            view = convertView
        }
        return view
    }
}
