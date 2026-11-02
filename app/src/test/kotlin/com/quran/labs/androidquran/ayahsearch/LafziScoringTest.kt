package com.quran.labs.androidquran.ayahsearch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LafziScoringTest {

  @Test
  fun `finds the longest contiguous subsequence within the allowed gap`() {
    assertThat(LafziScoring.longestContiguousSubsequence(listOf(1, 2, 3, 12), 7))
      .containsExactly(1, 2, 3).inOrder()
    assertThat(LafziScoring.longestContiguousSubsequence(listOf(12, 1, 2, 3), 7))
      .containsExactly(1, 2, 3).inOrder()
    assertThat(LafziScoring.longestContiguousSubsequence(emptyList(), 7)).isEmpty()
    assertThat(LafziScoring.longestContiguousSubsequence(listOf(4), 7))
      .containsExactly(4)
  }

  @Test
  fun `rewards positions that are close together`() {
    assertThat(LafziScoring.reciprocalDiffAverage(listOf(1, 2, 3))).isEqualTo(1.0)
    assertThat(LafziScoring.reciprocalDiffAverage(listOf(1, 3, 5))).isEqualTo(0.5)
    assertThat(LafziScoring.reciprocalDiffAverage(listOf(7))).isEqualTo(1.0)
  }

  @Test
  fun `merges nearby highlight positions and keeps distant ones separate`() {
    assertThat(LafziScoring.longestHighlightLookforward(listOf(0, 1, 2), 6))
      .containsExactly(0..2)

    val ranges = LafziScoring.longestHighlightLookforward(listOf(0, 20), 6)
    assertThat(ranges).containsExactly(0..0, 20..20).inOrder()
  }

  @Test
  fun `single highlight position spans the minimum length`() {
    assertThat(LafziScoring.longestHighlightLookforward(listOf(5), 6))
      .containsExactly(5..11)
  }
}
