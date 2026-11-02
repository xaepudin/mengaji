package com.quran.labs.androidquran.ayahsearch

/**
 * One ayah returned by the Lafzi style ayah search.
 *
 * [highlightRanges] are inclusive character ranges in [arabicText].
 */
data class AyahSearchResult(
  val ayahId: Int,
  val sura: Int,
  val ayah: Int,
  val arabicText: String,
  val highlightRanges: List<IntRange>,
  val relevance: Int
)
