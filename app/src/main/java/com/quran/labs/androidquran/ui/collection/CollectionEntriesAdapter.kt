package com.quran.labs.androidquran.ui.collection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.collection.model.AyahCollectionEntry

class CollectionEntriesAdapter(
  private val quranDisplayData: QuranDisplayData,
  private val onClick: (AyahCollectionEntry) -> Unit,
  private val onRename: (AyahCollectionEntry) -> Unit,
  private val onDelete: (AyahCollectionEntry) -> Unit
) : RecyclerView.Adapter<CollectionEntriesAdapter.ViewHolder>() {

  private val items = ArrayList<AyahCollectionEntry>()

  fun submitList(entries: List<AyahCollectionEntry>) {
    items.clear()
    items.addAll(entries)
    notifyDataSetChanged()
  }

  fun moveItem(from: Int, to: Int) {
    if (from < 0 || to < 0 || from >= items.size || to >= items.size) {
      return
    }
    val item = items.removeAt(from)
    items.add(to, item)
    notifyItemMoved(from, to)
  }

  fun currentIds(): List<Long> = items.map { it.id }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_collection_entry, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val entry = items[position]
    holder.number.text = QuranUtils.getLocalizedNumber(position + 1)
    holder.name.text = entry.name
    holder.range.text = collectionRangeLabel(
      holder.itemView.context,
      quranDisplayData,
      entry.start,
      entry.end
    )
    holder.itemView.setOnClickListener { onClick(entry) }
    holder.options.setOnClickListener { anchor ->
      PopupMenu(anchor.context, anchor).apply {
        inflate(R.menu.collection_entry_menu)
        setOnMenuItemClickListener { item ->
          when (item.itemId) {
            R.id.action_rename_entry -> {
              onRename(entry)
              true
            }

            R.id.action_delete_entry -> {
              onDelete(entry)
              true
            }

            else -> false
          }
        }
        show()
      }
    }
  }

  override fun getItemCount(): Int = items.size

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val number: TextView = itemView.findViewById(R.id.entry_number)
    val name: TextView = itemView.findViewById(R.id.entry_name)
    val range: TextView = itemView.findViewById(R.id.entry_range)
    val options: ImageButton = itemView.findViewById(R.id.entry_options)
  }
}
