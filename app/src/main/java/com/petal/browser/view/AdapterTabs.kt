package com.petal.browser.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.petal.browser.R
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.browser.BrowserController
import com.petal.browser.unit.HelperUnit

class AdapterTabs(
    private val context: Context,
    private val albumController: AlbumController,
    private var browserController: BrowserController?
) {
    var albumView: View? = null
        private set
    private lateinit var albumTitle: TextView
    private lateinit var albumUrl: TextView
    private lateinit var albumCardView: MaterialCardView

    init {
        initUI()
    }

    val url: Any
        get() = albumUrl.text.toString()

    fun setAlbumTitle(title: String?, url: String?) {
        val displayTitle = if (title.isNullOrEmpty() || title.equals("about:blank", ignoreCase = true) || title.equals("Petal Start", ignoreCase = true)) "Petal Home" else title
        val displayUrl = if (url.isNullOrEmpty() || url.equals("about:blank", ignoreCase = true)) "Petal Home" else url
        albumTitle.text = displayTitle
        albumUrl.text = displayUrl
        HelperUnit.setHighLightedText(context, albumUrl, displayUrl, HelperUnit.domain(displayUrl))
    }

    fun setBrowserController(browserController: BrowserController?) {
        this.browserController = browserController
    }

    @SuppressLint("InflateParams")
    private fun initUI() {
        val view = LayoutInflater.from(context).inflate(R.layout.item_list, null, false)
        albumView = view
        albumCardView = view.findViewById(R.id.item_CardViewItem)
        albumTitle = view.findViewById(R.id.titleView)
        albumUrl = view.findViewById(R.id.dateView)
        val albumClose = view.findViewById<ImageView>(R.id.item_icon)
        albumClose.visibility = View.VISIBLE
        albumClose.setImageResource(R.drawable.icon_close)

        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        albumClose.setBackgroundResource(outValue.resourceId)

        albumTitle.typeface = Typeface.DEFAULT_BOLD

        albumClose.setOnClickListener {
            val list = BrowserContainer.list()
            if (list.isEmpty()) {
                browserController?.hideOverview()
            } else {
                browserController?.removeAlbum(albumController)
            }
        }

        view.setOnClickListener {
            browserController?.showAlbum(albumController, false, false, true)
            browserController?.hideOverview()
        }

        view.setOnLongClickListener {
            browserController?.removeAlbum(albumController)
            true
        }
    }
}
