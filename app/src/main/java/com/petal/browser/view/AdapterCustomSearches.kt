package com.petal.browser.view

import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.objects.CustomRedirect
import com.petal.browser.objects.CustomSearchesHelper
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import org.json.JSONException
import java.util.ArrayList

class AdapterCustomSearches(
    private val context: Context,
    private val url: String,
    val redirects: ArrayList<CustomRedirect>
) : RecyclerView.Adapter<RedirectsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RedirectsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
        return RedirectsViewHolder(view)
    }

    override fun onBindViewHolder(holder: RedirectsViewHolder, position: Int) {
        val current = redirects[position]
        val source = holder.itemView.findViewById<TextView>(R.id.titleView)
        val target = holder.itemView.findViewById<TextView>(R.id.dateView)
        val remove = holder.itemView.findViewById<ImageView>(R.id.iconView)
        val image = holder.itemView.findViewById<ImageView>(R.id.item_icon)
        val cardView = holder.itemView.findViewById<CardView>(R.id.item_CardViewItem)

        remove.visibility = View.VISIBLE
        cardView.useCompatPadding = true

        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        remove.setBackgroundResource(outValue.resourceId)

        source.text = current.source
        val targetUrl = current.target
        target.text = targetUrl
        HelperUnit.setHighLightedText(context, target, targetUrl, HelperUnit.domain(targetUrl))

        image.setImageResource(R.drawable.icon_search)

        holder.itemView.setOnClickListener {
            val encodedQuery = BrowserUnit.urlEncode(url)
            val searchUrl = current.target + encodedQuery
            (context as BrowserActivity).updateAlbum(searchUrl)
            if (context.dialogOverview != null) {
                context.dialogOverview.cancel()
            }
        }

        remove.setOnClickListener {
            val removedItem = redirects[holder.adapterPosition]
            val removedPosition = holder.adapterPosition

            removeRedirect(removedPosition)
            try {
                CustomSearchesHelper.saveRedirects(redirects)
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }

            Snackbar.make(holder.itemView, R.string.deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                    restoreRedirect(removedPosition, removedItem)
                    try {
                        CustomSearchesHelper.saveRedirects(redirects)
                    } catch (e: JSONException) {
                        throw RuntimeException(e)
                    }
                }.show()
        }
    }

    fun addRedirect(redirect: CustomRedirect) {
        redirects.add(redirect)
        notifyItemInserted(redirects.size - 1)
    }

    private fun removeRedirect(position: Int) {
        redirects.removeAt(position)
        notifyItemRemoved(position)
    }

    private fun restoreRedirect(position: Int, redirect: CustomRedirect) {
        redirects.add(position, redirect)
        notifyItemInserted(position)
    }

    override fun getItemCount(): Int {
        return redirects.size
    }
}
