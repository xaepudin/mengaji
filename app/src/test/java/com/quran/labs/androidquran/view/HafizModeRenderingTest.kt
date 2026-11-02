package com.quran.labs.androidquran.view

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.data.coordinates.GlyphCoords
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [33], qualifiers = "en")
@RunWith(RobolectricTestRunner::class)
class HafizModeRenderingTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val pageSize = 200

  @Test
  fun `covered ayah is not drawn until it is revealed`() {
    val imageView = imageView(ayahCoordinates())
    imageView.setHafizModeEnabled(true, false, true, 0)

    val covered = draw(imageView)
    assertThat(pixelAt(covered, 100, 100)).isNotEqualTo(Color.RED)
    assertThat(pixelAt(covered, 10, 10)).isEqualTo(Color.RED)

    imageView.toggleAyahVisibility(1, 1)

    val revealed = draw(imageView)
    assertThat(pixelAt(revealed, 100, 100)).isEqualTo(Color.RED)
  }

  @Test
  fun `revealing one word keeps the rest of the ayah covered`() {
    val imageView = imageView(ayahCoordinatesWithGlyphs())
    imageView.setHafizModeEnabled(true, false, true, 0)

    imageView.revealNextHafizWord(1, 1)

    val partiallyRevealed = draw(imageView)
    // the first word (right side of the line) is visible
    assertThat(pixelAt(partiallyRevealed, 150, 25)).isEqualTo(Color.RED)
    // the second word is still covered
    assertThat(pixelAt(partiallyRevealed, 45, 25)).isNotEqualTo(Color.RED)
  }

  @Test
  fun `revealing one word works with the ayahinfo word rows`() {
    val imageView = imageView(ayahCoordinatesWithWordRows())
    imageView.setHafizModeEnabled(true, false, true, 0)

    imageView.revealNextHafizWord(1, 1)

    val partiallyRevealed = draw(imageView)
    assertThat(pixelAt(partiallyRevealed, 150, 25)).isEqualTo(Color.RED)
    assertThat(pixelAt(partiallyRevealed, 45, 25)).isNotEqualTo(Color.RED)
  }

  @Test
  fun `first word stays visible in ayah mode`() {
    val imageView = imageView(ayahCoordinatesWithMarker())
    imageView.setHafizModeEnabled(true, false, true, 1)

    val covered = draw(imageView)
    // the first word and the ayah number marker are visible
    assertThat(pixelAt(covered, 80, 25)).isEqualTo(Color.RED)
    assertThat(pixelAt(covered, 20, 25)).isEqualTo(Color.RED)
    // the second word is covered
    assertThat(pixelAt(covered, 150, 25)).isNotEqualTo(Color.RED)
  }

  @Test
  fun `ayah number marker stays visible when ayah numbers are shown`() {
    val imageView = imageView(ayahCoordinatesWithMarker())
    imageView.setHafizModeEnabled(true, false, true, 0)

    val covered = draw(imageView)
    assertThat(pixelAt(covered, 20, 25)).isEqualTo(Color.RED)
    assertThat(pixelAt(covered, 150, 25)).isNotEqualTo(Color.RED)
  }

  @Test
  fun `ayah number marker is covered when ayah numbers are hidden`() {
    val imageView = imageView(ayahCoordinatesWithMarker())
    imageView.setHafizModeEnabled(true, false, false, 0)

    val covered = draw(imageView)
    assertThat(pixelAt(covered, 20, 25)).isNotEqualTo(Color.RED)
  }

  private fun imageView(ayahCoordinates: AyahCoordinates): HighlightingImageView {
    val imageView = HighlightingImageView(context)
    val pageBitmap = Bitmap.createBitmap(pageSize, pageSize, Bitmap.Config.ARGB_8888)
    pageBitmap.eraseColor(Color.RED)
    imageView.setImageDrawable(BitmapDrawable(context.resources, pageBitmap))
    imageView.setPageData(
      PageCoordinates(
        1,
        RectF(0f, 0f, pageSize.toFloat(), pageSize.toFloat()),
        emptyList(),
        emptyList()
      ),
      emptySet()
    )
    imageView.setAyahData(ayahCoordinates)
    imageView.measure(
      View.MeasureSpec.makeMeasureSpec(pageSize, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(pageSize, View.MeasureSpec.EXACTLY)
    )
    imageView.layout(0, 0, pageSize, pageSize)
    return imageView
  }

  private fun ayahCoordinates(): AyahCoordinates {
    val bounds = listOf(AyahBounds(1, 1, RectF(50f, 50f, 150f, 150f)))
    return AyahCoordinates(1, mapOf("1:1" to bounds), null)
  }

  private fun ayahCoordinatesWithGlyphs(): AyahCoordinates {
    val ayah = SuraAyah(1, 1)
    val glyphs = listOf(
      GlyphCoords(WordGlyph(ayah, 1, 1), 1, RectF(100f, 0f, 200f, 50f)),
      GlyphCoords(WordGlyph(ayah, 2, 2), 1, RectF(0f, 0f, 90f, 50f)),
      GlyphCoords(AyahEndGlyph(ayah, 3), 1, RectF(-50f, 0f, -10f, 50f))
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

  private fun ayahCoordinatesWithMarker(): AyahCoordinates {
    val words = listOf(
      AyahBounds(1, 1, RectF(50f, 0f, 120f, 50f)),
      AyahBounds(1, 2, RectF(130f, 0f, 200f, 50f)),
      AyahBounds(1, 3, RectF(0f, 0f, 40f, 50f))
    )
    return AyahCoordinates(
      1,
      mapOf("1:1" to listOf(AyahBounds(1, 1, RectF(0f, 0f, 200f, 50f)))),
      null,
      wordCoordinates = mapOf("1:1" to words)
    )
  }

  private fun draw(imageView: HighlightingImageView): Bitmap {
    val output = Bitmap.createBitmap(pageSize, pageSize, Bitmap.Config.ARGB_8888)
    imageView.draw(Canvas(output))
    return output
  }

  private fun pixelAt(bitmap: Bitmap, x: Int, y: Int): Int = bitmap.getPixel(x, y)
}
