package com.quran.labs.androidquran.view

import android.app.Application
import android.content.Context
import android.graphics.RectF
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.data.HafizRevealStyle
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.coordinates.GlyphCoords
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@Config(application = Application::class, sdk = [33], qualifiers = "en")
@RunWith(RobolectricTestRunner::class)
class HighlightingImageViewTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `hafiz mode covers every ayah on the page`() {
    val imageView = imageView()
    imageView.setAyahData(ayahCoordinates("1:1", "1:2"))
    imageView.setHafizModeEnabled(true, false, false, 0)

    assertThat(imageView.isAyahHidden(1, 1)).isTrue()
    assertThat(imageView.isAyahHidden(1, 2)).isTrue()
  }

  @Test
  fun `enabling hafiz mode before coordinates load covers ayahs when they arrive`() {
    val imageView = imageView()
    imageView.setHafizModeEnabled(true, false, false, 0)
    imageView.setAyahData(ayahCoordinates("1:1"))

    assertThat(imageView.isAyahHidden(1, 1)).isTrue()
  }

  @Test
  fun `tapping a covered ayah reveals only that ayah`() {
    val imageView = imageView()
    imageView.setAyahData(ayahCoordinates("1:1", "1:2"))
    imageView.setHafizModeEnabled(true, false, false, 0)

    assertThat(imageView.toggleAyahVisibility(1, 1)).isTrue()

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isTrue()
  }

  @Test
  fun `tapping a revealed ayah covers it again`() {
    val imageView = imageView()
    imageView.setAyahData(ayahCoordinates("1:1"))
    imageView.setHafizModeEnabled(true, false, false, 0)

    imageView.toggleAyahVisibility(1, 1)
    assertThat(imageView.toggleAyahVisibility(1, 1)).isTrue()

    assertThat(imageView.isAyahHidden(1, 1)).isTrue()
  }

  @Test
  fun `turning hafiz mode off reveals every ayah`() {
    val imageView = imageView()
    imageView.setAyahData(ayahCoordinates("1:1", "1:2"))
    imageView.setHafizModeEnabled(true, false, false, 0)
    imageView.toggleAyahVisibility(1, 1)

    imageView.setHafizModeEnabled(false, false, false, 0)

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isFalse()
  }

  @Test
  fun `ayahs that are not on the page cannot be toggled`() {
    val imageView = imageView()
    imageView.setAyahData(ayahCoordinates("1:1"))
    imageView.setHafizModeEnabled(true, false, false, 0)

    assertThat(imageView.toggleAyahVisibility(2, 255)).isFalse()
  }

  @Test
  fun `revealing words one at a time eventually reveals the whole ayah`() {
    val imageView = imageView()
    imageView.setAyahData(ayahCoordinatesWithGlyphs())
    imageView.setHafizModeEnabled(true, false, true, 0)

    imageView.revealNextHafizWord(1, 1)

    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isTrue()
    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(1)
    assertThat(imageView.isAyahHidden(1, 1)).isFalse()

    imageView.revealNextHafizWord(1, 1)

    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
  }

  @Test
  fun `revealing words one at a time works with the ayahinfo word rows`() {
    val imageView = imageView()
    imageView.setAyahData(ayahCoordinatesWithWordRows())
    imageView.setHafizModeEnabled(true, false, true, 0)

    imageView.revealNextHafizWord(1, 1)

    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isTrue()
    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(1)
  }

  @Test
  fun `tapping a partially revealed ayah reveals the rest of it`() {
    val imageView = imageView()
    imageView.setAyahData(ayahCoordinatesWithGlyphs())
    imageView.setHafizModeEnabled(true, false, true, 0)
    imageView.revealNextHafizWord(1, 1)

    assertThat(imageView.toggleAyahVisibility(1, 1)).isTrue()

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isFalse()
  }

  @Test
  fun `per page mode reveals words from the first ayah of the page`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealNextHafizPageWord()

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(1)
    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isTrue()
    assertThat(imageView.isAyahHidden(1, 2)).isTrue()
    assertThat(imageView.isAyahHidden(1, 3)).isTrue()
  }

  @Test
  fun `per page mode reveals the next ayah of the page`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealNextHafizPageAyah()

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isTrue()
    assertThat(imageView.isAyahHidden(1, 3)).isTrue()
  }

  @Test
  fun `first word applied before coordinates load covers when they arrive`() {
    val imageView = imageView()
    imageView.setHafizModeEnabled(true, false, true, 1)

    imageView.setAyahData(pageCoordinates())

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(1)
    assertThat(imageView.getHafizRevealedWords(1, 2)).isEqualTo(1)
    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isTrue()
  }

  @Test
  fun `enabling first word only reveals the first word of every ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())

    imageView.setHafizModeEnabled(true, true, true, 1)

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(1)
    assertThat(imageView.getHafizRevealedWords(1, 2)).isEqualTo(1)
    assertThat(imageView.getHafizRevealedWords(1, 3)).isEqualTo(1)
  }

  @Test
  fun `increasing the first words count reveals more words per ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 1)

    imageView.setHafizModeEnabled(true, true, true, 2)

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(2)
    assertThat(imageView.getHafizRevealedWords(1, 2)).isEqualTo(2)
    assertThat(imageView.getHafizRevealedWords(1, 3)).isEqualTo(2)
  }

  @Test
  fun `switching to first word only while enabled applies it`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.setHafizModeEnabled(true, true, true, 1)

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(1)
    assertThat(imageView.getHafizRevealedWords(1, 2)).isEqualTo(1)
  }

  @Test
  fun `reveal all reveals every ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealAllHafizAyahs()

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isFalse()
    assertThat(imageView.isAyahHidden(1, 3)).isFalse()
  }

  @Test
  fun `hide all covers every ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, false, true, 0)
    imageView.revealAllHafizAyahs()

    imageView.hideAllHafizAyahs()

    assertThat(imageView.isAyahHidden(1, 1)).isTrue()
    assertThat(imageView.isAyahHidden(1, 2)).isTrue()
    assertThat(imageView.isAyahHidden(1, 3)).isTrue()
  }

  @Test
  fun `reveal first word reveals one word per ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealFirstWordOfEachAyah()

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(1)
    assertThat(imageView.getHafizRevealedWords(1, 2)).isEqualTo(1)
    assertThat(imageView.getHafizRevealedWords(1, 3)).isEqualTo(1)
    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isTrue()
    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
  }

  @Test
  fun `continuing to reveal keeps the first word of every ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)
    imageView.revealFirstWordOfEachAyah()

    imageView.revealNextHafizPageAyah()

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(2)
    assertThat(imageView.getHafizRevealedWords(1, 2)).isEqualTo(1)
    assertThat(imageView.getHafizRevealedWords(1, 3)).isEqualTo(1)
    assertThat(imageView.isAyahPartiallyRevealed(1, 2)).isTrue()
  }

  @Test
  fun `word by word continues after the first word of each ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)
    imageView.revealFirstWordOfEachAyah()

    imageView.revealNextHafizPageWord()

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(2)
    assertThat(imageView.getHafizRevealedWords(1, 2)).isEqualTo(1)
  }

  @Test
  fun `swiping up keeps the first word of the hidden ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)
    imageView.revealFirstWordOfEachAyah()
    imageView.revealNextHafizPageAyah()
    imageView.revealNextHafizPageAyah()

    imageView.hidePreviousHafizPageAyah()

    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(2)
    assertThat(imageView.getHafizRevealedWords(1, 2)).isEqualTo(1)
    assertThat(imageView.getHafizRevealedWords(1, 3)).isEqualTo(1)
  }

  @Test
  fun `revealing a word after the first word action continues normally`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, false, true, 0)
    imageView.revealFirstWordOfEachAyah()

    imageView.revealNextHafizWord(1, 1)

    // the second word completed the ayah
    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isFalse()
  }

  @Test
  fun `per page mode hides the last revealed ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealNextHafizPageAyah()
    imageView.revealNextHafizPageAyah()
    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isFalse()
    assertThat(imageView.isAyahHidden(1, 3)).isTrue()

    imageView.hidePreviousHafizPageAyah()

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isTrue()
    assertThat(imageView.isAyahHidden(1, 3)).isTrue()
  }

  @Test
  fun `per page mode hides a partially revealed ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealNextHafizPageAyah()
    imageView.revealNextHafizPageWord()
    assertThat(imageView.isAyahPartiallyRevealed(1, 2)).isTrue()

    imageView.hidePreviousHafizPageAyah()

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isTrue()
  }

  @Test
  fun `per page mode applies the configured tap style`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealHafizOnTap(1, 1, HafizRevealStyle.REVEAL)
    assertThat(imageView.isAyahHidden(1, 1)).isFalse()

    imageView.setHafizModeEnabled(false, true, true, 0)
    imageView.setHafizModeEnabled(true, true, true, 0)
    imageView.revealHafizOnTap(1, 1, HafizRevealStyle.WORD_BY_WORD)
    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(1)
  }

  @Test
  fun `continuous long press reveals words while held`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, false, true, 0)

    imageView.startHafizContinuousReveal(1, 1)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
    val revealedWhileHeld = imageView.getHafizRevealedWords(1, 1)
    assertThat(revealedWhileHeld).isGreaterThan(0)

    imageView.stopHafizContinuousReveal()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    assertThat(imageView.getHafizRevealedWords(1, 1)).isEqualTo(revealedWhileHeld)
  }

  @Test
  fun `per page continuous long press stops at the chosen ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.startHafizContinuousReveal(1, 2)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
    imageView.stopHafizContinuousReveal()

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isFalse()
    assertThat(imageView.isAyahHidden(1, 3)).isTrue()
  }

  @Test
  fun `per page mode reveals everything up to the selected ayah`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealHafizUpToAyah(1, 2)

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isFalse()
    assertThat(imageView.isAyahHidden(1, 3)).isTrue()
  }

  @Test
  fun `per page long press ignores the word by word style`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, true, true, 0)

    imageView.revealHafizOnLongPress(1, 2, HafizRevealStyle.WORD_BY_WORD)

    assertThat(imageView.isAyahHidden(1, 1)).isFalse()
    assertThat(imageView.isAyahHidden(1, 2)).isFalse()
    assertThat(imageView.isAyahHidden(1, 3)).isTrue()
  }

  @Test
  fun `per ayah mode applies the configured tap style`() {
    val imageView = imageView()
    imageView.setAyahData(pageCoordinates())
    imageView.setHafizModeEnabled(true, false, true, 0)

    imageView.revealHafizOnTap(1, 2, HafizRevealStyle.REVEAL)
    assertThat(imageView.isAyahHidden(1, 2)).isFalse()
    assertThat(imageView.isAyahHidden(1, 1)).isTrue()

    imageView.setHafizModeEnabled(true, false, true, 0)
    imageView.revealHafizOnTap(1, 1, HafizRevealStyle.WORD_BY_WORD)
    assertThat(imageView.isAyahPartiallyRevealed(1, 1)).isTrue()
  }

  private fun imageView(): HighlightingImageView = HighlightingImageView(context)

  private fun ayahCoordinates(vararg keys: String): AyahCoordinates {
    val coordinates = keys.associateWith {
      listOf(AyahBounds(1, 1, RectF(0f, 0f, 100f, 100f)))
    }
    return AyahCoordinates(1, coordinates, null)
  }

  private fun ayahCoordinatesWithGlyphs(): AyahCoordinates {
    val ayah = SuraAyah(1, 1)
    val glyphs = listOf(
      GlyphCoords(WordGlyph(ayah, 1, 1), 1, RectF(100f, 0f, 200f, 50f)),
      GlyphCoords(WordGlyph(ayah, 2, 2), 1, RectF(0f, 0f, 90f, 50f)),
      GlyphCoords(AyahEndGlyph(ayah, 3), 1, RectF(-50f, 0f, -10f, 50f)),
      GlyphCoords(WordGlyph(SuraAyah(1, 2), 1, 1), 2, RectF(0f, 60f, 200f, 110f))
    )
    return AyahCoordinates(
      1,
      mapOf("1:1" to listOf(AyahBounds(1, 1, RectF(-50f, 0f, 200f, 50f)))),
      PageGlyphsCoords(1, glyphs)
    )
  }

  private fun ayahCoordinatesWithWordRows(): AyahCoordinates {
    val words = listOf(
      AyahBounds(1, 1, RectF(100f, 0f, 200f, 50f)),
      AyahBounds(1, 2, RectF(0f, 0f, 90f, 50f)),
      AyahBounds(1, 3, RectF(-50f, 0f, -10f, 50f))
    )
    return AyahCoordinates(
      1,
      mapOf("1:1" to listOf(AyahBounds(1, 1, RectF(-50f, 0f, 200f, 50f)))),
      null,
      wordCoordinates = mapOf("1:1" to words)
    )
  }

  private fun pageCoordinates(): AyahCoordinates {
    val rows = mapOf(
      "1:1" to listOf(
        AyahBounds(1, 1, RectF(200f, 0f, 300f, 50f)),
        AyahBounds(1, 2, RectF(100f, 0f, 190f, 50f)),
        AyahBounds(1, 3, RectF(40f, 0f, 90f, 50f))
      ),
      "1:2" to listOf(
        AyahBounds(1, 1, RectF(200f, 60f, 300f, 110f)),
        AyahBounds(1, 2, RectF(100f, 60f, 190f, 110f)),
        AyahBounds(1, 3, RectF(40f, 60f, 90f, 110f))
      ),
      "1:3" to listOf(
        AyahBounds(1, 1, RectF(200f, 120f, 300f, 170f)),
        AyahBounds(1, 2, RectF(100f, 120f, 190f, 170f)),
        AyahBounds(1, 3, RectF(40f, 120f, 90f, 170f))
      )
    )
    val merged = mapOf(
      "1:1" to listOf(AyahBounds(1, 1, RectF(40f, 0f, 300f, 50f))),
      "1:2" to listOf(AyahBounds(2, 1, RectF(40f, 60f, 300f, 110f))),
      "1:3" to listOf(AyahBounds(3, 1, RectF(40f, 120f, 300f, 170f)))
    )
    return AyahCoordinates(1, merged, null, wordCoordinates = rows)
  }
}
