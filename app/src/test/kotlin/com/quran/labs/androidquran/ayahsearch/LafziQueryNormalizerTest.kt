package com.quran.labs.androidquran.ayahsearch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LafziQueryNormalizerTest {

  @Test
  fun `normalizes vocal latin queries like lafzi`() {
    assertThat(LafziQueryNormalizer.normalize("bismillah", true)).isEqualTo("BISMILAH")
    assertThat(LafziQueryNormalizer.normalize("zzzzzzz", true)).isEqualTo("Z")
    assertThat(LafziQueryNormalizer.normalize("Adzaabis saiir", true))
      .isEqualTo("XAZABISAYR")
    assertThat(LafziQueryNormalizer.normalize("Fir'aun", true)).isEqualTo("FIRXAWN")
  }

  @Test
  fun `normalizes non vocal latin queries like lafzi`() {
    assertThat(LafziQueryNormalizer.normalize("bismillah", false)).isEqualTo("BSMLH")
    assertThat(LafziQueryNormalizer.normalize("MuNafiquun", false)).isEqualTo("MNFKN")
    assertThat(LafziQueryNormalizer.normalize("wallahi", false)).isEqualTo("WLH")
  }

  @Test
  fun `handles punctuation, whitespace and short queries`() {
    assertThat(LafziQueryNormalizer.normalize("  bismillah  ", true)).isEqualTo("BISMILAH")
    assertThat(LafziQueryNormalizer.normalize("bismillah!", true)).isEqualTo("BISMILAH")
    assertThat(LafziQueryNormalizer.normalize("bismillah-rahman", true))
      .isEqualTo("BISMILAHRAHMAN")
    assertThat(LafziQueryNormalizer.normalize("ab", true)).isEqualTo("XAB")
  }
}
