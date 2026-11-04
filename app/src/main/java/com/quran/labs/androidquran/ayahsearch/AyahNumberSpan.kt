package com.quran.labs.androidquran.ayahsearch

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.style.ReplacementSpan

/**
 * Draws the End of Ayah ornament with the Arabic-Indic ayah number centered
 * inside it. Fonts do not consistently place digits inside U+06DD via text
 * shaping, so the number is overlaid on the ornament here.
 */
internal class AyahNumberSpan(private val number: String) : ReplacementSpan() {
  private val marker = "\u06DD"
  private val markerScale = 0.85f

  override fun getSize(
    paint: Paint,
    text: CharSequence,
    start: Int,
    end: Int,
    fm: Paint.FontMetricsInt?
  ): Int {
    val markerPaint = Paint(paint).apply { textSize = paint.textSize * markerScale }
    return markerPaint.measureText(marker).toInt()
  }

  override fun draw(
    canvas: Canvas,
    text: CharSequence,
    start: Int,
    end: Int,
    x: Float,
    top: Int,
    y: Int,
    bottom: Int,
    paint: Paint
  ) {
    val markerPaint = Paint(paint).apply { textSize = paint.textSize * markerScale }
    val markerWidth = markerPaint.measureText(marker)
    canvas.drawText(marker, x, y.toFloat(), markerPaint)

    val numberPaint = Paint(paint).apply {
      // me_quran's Arabic-Indic digits are decorative ayah-number glyphs
      // with bars around them; use a normal Arabic typeface for the digit.
      typeface = Typeface.create("sans-serif", Typeface.BOLD)
      isFakeBoldText = true
      textSize = paint.textSize * 0.52f
      textAlign = Paint.Align.CENTER
    }
    val markerMetrics = markerPaint.fontMetrics
    val centerY = y + (markerMetrics.ascent + markerMetrics.descent) / 2f
    val numberMetrics = numberPaint.fontMetrics
    val numberBaseline = centerY - (numberMetrics.ascent + numberMetrics.descent) / 2f
    canvas.drawText(number, x + markerWidth / 2f, numberBaseline, numberPaint)
  }
}
