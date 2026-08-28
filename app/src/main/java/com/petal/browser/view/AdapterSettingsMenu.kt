package com.petal.browser.view

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.petal.browser.R

class AdapterSettingsMenu(private val itemList: List<MenuItem>) : RecyclerView.Adapter<AdapterSettingsMenu.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itemList[position]

        holder.textView.text = item.title
        holder.imageView.setImageResource(item.iconResId)

        // 1. Remove old listeners before setting state
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.itemView.setOnClickListener(null)

        // 2. Set visual state
        holder.checkBox.isChecked = item.isSelected
        if (item.isSelected) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD")) // Soft Blue
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE)
        }

        // 3. Listener for CheckBox
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            item.isSelected = isChecked
            if (isChecked) {
                holder.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
            } else {
                holder.cardView.setCardBackgroundColor(Color.WHITE)
            }
        }

        // 4. Click whole card to toggle checkbox
        holder.itemView.setOnClickListener {
            holder.checkBox.toggle()
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.titleView)
        val imageView: ImageView = itemView.findViewById(R.id.item_icon)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        val cardView: CardView = itemView.findViewById(R.id.item_CardViewItem)
    }
}
