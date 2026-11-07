package com.quran.labs.androidquran.presenter.quran.ayahtracker

import android.view.View
import com.quran.data.model.AyahGlyph
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.selection.SelectionIndicator
import com.quran.labs.androidquran.data.HafizPageAction
import com.quran.labs.androidquran.data.HafizRevealStyle
import com.quran.labs.androidquran.ui.translation.AyahIndicators
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates

class AyahSplitConsolidationTrackerItem(
  page: Int,
  private val imageTrackerItem: AyahImageTrackerItem,
  private val translationTrackerItem: AyahTranslationTrackerItem
) : AyahTrackerItem(page) {

  override fun onSetPageBounds(pageCoordinates: PageCoordinates) {
    imageTrackerItem.onSetPageBounds(pageCoordinates)
    translationTrackerItem.onSetPageBounds(pageCoordinates)
  }

  override fun onSetAyahCoordinates(ayahCoordinates: AyahCoordinates) {
    imageTrackerItem.onSetAyahCoordinates(ayahCoordinates)
    translationTrackerItem.onSetAyahCoordinates(ayahCoordinates)
  }

  override fun onHighlightAyah(
    page: Int, sura: Int, ayah: Int, word: Int, type: HighlightType, scrollToAyah: Boolean
  ): Boolean {
    val firstResult = imageTrackerItem.onHighlightAyah(page, sura, ayah, word, type, scrollToAyah)
    val secondResult = translationTrackerItem.onHighlightAyah(page, sura, ayah, word, type, scrollToAyah)
    return firstResult && secondResult
  }

  override fun onHighlightAyat(page: Int, ayahKeys: Set<String>, type: HighlightType) {
    imageTrackerItem.onHighlightAyat(page, ayahKeys, type)
    translationTrackerItem.onHighlightAyat(page, ayahKeys, type)
  }

  override fun onUnHighlightAyah(page: Int, sura: Int, ayah: Int, type: HighlightType) {
    imageTrackerItem.onUnHighlightAyah(page, sura, ayah, type)
    translationTrackerItem.onUnHighlightAyah(page, sura, ayah, type)
  }

  override fun onUnHighlightAyahType(type: HighlightType) {
    imageTrackerItem.onUnHighlightAyahType(type)
    translationTrackerItem.onUnHighlightAyahType(type)
  }

  override fun onAyahIndicators(indicators: Map<SuraAyah, AyahIndicators>) {
    translationTrackerItem.onAyahIndicators(indicators)
  }

  override fun getToolBarPosition(page: Int, sura: Int, ayah: Int): SelectionIndicator {
    return imageTrackerItem.getToolBarPosition(page, sura, ayah)
  }

  override fun getAyahForPosition(page: Int, x: Float, y: Float): SuraAyah? {
    return imageTrackerItem.getAyahForPosition(page, x, y)
  }

  override fun getGlyphForPosition(page: Int, x: Float, y: Float): AyahGlyph? {
    return imageTrackerItem.getGlyphForPosition(page, x, y)
  }

  override fun isPointOnPage(page: Int, x: Float, y: Float): Boolean {
    return imageTrackerItem.isPointOnPage(page, x, y)
  }

  override fun revealHafizAyah(page: Int, x: Float, y: Float) {
    imageTrackerItem.revealHafizAyah(page, x, y)
  }

  override fun revealHafizAyahOnTap(
    page: Int,
    sura: Int,
    ayah: Int,
    style: HafizRevealStyle
  ): Boolean {
    return imageTrackerItem.revealHafizAyahOnTap(page, sura, ayah, style)
  }

  override fun revealHafizAyahOnLongPress(
    page: Int,
    sura: Int,
    ayah: Int,
    style: HafizRevealStyle
  ): Boolean {
    return imageTrackerItem.revealHafizAyahOnLongPress(page, sura, ayah, style)
  }

  override fun revealNextHafizPageAyah(page: Int): Boolean {
    return imageTrackerItem.revealNextHafizPageAyah(page)
  }

  override fun hidePreviousHafizPageAyah(page: Int): Boolean {
    return imageTrackerItem.hidePreviousHafizPageAyah(page)
  }

  override fun applyHafizPageAction(action: HafizPageAction): Boolean {
    return imageTrackerItem.applyHafizPageAction(action)
  }

  override fun getAyahView(): View {
    return imageTrackerItem.ayahView
  }
}
