package com.quran.labs.androidquran.ayahsearch

/**
 * Arabic text for one ayah, loaded from the bundled Lafzi database for the collection reading
 * screen.
 */
data class AyahText(
  val ayahId: Int,
  val sura: Int,
  val ayah: Int,
  val text: String
)
