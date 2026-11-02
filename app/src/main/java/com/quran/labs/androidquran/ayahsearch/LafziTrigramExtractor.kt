package com.quran.labs.androidquran.ayahsearch

/**
 * A trigram and the (zero based) positions where it occurs in the normalised query.
 *
 * For a three character query Lafzi uses position 1, matching the bundled index format.
 */
data class LafziTrigram(
  val frequency: Int,
  val positions: List<Int>
)

/**
 * Port of Lafzi's `TrigramUtil.extractTrigramFrequencyAndPosition` (lafzi-android, GPL-3.0).
 */
object LafziTrigramExtractor {

  fun extract(text: String): Map<String, LafziTrigram> {
    if (text.length < 3) {
      return emptyMap()
    }
    if (text.length == 3) {
      return mapOf(text to LafziTrigram(frequency = 1, positions = listOf(1)))
    }

    val results = HashMap<String, LafziTrigram>()
    for (index in 0..text.length - 3) {
      val trigram = text.substring(index, index + 3)
      val previous = results[trigram]
      results[trigram] = if (previous == null) {
        LafziTrigram(frequency = 1, positions = listOf(index))
      } else {
        LafziTrigram(
          frequency = previous.frequency + 1,
          positions = previous.positions + index
        )
      }
    }
    return results
  }
}
