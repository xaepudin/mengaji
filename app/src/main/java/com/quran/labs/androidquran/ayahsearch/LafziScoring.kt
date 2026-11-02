package com.quran.labs.androidquran.ayahsearch

/**
 * Scoring helpers ported from Lafzi's `SearchUtil` and `HighlightUtil` (lafzi-android, GPL-3.0).
 *
 * Lafzi ranks ayahs by finding the longest run of matched phonetic trigram positions and then
 * rewarding matches whose positions are close together. The same numbers are used here so that
 * results keep the ordering users expect from Lafzi.
 */
object LafziScoring {

  /**
   * Finds the longest contiguous (within [maxGap]) subsequence of [sequence].
   *
   * Port of `SearchUtil.longestContiguousSubsequence`. The input is not mutated.
   */
  fun longestContiguousSubsequence(sequence: List<Int>, maxGap: Int): List<Int> {
    if (sequence.isEmpty()) {
      return emptyList()
    }
    val sorted = sequence.sorted()
    val size = sorted.size
    var start = 0
    var length = 0
    var maxStart = 0
    var maxLength = 0

    for (index in 0 until size - 1) {
      if (sorted[index + 1] - sorted[index] > maxGap) {
        length = 0
        start = index + 1
      } else {
        length++
        if (length > maxLength) {
          maxLength = length
          maxStart = start
        }
      }
    }

    maxLength++
    val end = (maxStart + maxLength).coerceAtMost(size)
    return sorted.subList(maxStart, end)
  }

  /**
   * Port of `SearchUtil.reciprocalDiffAverage`: the closer the positions, the higher the score.
   */
  fun reciprocalDiffAverage(positions: List<Int>): Double {
    val size = positions.size
    if (size == 1) {
      return 1.0
    }
    if (size < 2) {
      return 0.0
    }

    var total = 0.0
    for (index in 0 until size - 1) {
      val diff = positions[index + 1] - positions[index]
      if (diff > 0) {
        total += 1.0 / diff
      }
    }
    return total / (size - 1)
  }

  /**
   * Port of `HighlightUtil.longestHighlightLookforward`.
   *
   * The returned ranges are inclusive, matching Lafzi's `HighlightPosition` (whose end value is
   * passed to `Spannable.setSpan` after adding one).
   */
  fun longestHighlightLookforward(
    highlightSequences: List<Int>,
    minLength: Int
  ): List<IntRange> {
    if (highlightSequences.isEmpty()) {
      return emptyList()
    }

    val sorted = highlightSequences.sorted()
    val size = sorted.size
    if (size == 1) {
      return listOf(sorted[0]..(sorted[0] + minLength))
    }

    val results = ArrayList<IntRange>()
    var index = 0
    var next = 1
    while (index < size) {
      while (next < size && sorted[next] - sorted[next - 1] <= minLength + 1) {
        next++
      }

      results += sorted[index]..sorted[next - 1]
      index = next
      next++
    }
    return results
  }
}
