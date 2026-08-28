package com.petal.browser.view

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.petal.browser.R

class AdapterProfileList(
    private val context: Context,
    private val list: List<String>
) : ArrayAdapter<String>(context, R.layout.item_list, list) {

    private val layoutResId: Int = R.layout.item_menu

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: Holder
        val view: View
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(layoutResId, parent, false)
            holder = Holder(view.findViewById(R.id.menuEntry))
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as Holder
        }
        holder.domain.text = list[position]
        return view
    }

    private class Holder(val domain: TextView)
}
