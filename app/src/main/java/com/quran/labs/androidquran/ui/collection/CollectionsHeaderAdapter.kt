package com.quran.labs.androidquran.ui.collection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.R

/** Header for the collections section in the Bookmarks tab. */
class CollectionsHeaderAdapter(
  private val onCreateCollection: () -> Unit
) : RecyclerView.Adapter<CollectionsHeaderAdapter.ViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_collections_header, parent, false)
    return ViewHolder(view, onCreateCollection)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit

  override fun getItemCount(): Int = 1

  class ViewHolder(itemView: View, onCreateCollection: () -> Unit) :
    RecyclerView.ViewHolder(itemView) {
    init {
      itemView.findViewById<View>(R.id.collections_header_add).setOnClickListener {
        onCreateCollection()
      }
    }
  }
}
