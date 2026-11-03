package com.quran.labs.androidquran.ayahsearch

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.util.TypefaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AyahSearchAdapter(
  private val context: Context,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val pageSnippetLoader: PageImageSnippetLoader,
  private val scope: CoroutineScope,
  private val onResultClick: (AyahSearchResult) -> Unit
) : RecyclerView.Adapter<AyahSearchAdapter.ViewHolder>() {

  private val items = ArrayList<AyahSearchResult>()
  private val highlightColor =
    ContextCompat.getColor(context, R.color.ayah_search_highlight)
  private val quranTypeface by lazy { TypefaceManager.getUthmaniTypeface(context) }
  private val cardContentWidth: Int by lazy {
    context.resources.displayMetrics.widthPixels - dp(CARD_HORIZONTAL_INSET_DP)
  }

  fun submitItems(results: List<AyahSearchResult>) {
    items.clear()
    items.addAll(results)
    notifyDataSetChanged()
  }

  fun clear() {
    if (items.isEmpty()) {
      return
    }
    items.clear()
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(context)
      .inflate(R.layout.item_ayah_search_result, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val result = items[position]
    val page = quranInfo.getPageFromSuraAyah(result.sura, result.ayah)
    holder.metadata.text = context.getString(
      R.string.ayah_search_result_meta,
      result.sura,
      quranDisplayData.getSuraName(context, result.sura, false),
      result.ayah,
      page
    )

    // Show the font-rendered ayah while the page image crop loads, and keep it as a fallback if
    // the page image or its ayah coordinates have not been downloaded.
    holder.arabic.visibility = View.VISIBLE
    holder.arabic.typeface = quranTypeface
    holder.arabic.text = buildArabicText(result)
    holder.arabicImage.visibility = View.GONE
    holder.arabicImage.setImageDrawable(null)
    holder.arabicImage.colorFilter = null
    holder.arabicImage.contentDescription = null

    val imageKey = "$page:${result.sura}:${result.ayah}"
    holder.arabicImage.tag = imageKey
    scope.launch {
      val bitmap = pageSnippetLoader.load(
        page = page,
        sura = result.sura,
        ayah = result.ayah,
        targetWidth = cardContentWidth
      )
      if (bitmap != null && holder.arabicImage.tag == imageKey) {
        holder.arabicImage.setImageBitmap(bitmap)
        holder.arabicImage.colorFilter = pageSnippetLoader.nightModeFilter(holder.itemView.context)
        holder.arabicImage.contentDescription = result.arabicText
        holder.arabicImage.visibility = View.VISIBLE
        holder.arabic.visibility = View.GONE
      }
    }

    val translation = result.translation
    if (translation.isNullOrEmpty()) {
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

  override fun getItemCount(): Int = items.size

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

  private fun dp(value: Int): Int {
    return (value * context.resources.displayMetrics.density).toInt()
  }

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val metadata: TextView = itemView.findViewById(R.id.result_metadata)
    val arabic: TextView = itemView.findViewById(R.id.result_arabic)
    val arabicImage: ImageView = itemView.findViewById(R.id.result_arabic_image)
    val translation: TextView = itemView.findViewById(R.id.result_translation)
  }

  private companion object {
    // 16dp card margins on both sides + 16dp inner padding on both sides.
    const val CARD_HORIZONTAL_INSET_DP = 64
  }
}
