package com.quran.labs.androidquran.ayahsearch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LafziTrigramExtractorTest {

  @Test
  fun `extracts every trigram and its positions`() {
    val trigrams = LafziTrigramExtractor.extract("BISMILAH")

    assertThat(trigrams.keys)
      .containsExactly("BIS", "ISM", "SMI", "MIL", "ILA", "LAH")
    assertThat(trigrams.getValue("BIS"))
      .isEqualTo(LafziTrigram(frequency = 1, positions = listOf(0)))
    assertThat(trigrams.getValue("LAH"))
      .isEqualTo(LafziTrigram(frequency = 1, positions = listOf(5)))
  }

  @Test
  fun `repeated trigrams keep all positions`() {
    val trigrams = LafziTrigramExtractor.extract("ABABAB")

    assertThat(trigrams.getValue("ABA").frequency).isEqualTo(2)
    assertThat(trigrams.getValue("ABA").positions).containsExactly(0, 2).inOrder()
    assertThat(trigrams.getValue("BAB").frequency).isEqualTo(2)
    assertThat(trigrams.getValue("BAB").positions).containsExactly(1, 3).inOrder()
  }

  @Test
  fun `three character query uses lafzi's index position`() {
    val trigrams = LafziTrigramExtractor.extract("AAA")

    assertThat(trigrams)
      .containsExactly("AAA", LafziTrigram(frequency = 1, positions = listOf(1)))
  }

  @Test
  fun `queries shorter than three characters have no trigrams`() {
    assertThat(LafziTrigramExtractor.extract("AB")).isEmpty()
    assertThat(LafziTrigramExtractor.extract("")).isEmpty()
  }
}
