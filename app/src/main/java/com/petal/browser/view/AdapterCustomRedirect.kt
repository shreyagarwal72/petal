package com.petal.browser.view

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.petal.browser.R
import com.petal.browser.objects.CustomRedirect
import com.petal.browser.objects.CustomRedirectsHelper
import com.petal.browser.unit.HelperUnit
import org.json.JSONException
import java.util.ArrayList

class AdapterCustomRedirect(
    val redirects: ArrayList<CustomRedirect>,
    private val context: Context
) : RecyclerView.Adapter<RedirectsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RedirectsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_checkbox, parent, false)
        return RedirectsViewHolder(view)
    }

    override fun onBindViewHolder(holder: RedirectsViewHolder, position: Int) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val current = redirects[position]
        val source = holder.itemView.findViewById<TextView>(R.id.titleView)
        val target = holder.itemView.findViewById<TextView>(R.id.dateView)
        val remove = holder.itemView.findViewById<ImageView>(R.id.item_icon)
        val edit = holder.itemView.findViewById<ImageView>(R.id.edit_icon)
        val checkBox = holder.itemView.findViewById<CheckBox>(R.id.checkBox)
        val cardView = holder.itemView.findViewById<CardView>(R.id.item_CardViewItem)

        edit.visibility = View.VISIBLE
        remove.visibility = View.VISIBLE
        checkBox.visibility = View.GONE
        cardView.useCompatPadding = true

        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        remove.setBackgroundResource(outValue.resourceId)
        edit.setBackgroundResource(outValue.resourceId)

        source.text = current.source
        target.text = current.target

        edit.setOnClickListener {
            val builderEditCustom = MaterialAlertDialogBuilder(context)
            val dialogViewEditCustom = View.inflate(context, R.layout.create_new_redirects, null)
            val sourceEdit = dialogViewEditCustom.findViewById<TextInputEditText>(R.id.source)
            val targetEdit = dialogViewEditCustom.findViewById<TextInputEditText>(R.id.target)
            sourceEdit.setText(current.source)
            targetEdit.setText(current.target)

            builderEditCustom.setTitle(R.string.custom_redirects_title)
            builderEditCustom.setIcon(R.drawable.icon_preview)
            builderEditCustom.setPositiveButton(R.string.app_cancel, null)
            builderEditCustom.setNegativeButton(R.string.app_ok) { _, _ ->
                val sourceText = sourceEdit.text?.toString() ?: ""
                val targetText = targetEdit.text?.toString() ?: ""
                if (targetText.isEmpty() || sourceText.isEmpty()) return@setNegativeButton

                editRedirect(position, CustomRedirect(sourceText, targetText))
                try {
                    CustomRedirectsHelper.saveRedirects(redirects)
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }
            }
            builderEditCustom.setView(dialogViewEditCustom)
            val dialogCustomRedirectsEdit: AlertDialog = builderEditCustom.create()
            dialogCustomRedirectsEdit.show()
            HelperUnit.setupDialog(context, dialogCustomRedirectsEdit)
        }

        remove.setOnClickListener {
            val removedItem = redirects[holder.adapterPosition]
            val removedPosition = holder.adapterPosition

            removeRedirect(removedPosition)
            try {
                CustomRedirectsHelper.saveRedirects(redirects)
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }

            Snackbar.make(holder.itemView, R.string.deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                    restoreRedirect(removedPosition, removedItem)
                    try {
                        CustomRedirectsHelper.saveRedirects(redirects)
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

    private fun editRedirect(position: Int, redirect: CustomRedirect) {
        redirects[position] = redirect
        notifyItemChanged(position)
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
