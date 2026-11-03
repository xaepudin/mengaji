package com.quran.labs.androidquran.ayahsearch

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
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
import com.quran.labs.androidquran.util.QuranUtils

class AyahSearchAdapter(
  private val context: Context,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val showPageNumber: Boolean = true,
  private val onResultClick: (AyahSearchResult) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private val items = ArrayList<AyahSearchResult>()
  private var groupHeaders: Map<Int, AyahGroupHeader> = emptyMap()
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

  var showContinuous: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        rebuildRows()
        notifyDataSetChanged()
      }
    }

  fun submitItems(
    results: List<AyahSearchResult>,
    groupHeaders: Map<Int, AyahGroupHeader> = emptyMap()
  ) {
    items.clear()
    items.addAll(results)
    this.groupHeaders = groupHeaders
    rebuildRows()
    notifyDataSetChanged()
  }

  fun clear() {
    if (items.isEmpty()) {
      return
    }
    items.clear()
    groupHeaders = emptyMap()
    rows = emptyList()
    notifyDataSetChanged()
  }

  override fun getItemCount(): Int = rows.size

  override fun getItemViewType(position: Int): Int {
    return when (rows[position]) {
      is Row.GroupHeader -> TYPE_GROUP_HEADER
      is Row.Continuous -> TYPE_CONTINUOUS
      is Row.Result -> TYPE_RESULT
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(context)
    return when (viewType) {
      TYPE_GROUP_HEADER ->
        GroupHeaderViewHolder(
          inflater.inflate(R.layout.item_collection_reading_group_header, parent, false)
        )

      TYPE_CONTINUOUS ->
        ContinuousViewHolder(
          inflater.inflate(R.layout.item_collection_reading_continuous, parent, false)
        )

      else -> ViewHolder(inflater.inflate(R.layout.item_ayah_search_result, parent, false))
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val row = rows[position]) {
      is Row.Result -> if (holder is ViewHolder) bindResult(holder, row.result)
      is Row.GroupHeader -> if (holder is GroupHeaderViewHolder) bindGroupHeader(holder, row.header)
      is Row.Continuous -> if (holder is ContinuousViewHolder) bindContinuous(holder, row.results)
    }
  }

  private fun bindGroupHeader(holder: GroupHeaderViewHolder, header: AyahGroupHeader) {
    holder.name.text = header.name
    if (header.name == header.range) {
      holder.range.visibility = View.GONE
    } else {
      holder.range.visibility = View.VISIBLE
      holder.range.text = header.range
    }
  }

  private fun bindContinuous(holder: ContinuousViewHolder, results: List<AyahSearchResult>) {
    val builder = SpannableStringBuilder()
    results.forEachIndexed { index, result ->
      if (index > 0) {
        builder.append(" ")
      }
      builder.append(result.arabicText)
      builder.append(" ﴿")
      builder.append(QuranUtils.getLocalizedNumber(result.ayah))
      builder.append("﴾ ")
    }
    holder.arabic.typeface = quranTypeface
    holder.arabic.text = builder

    if (!showTranslation) {
      holder.translation.visibility = View.GONE
    } else {
      val translations = SpannableStringBuilder()
      for (result in results) {
        val translation = result.translation ?: continue
        if (translations.isNotEmpty()) {
          translations.append("\n\n")
        }
        translations.append("${result.sura}:${result.ayah} ")
        translations.append(
          HtmlCompat.fromHtml(translation, HtmlCompat.FROM_HTML_MODE_COMPACT)
        )
      }
      if (translations.isEmpty()) {
        holder.translation.visibility = View.GONE
      } else {
        holder.translation.visibility = View.VISIBLE
        holder.translation.text = translations
      }
    }

    holder.itemView.setOnClickListener {
      results.firstOrNull()?.let(onResultClick)
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
      if (showContinuous) {
        var group = mutableListOf<AyahSearchResult>()
        fun flushGroup() {
          if (group.isNotEmpty()) {
            add(Row.Continuous(group.toList()))
            group = mutableListOf()
          }
        }
        items.forEachIndexed { index, result ->
          groupHeaders[index]?.let { header ->
            flushGroup()
            add(Row.GroupHeader(header))
          }
          group.add(result)
        }
        flushGroup()
      } else {
        items.forEachIndexed { index, result ->
          groupHeaders[index]?.let { header -> add(Row.GroupHeader(header)) }
          add(Row.Result(result))
        }
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

  private class GroupHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(R.id.reading_group_name)
    val range: TextView = itemView.findViewById(R.id.reading_group_range)
  }

  private class ContinuousViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val arabic: TextView = itemView.findViewById(R.id.continuous_arabic)
    val translation: TextView = itemView.findViewById(R.id.continuous_translation)
  }

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val metadata: TextView = itemView.findViewById(R.id.result_metadata)
    val arabic: TextView = itemView.findViewById(R.id.result_arabic)
    val translation: TextView = itemView.findViewById(R.id.result_translation)
  }

  private sealed interface Row {
    data class Result(val result: AyahSearchResult) : Row
    data class GroupHeader(val header: AyahGroupHeader) : Row
    data class Continuous(val results: List<AyahSearchResult>) : Row
  }

  private companion object {
    const val TYPE_RESULT = 0
    const val TYPE_GROUP_HEADER = 1
    const val TYPE_CONTINUOUS = 2
  }
}
