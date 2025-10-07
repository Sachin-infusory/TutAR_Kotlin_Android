// ModelBrowserAdapter.kt - Updated with standard Android icons
package com.infusory.tutarapp.ui.models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.infusory.tutarapp.R
import java.io.File

class ModelBrowserAdapter(
    private val onItemClick: (BrowserItem) -> Unit
) : RecyclerView.Adapter<ModelBrowserAdapter.ViewHolder>() {

    private var items = listOf<BrowserItem>()
    private var currentPath = ""

    fun updateItems(newItems: List<BrowserItem>, path: String = "") {
        items = newItems
        currentPath = path
        notifyDataSetChanged()
    }

    fun getCurrentPath(): String = currentPath

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_browser, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.tv_title)
        private val subtitleText: TextView = itemView.findViewById(R.id.tv_subtitle)
        private val iconImage: ImageView = itemView.findViewById(R.id.iv_icon)
        private val thumbnailImage: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val arrowImage: ImageView = itemView.findViewById(R.id.iv_arrow)

        fun bind(item: BrowserItem) {
            titleText.text = item.title
            subtitleText.text = item.subtitle
            subtitleText.visibility = if (item.subtitle.isNotEmpty()) View.VISIBLE else View.GONE

            when (item.type) {
                BrowserItemType.CLASS -> {
                    iconImage.setImageResource(android.R.drawable.ic_menu_my_calendar) // School/Class icon
                    iconImage.visibility = View.VISIBLE
                    thumbnailImage.visibility = View.GONE
                    arrowImage.visibility = View.VISIBLE
                    arrowImage.setImageResource(android.R.drawable.ic_media_play)
                }
                BrowserItemType.SUBJECT -> {
                    iconImage.setImageResource(android.R.drawable.ic_menu_agenda) // Book/Subject icon
                    iconImage.visibility = View.VISIBLE
                    thumbnailImage.visibility = View.GONE
                    arrowImage.visibility = View.VISIBLE
                    arrowImage.setImageResource(android.R.drawable.ic_media_play)
                }
                BrowserItemType.TOPIC -> {
                    iconImage.setImageResource(android.R.drawable.ic_menu_info_details) // Topic icon
                    iconImage.visibility = View.VISIBLE
                    thumbnailImage.visibility = View.GONE
                    arrowImage.visibility = View.VISIBLE
                    arrowImage.setImageResource(android.R.drawable.ic_media_play)
                }
                BrowserItemType.MODEL -> {
                    iconImage.visibility = View.GONE
                    thumbnailImage.visibility = View.VISIBLE
                    arrowImage.visibility = View.GONE

                    // Simple thumbnail handling without Glide dependency
                    thumbnailImage.setImageResource(android.R.drawable.ic_menu_gallery)

                    // If you want to load actual thumbnails, uncomment this code and add Glide dependency
                    /*
                    val thumbnailPath = File(itemView.context.getExternalFilesDir("models"), item.thumbnailPath)
                    if (thumbnailPath.exists()) {
                        // You'll need to add Glide dependency: implementation 'com.github.bumptech.glide:glide:4.16.0'
                        Glide.with(itemView.context)
                            .load(thumbnailPath)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .centerCrop()
                            .into(thumbnailImage)
                    } else {
                        thumbnailImage.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                    */
                }
                BrowserItemType.BACK -> {
                    iconImage.setImageResource(android.R.drawable.ic_menu_revert)
                    iconImage.visibility = View.VISIBLE
                    thumbnailImage.visibility = View.GONE
                    arrowImage.visibility = View.GONE
                }
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

data class BrowserItem(
    val type: BrowserItemType,
    val title: String,
    val subtitle: String = "",
    val modelPath: String = "",
    val thumbnailPath: String = "",
    val data: Any? = null // Can hold ClassData, SubjectData, TopicData, or ModelData
)

enum class BrowserItemType {
    CLASS, SUBJECT, TOPIC, MODEL, BACK
}