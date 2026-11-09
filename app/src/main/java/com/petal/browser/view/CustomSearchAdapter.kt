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
import com.petal.browser.R
import com.petal.browser.engine.gecko.PetalEngineBridge
import com.petal.browser.objects.CustomRedirect
import com.petal.browser.objects.CustomSearchesHelper
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBrowserUtils
import java.util.ArrayList

class CustomSearchAdapter(
    private val context: Context,
    private val url: String,
    private val redirects: ArrayList<CustomRedirect>
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

        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true)
        val color = typedValue.data
        val albumCardView = holder.itemView.findViewById<CardView>(R.id.item_CardViewItem)
        albumCardView?.setCardBackgroundColor(color)
        val cardView = holder.itemView.findViewById<CardView>(R.id.item_cardView)
        cardView?.visibility = View.GONE

        remove?.visibility = View.VISIBLE
        source?.text = current.source
        target?.text = current.target
        PetalAndroidUtils.setHighLightedText(context, target, current.target, PetalAndroidUtils.domain(current.target))

        remove?.setOnClickListener {
            PetalToast.show(context, context.getString(R.string.hint_database), PetalToast.LENGTH_SHORT, context.getString(R.string.app_ok)) {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos < redirects.size) {
                    redirects.removeAt(currentPos)
                    notifyItemRemoved(currentPos)
                    notifyItemRangeChanged(currentPos, itemCount)
                    try {
                        CustomSearchesHelper.saveRedirects(redirects)
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            }
        }

        holder.itemView.setOnClickListener {
            val controller = PetalEngineBridge.getBrowserController()
            controller?.hideSearch()
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit().putString("sp_search_customSearches", current.target).apply()
            val t = PetalBrowserUtils.queryWrapper(context, url)
            if (PetalBrowserUtils.isURL(t)) {
                PetalBrowserUtils.intentURL(context, Uri.parse(t))
                sp.edit().putString("sp_search_customSearches", "").apply()
            } else {
                PetalToast.show(context, R.string.app_error)
            }
        }
    }

    override fun getItemCount(): Int = redirects.size

    fun getRedirects(): ArrayList<CustomRedirect> = redirects

    fun addRedirect(redirect: CustomRedirect) {
        redirects.add(redirect)
        notifyItemInserted(itemCount - 1)
    }
}
