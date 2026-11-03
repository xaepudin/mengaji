package com.quran.labs.androidquran.ui.collection

import android.content.Context
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData

/**
 * Formats an ayah range for collection lists and dialogs, for example
 * "Al-Fatihah · Ayah 1–7" or "Al-Fatihah 1 – Al-Baqarah 5".
 */
fun collectionRangeLabel(
  context: Context,
  quranDisplayData: QuranDisplayData,
  startSura: Int,
  startAyah: Int,
  endSura: Int,
  endAyah: Int
): String {
  val startName = quranDisplayData.getSuraName(context, startSura, false)
  return if (startSura == endSura) {
    if (startAyah == endAyah) {
      context.getString(R.string.collection_range_single, startName, startAyah)
    } else {
      context.getString(R.string.collection_range_ayahs, startName, startAyah, endAyah)
    }
  } else {
    val endName = quranDisplayData.getSuraName(context, endSura, false)
    context.getString(
      R.string.collection_range_suras,
      startName,
      startAyah,
      endName,
      endAyah
    )
  }
}

fun collectionRangeLabel(
  context: Context,
  quranDisplayData: QuranDisplayData,
  start: SuraAyah,
  end: SuraAyah
): String = collectionRangeLabel(
  context,
  quranDisplayData,
  start.sura,
  start.ayah,
  end.sura,
  end.ayah
)
