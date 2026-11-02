package com.quran.labs.androidquran.data

/** How hafiz mode reveals a page: ayah by ayah, or progressively from the page start. */
enum class HafizModeType(val key: String) {
  /** Reveal each ayah on its own. */
  AYAH("ayah"),

  /** Reveal from the first ayah of the page towards the end of the page. */
  PAGE("page");

  companion object {
    @JvmStatic
    fun fromKey(key: String?): HafizModeType =
      entries.firstOrNull { it.key == key } ?: PAGE
  }
}
