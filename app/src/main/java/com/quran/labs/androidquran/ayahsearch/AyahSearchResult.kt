package com.quran.labs.androidquran.ayahsearch

/**
 * One ayah returned by the Lafzi style ayah search.
 *
 * [highlightRanges] are inclusive character ranges in [arabicText]. [translation] is optional and
 * is filled in by the screen when the user has a translation selected and the corresponding
 * preference is enabled.
 */
data class AyahSearchResult(
  val ayahId: Int,
  val sura: Int,
  val ayah: Int,
  val arabicText: String,
  val highlightRanges: List<IntRange>,
  val relevance: Int,
  val translation: String? = null
)
