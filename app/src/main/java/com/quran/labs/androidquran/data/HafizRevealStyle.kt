package com.quran.labs.androidquran.data

/** What a tap or a long press does to a covered ayah while hafiz mode is on. */
enum class HafizRevealStyle(val key: String) {
  /** Reveal the ayah (or the next ayah / everything up to the chosen ayah in per page mode). */
  REVEAL("reveal"),

  /** Reveal one more word with every gesture. */
  WORD_BY_WORD("word"),

  /** Reveal word by word while the finger is held down. */
  WORD_BY_WORD_CONTINUOUS("continuous");

  companion object {
    @JvmStatic
    @JvmOverloads
    fun fromKey(key: String?, default: HafizRevealStyle = REVEAL): HafizRevealStyle =
      entries.firstOrNull { it.key == key } ?: default
  }
}
