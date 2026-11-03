package com.quran.labs.androidquran.ayahsearch

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.util.TypefaceManager

class AyahSearchAdapter(
  private val context: Context,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val showPageNumber: Boolean = true,
  private val onResultClick: (AyahSearchResult) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private val items = ArrayList<AyahSearchResult>()
  private var dividerBefore: List<Boolean> = emptyList()
  private var rows: List<Row> = emptyList()
  private val highlightColor =
    ContextCompat.getColor(context, R.color.ayah_search_highlight)
  private val quranTypeface by lazy { TypefaceManager.getMeQuranTypeface(context) }

  var showTranslation: Boolean = true
    set(value) {
      if (field != value) {
        field = value
        notifyDataSetChanged()
      }
    }

  var showEntryDividers: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        rebuildRows()
        notifyDataSetChanged()
      }
    }

  fun submitItems(results: List<AyahSearchResult>, dividerBefore: List<Boolean> = emptyList()) {
    items.clear()
    items.addAll(results)
    this.dividerBefore = dividerBefore
    rebuildRows()
    notifyDataSetChanged()
  }

  fun clear() {
    if (items.isEmpty()) {
      return
    }
    items.clear()
    dividerBefore = emptyList()
    rows = emptyList()
    notifyDataSetChanged()
  }

  override fun getItemCount(): Int = rows.size

  override fun getItemViewType(position: Int): Int {
    return if (rows[position] is Row.Divider) TYPE_DIVIDER else TYPE_RESULT
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(context)
    return if (viewType == TYPE_DIVIDER) {
      DividerViewHolder(inflater.inflate(R.layout.item_collection_reading_divider, parent, false))
    } else {
      ViewHolder(inflater.inflate(R.layout.item_ayah_search_result, parent, false))
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    val row = rows[position]
    if (row is Row.Result && holder is ViewHolder) {
      bindResult(holder, row.result)
    }
  }

  private fun bindResult(holder: ViewHolder, result: AyahSearchResult) {
    val page = quranInfo.getPageFromSuraAyah(result.sura, result.ayah)
    holder.metadata.text = if (showPageNumber) {
      context.getString(
        R.string.ayah_search_result_meta,
        result.sura,
        quranDisplayData.getSuraName(context, result.sura, false),
        result.ayah,
        page
      )
    } else {
      context.getString(
        R.string.ayah_search_result_meta_no_page,
        result.sura,
        quranDisplayData.getSuraName(context, result.sura, false),
        result.ayah
      )
    }

    holder.arabic.typeface = quranTypeface
    holder.arabic.text = buildArabicText(result)

    val translation = result.translation
    if (!showTranslation || translation.isNullOrEmpty()) {
      holder.translation.visibility = View.GONE
    } else {
      holder.translation.visibility = View.VISIBLE
      holder.translation.text = HtmlCompat.fromHtml(
        translation,
        HtmlCompat.FROM_HTML_MODE_COMPACT
      )
    }

    holder.itemView.setOnClickListener { onResultClick(result) }
  }

  private fun rebuildRows() {
    rows = buildList {
      items.forEachIndexed { index, result ->
        if (showEntryDividers && dividerBefore.getOrElse(index) { false }) {
          add(Row.Divider)
        }
        add(Row.Result(result))
      }
    }
  }

  private fun buildArabicText(result: AyahSearchResult): CharSequence {
    val text = result.arabicText
    if (text.isEmpty() || result.highlightRanges.isEmpty()) {
      return text
    }

    val lastIndex = text.length - 1
    val spannable = SpannableString(text)
    for (range in result.highlightRanges) {
      val start = range.first.coerceIn(0, lastIndex)
      val end = range.last.coerceIn(0, lastIndex)
      if (start <= end) {
        spannable.setSpan(
          BackgroundColorSpan(highlightColor),
          start,
          end + 1,
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
      }
    }
    return spannable
  }

  private class DividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val metadata: TextView = itemView.findViewById(R.id.result_metadata)
    val arabic: TextView = itemView.findViewById(R.id.result_arabic)
    val translation: TextView = itemView.findViewById(R.id.result_translation)
  }

  private sealed interface Row {
    data class Result(val result: AyahSearchResult) : Row
    data object Divider : Row
  }

  private companion object {
    const val TYPE_RESULT = 0
    const val TYPE_DIVIDER = 1
  }
}
