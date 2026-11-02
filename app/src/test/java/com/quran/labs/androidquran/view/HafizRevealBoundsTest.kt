package com.quran.labs.androidquran.view

import android.app.Application
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.coordinates.GlyphCoords
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33], qualifiers = "en")
@RunWith(RobolectricTestRunner::class)
class HafizRevealBoundsTest {
  private val ayah = SuraAyah(1, 1)

  @Test
  fun `word count uses glyph data when available`() {
    assertThat(HafizRevealBounds.wordCount(glyphCoordinates(), ayah, true)).isEqualTo(2)
  }

  @Test
  fun `word count excludes the ayah number marker when it is shown`() {
    assertThat(HafizRevealBounds.wordCount(wordCoordinates(), ayah, true)).isEqualTo(2)
  }

  @Test
  fun `word count includes the ayah number marker when it is hidden`() {
    assertThat(HafizRevealBounds.wordCount(wordCoordinates(), ayah, false)).isEqualTo(3)
  }

  @Test
  fun `nothing is covered once the whole ayah is revealed`() {
    assertThat(HafizRevealBounds.coveredBounds(glyphCoordinates(), ayah, 2, true)).isEmpty()
    assertThat(HafizRevealBounds.coveredBounds(glyphCoordinates(), ayah, 2, false)).isEmpty()
    assertThat(HafizRevealBounds.coveredBounds(wordCoordinates(), ayah, 2, true)).isEmpty()
    assertThat(HafizRevealBounds.coveredBounds(wordCoordinates(), ayah, 3, false)).isEmpty()
  }

  @Test
  fun `glyph bounds include the ayah number marker when it is hidden`() {
    val bounds = HafizRevealBounds.coveredBounds(glyphCoordinates(), ayah, 1, false)

    assertThat(bounds).hasSize(1)
    assertThat(bounds[0].left).isEqualTo(-50f)
    assertThat(bounds[0].right).isEqualTo(90f)
    assertThat(bounds[0].top).isEqualTo(0f)
    // expanded halfway towards the next line
    assertThat(bounds[0].bottom).isEqualTo(55f)
  }

  @Test
  fun `glyph bounds exclude the ayah number marker when it is shown`() {
    val bounds = HafizRevealBounds.coveredBounds(glyphCoordinates(), ayah, 1, true)

    assertThat(bounds).hasSize(1)
    assertThat(bounds[0].left).isEqualTo(0f)
    assertThat(bounds[0].right).isEqualTo(90f)
  }

  @Test
  fun `word row bounds exclude the ayah number marker when it is shown`() {
    val bounds = HafizRevealBounds.coveredBounds(wordCoordinates(), ayah, 1, true)

    assertThat(bounds).hasSize(1)
    assertThat(bounds[0].left).isEqualTo(0f)
    assertThat(bounds[0].right).isEqualTo(90f)
    assertThat(bounds[0].bottom).isEqualTo(55f)
  }

  @Test
  fun `word row bounds include the ayah number marker when it is hidden`() {
    val bounds = HafizRevealBounds.coveredBounds(wordCoordinates(), ayah, 1, false)

    assertThat(bounds).hasSize(1)
    assertThat(bounds[0].left).isEqualTo(-50f)
    assertThat(bounds[0].right).isEqualTo(90f)
  }

  @Test
  fun `the whole ayah is covered when no word is revealed`() {
    val shown = HafizRevealBounds.coveredBounds(wordCoordinates(), ayah, 0, true)
    assertThat(shown).hasSize(1)
    assertThat(shown[0].left).isEqualTo(0f)
    assertThat(shown[0].right).isEqualTo(200f)

    val hidden = HafizRevealBounds.coveredBounds(wordCoordinates(), ayah, 0, false)
    assertThat(hidden).hasSize(1)
    assertThat(hidden[0].left).isEqualTo(-50f)
    assertThat(hidden[0].right).isEqualTo(200f)
  }

  @Test
  fun `ayahs without word data have no word count`() {
    val coordinates = AyahCoordinates(
      1,
      mapOf("1:1" to listOf(AyahBounds(1, 1, RectF(0f, 0f, 100f, 50f)))),
      null
    )

    assertThat(HafizRevealBounds.wordCount(coordinates, ayah, true)).isEqualTo(0)
    assertThat(HafizRevealBounds.coveredBounds(coordinates, ayah, 0, true)).hasSize(1)
    assertThat(HafizRevealBounds.coveredBounds(coordinates, ayah, 1, true)).isEmpty()
  }

  private fun glyphCoordinates(): AyahCoordinates {
    val glyphs = listOf(
      GlyphCoords(WordGlyph(ayah, 1, 1), 1, RectF(100f, 0f, 200f, 50f)),
      GlyphCoords(WordGlyph(ayah, 2, 2), 1, RectF(0f, 0f, 90f, 50f)),
      GlyphCoords(AyahEndGlyph(ayah, 3), 1, RectF(-50f, 0f, -10f, 50f)),
      GlyphCoords(WordGlyph(SuraAyah(1, 2), 1, 1), 2, RectF(0f, 60f, 200f, 110f))
    )
    return AyahCoordinates(
      1,
      ayahLineBounds(),
      PageGlyphsCoords(1, glyphs)
    )
  }

  private fun wordCoordinates(): AyahCoordinates {
    val words = listOf(
      AyahBounds(1, 1, RectF(100f, 0f, 200f, 50f)),
      AyahBounds(1, 2, RectF(0f, 0f, 90f, 50f)),
      AyahBounds(1, 3, RectF(-50f, 0f, -10f, 50f))
    )
    return AyahCoordinates(
      1,
      ayahLineBounds(),
      null,
      wordCoordinates = mapOf("1:1" to words)
    )
  }

  private fun ayahLineBounds(): Map<String, List<AyahBounds>> = mapOf(
    "1:1" to listOf(AyahBounds(1, 1, RectF(-50f, 0f, 200f, 50f))),
    "1:2" to listOf(AyahBounds(2, 1, RectF(0f, 60f, 200f, 110f)))
  )
}
