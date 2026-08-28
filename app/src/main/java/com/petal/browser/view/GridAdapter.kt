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

class GridAdapter(private val context: Context, private val list: List<GridItem>) : BaseAdapter() {

    override fun getCount(): Int {
        return list.size
    }

    override fun getItem(position: Int): Any {
        return list[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val holder: Holder
        val view: View

        if (convertView == null) {
            val item = list[position]
            val text = item.title

            view = LayoutInflater.from(context).inflate(R.layout.item_menu, parent, false)
            holder = Holder()
            holder.title = view.findViewById(R.id.menuEntry)
            holder.title.text = text
            holder.cardView = view.findViewById(R.id.menuCardView)
            holder.iconMenu = view.findViewById(R.id.iconMenu)

            try {
                holder.iconMenu.setImageResource(item.data)
            } catch (e: Exception) {
                Log.i("Petal", "Exception:$e")
            }

            if (sp.getString("showFilterDialogX", "false") == "true") {
                if (text == sp.getString("icon_01", context.resources.getString(R.string.color_red))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.red, null))
                else if (text == sp.getString("icon_02", context.resources.getString(R.string.color_pink))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.pink, null))
                else if (text == sp.getString("icon_03", context.resources.getString(R.string.color_purple))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
                else if (text == sp.getString("icon_04", context.resources.getString(R.string.color_blue))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.blue, null))
                else if (text == sp.getString("icon_05", context.resources.getString(R.string.color_teal))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.teal, null))
                else if (text == sp.getString("icon_06", context.resources.getString(R.string.color_green))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.green, null))
                else if (text == sp.getString("icon_07", context.resources.getString(R.string.color_lime))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.lime, null))
                else if (text == sp.getString("icon_08", context.resources.getString(R.string.color_yellow))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.yellow, null))
                else if (text == sp.getString("icon_09", context.resources.getString(R.string.color_orange))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.orange, null))
                else if (text == sp.getString("icon_10", context.resources.getString(R.string.color_brown))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.brown, null))
                else if (text == sp.getString("icon_11", context.resources.getString(R.string.color_grey))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.grey, null))
            } else {
                holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.color_white, null))
            }

            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as Holder
            val item = list[position]
            val text = item.title
            holder.title.text = text

            try {
                holder.iconMenu.setImageResource(item.data)
            } catch (e: Exception) {
                Log.i("Petal", "Exception:$e")
            }

            if (sp.getString("showFilterDialogX", "false") == "true") {
                if (text == sp.getString("icon_01", context.resources.getString(R.string.color_red))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.red, null))
                else if (text == sp.getString("icon_02", context.resources.getString(R.string.color_pink))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.pink, null))
                else if (text == sp.getString("icon_03", context.resources.getString(R.string.color_purple))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
                else if (text == sp.getString("icon_04", context.resources.getString(R.string.color_blue))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.blue, null))
                else if (text == sp.getString("icon_05", context.resources.getString(R.string.color_teal))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.teal, null))
                else if (text == sp.getString("icon_06", context.resources.getString(R.string.color_green))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.green, null))
                else if (text == sp.getString("icon_07", context.resources.getString(R.string.color_lime))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.lime, null))
                else if (text == sp.getString("icon_08", context.resources.getString(R.string.color_yellow))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.yellow, null))
                else if (text == sp.getString("icon_09", context.resources.getString(R.string.color_orange))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.orange, null))
                else if (text == sp.getString("icon_10", context.resources.getString(R.string.color_brown))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.brown, null))
                else if (text == sp.getString("icon_11", context.resources.getString(R.string.color_grey))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.grey, null))
            } else {
                holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.color_white, null))
            }
        }

        return view
    }

    private class Holder {
        lateinit var title: TextView
        lateinit var cardView: CardView
        lateinit var iconMenu: ImageView
    }
}
