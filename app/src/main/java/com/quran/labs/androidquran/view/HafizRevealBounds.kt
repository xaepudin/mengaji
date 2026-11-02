package com.quran.labs.androidquran.view

import android.graphics.RectF
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.coordinates.GlyphCoords
import com.quran.page.common.data.coordinates.PageGlyphsCoords

/**
 * Computes how much of an ayah still has to be covered while hafiz mode is revealing it.
 *
 * The ayahinfo database used by the Madani pages has one row per word (with the ayah number
 * marker as the last row) but no glyph data, so word bounds are taken from those rows when
 * glyph data is not available.
 */
object HafizRevealBounds {

  /**
   * Number of revealable words in the ayah, or 0 when there is no word data.
   *
   * When [showAyahNumber] is true the ayah number marker is left visible, so it does not count
   * as a word to reveal.
   */
  fun wordCount(
    coordinates: AyahCoordinates,
    ayah: SuraAyah,
    showAyahNumber: Boolean
  ): Int {
    val glyphWords = glyphWords(coordinates.glyphCoordinates, ayah)
    if (glyphWords.isNotEmpty()) {
      return glyphWords.size
    }
    val rows = coordinates.wordCoordinates[key(ayah)] ?: return 0
    return if (showAyahNumber && rows.isNotEmpty()) rows.size - 1 else rows.size
  }

  /**
   * Bounds that still have to be covered after [revealedWords] words of [ayah] were revealed.
   *
   * Passing 0 returns the bounds of the whole ayah (minus the ayah number marker when
   * [showAyahNumber] is true). Returns an empty list when the ayah is fully revealed.
   */
  fun coveredBounds(
    coordinates: AyahCoordinates,
    ayah: SuraAyah,
    revealedWords: Int,
    showAyahNumber: Boolean
  ): List<RectF> {
    val glyphs = coordinates.glyphCoordinates
    if (glyphs != null && glyphWords(glyphs, ayah).isNotEmpty()) {
      return glyphCoveredBounds(glyphs, ayah, revealedWords, showAyahNumber)
    }

    val rows = coordinates.wordCoordinates[key(ayah)]
    if (!rows.isNullOrEmpty()) {
      return wordCoveredBounds(coordinates, ayah, rows, revealedWords, showAyahNumber)
    }

    // without any word data only a full cover is possible
    if (revealedWords > 0) {
      return emptyList()
    }
    return coordinates.ayahCoordinates[key(ayah)]?.map { it.bounds } ?: emptyList()
  }

  private fun glyphWords(glyphs: PageGlyphsCoords?, ayah: SuraAyah): List<GlyphCoords> =
    glyphs?.glyphsByAyah?.get(ayah)
      ?.filter { it.glyph is WordGlyph }
      ?.sortedBy { it.glyph.position }
      .orEmpty()

  private fun glyphCoveredBounds(
    glyphs: PageGlyphsCoords,
    ayah: SuraAyah,
    revealedWords: Int,
    showAyahNumber: Boolean
  ): List<RectF> {
    val ayahGlyphs = glyphs.glyphsByAyah[ayah] ?: return emptyList()
    val words = glyphWords(glyphs, ayah)
    if (words.isEmpty() || revealedWords >= words.size) {
      return emptyList()
    }

    // everything after the last revealed word (in reading order) is still hidden
    val lastRevealedPosition =
      if (revealedWords <= 0) -1 else words[revealedWords - 1].glyph.position
    val hiddenByLine = LinkedHashMap<Int, RectF>()
    ayahGlyphs.forEach { glyph ->
      if (glyph.glyph.position <= lastRevealedPosition) {
        return@forEach
      }
      if (showAyahNumber && glyph.glyph is AyahEndGlyph) {
        return@forEach
      }
      val lineBounds = hiddenByLine[glyph.line]
      if (lineBounds == null) {
        hiddenByLine[glyph.line] = RectF(glyph.bounds)
      } else {
        lineBounds.union(glyph.bounds)
      }
    }

    return hiddenByLine.map { (line, bounds) ->
      alignWithPageLines(glyphs.lineBounds, line, bounds)
      bounds
    }
  }

  private fun wordCoveredBounds(
    coordinates: AyahCoordinates,
    ayah: SuraAyah,
    rows: List<AyahBounds>,
    revealedWords: Int,
    showAyahNumber: Boolean
  ): List<RectF> {
    // the last row of an ayah in the ayahinfo database is its ayah number marker
    val words = if (showAyahNumber) rows.dropLast(1) else rows
    if (words.isEmpty() || revealedWords >= words.size) {
      return emptyList()
    }

    val pageLineBounds = pageLineBounds(coordinates.ayahCoordinates)
    val hiddenByLine = LinkedHashMap<Int, RectF>()
    words.drop(maxOf(0, revealedWords)).forEach { word ->
      val lineBounds = hiddenByLine[word.line]
      if (lineBounds == null) {
        hiddenByLine[word.line] = RectF(word.bounds)
      } else {
        lineBounds.union(word.bounds)
      }
    }

    return hiddenByLine.map { (line, bounds) ->
      alignWithPageLines(pageLineBounds, line, bounds)
      bounds
    }
  }

  private fun alignWithPageLines(
    pageLineBounds: Map<Int, RectF>,
    line: Int,
    bounds: RectF
  ) {
    val lineBounds = pageLineBounds[line] ?: return
    bounds.top = lineBounds.top
    bounds.bottom = lineBounds.bottom
    // expand vertically to the adjacent lines, like the full ayah cover does
    pageLineBounds[line - 1]?.let { previous ->
      bounds.top = previous.bottom + (lineBounds.top - previous.bottom) / 2f
    }
    pageLineBounds[line + 1]?.let { next ->
      bounds.bottom = lineBounds.bottom + (next.top - lineBounds.bottom) / 2f
    }
  }

  private fun pageLineBounds(ayahCoordinates: Map<String, List<AyahBounds>>): Map<Int, RectF> {
    val result = HashMap<Int, RectF>()
    ayahCoordinates.values.forEach { ayahBounds ->
      ayahBounds.forEach { bound ->
        val lineBounds = result[bound.line]
        if (lineBounds == null) {
          result[bound.line] = RectF(bound.bounds)
        } else {
          lineBounds.union(bound.bounds)
        }
      }
    }
    return result
  }

  private fun key(ayah: SuraAyah): String = "${ayah.sura}:${ayah.ayah}"
}
