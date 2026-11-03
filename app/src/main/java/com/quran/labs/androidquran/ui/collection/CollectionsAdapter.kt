package com.quran.labs.androidquran.ui.collection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.R
import com.quran.mobile.collection.model.AyahCollection

class CollectionsAdapter(
  private val onClick: (AyahCollection) -> Unit,
  private val onLongClick: (AyahCollection) -> Unit
) : ListAdapter<AyahCollection, CollectionsAdapter.ViewHolder>(Diff) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_collection, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val collection = getItem(position)
    holder.name.text = collection.name
    holder.count.text = holder.itemView.context.resources.getQuantityString(
      R.plurals.collection_entry_count,
      collection.entryCount,
      collection.entryCount
    )
    holder.itemView.setOnClickListener { onClick(collection) }
    holder.itemView.setOnLongClickListener {
      onLongClick(collection)
      true
    }
  }

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(R.id.collection_name)
    val count: TextView = itemView.findViewById(R.id.collection_count)
  }

  private object Diff : DiffUtil.ItemCallback<AyahCollection>() {
    override fun areItemsTheSame(oldItem: AyahCollection, newItem: AyahCollection): Boolean {
      return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AyahCollection, newItem: AyahCollection): Boolean {
      return oldItem == newItem
    }
  }
}
