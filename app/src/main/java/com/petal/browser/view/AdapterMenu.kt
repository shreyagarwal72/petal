package com.petal.browser.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.petal.browser.R

class AdapterMenu(
    private val gridItems: List<MenuItem>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<AdapterMenu.ViewHolder>() {

    fun interface OnItemClickListener {
        fun onItemClick(item: MenuItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_overflow, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = gridItems[position]
        holder.titleTextView.text = item.title
        holder.iconImageView.setImageResource(item.iconResId)
        holder.itemView.setOnClickListener { listener.onItemClick(item) }
    }

    override fun getItemCount(): Int {
        return gridItems.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.gridIcon)
        val titleTextView: TextView = itemView.findViewById(R.id.gridTitle)
    }
}
