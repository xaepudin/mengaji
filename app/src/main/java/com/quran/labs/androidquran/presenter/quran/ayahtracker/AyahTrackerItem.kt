package com.quran.labs.androidquran.presenter.quran.ayahtracker

import android.view.View
import com.quran.data.model.AyahGlyph
import com.quran.data.model.AyahWord
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightInfo
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.selection.SelectionIndicator
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.HafizPageAction
import com.quran.labs.androidquran.data.HafizRevealStyle
import com.quran.labs.androidquran.ui.translation.AyahIndicators
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates

open class AyahTrackerItem internal constructor(val page: Int) {
  open fun onSetPageBounds(pageCoordinates: PageCoordinates) {}
  open fun onSetAyahCoordinates(ayahCoordinates: AyahCoordinates) {}

  open fun onHighlight(page: Int, highlightInfo: HighlightInfo): Boolean {
    return onHighlightAyah(page, highlightInfo.sura, highlightInfo.ayah,
        highlightInfo.word, highlightInfo.highlightType, highlightInfo.scrollToAyah)
  }

  open fun onUnhighlight(page: Int, highlightInfo: HighlightInfo) {
    onUnHighlightAyah(page, highlightInfo.sura, highlightInfo.ayah,
        highlightInfo.word, highlightInfo.highlightType)
  }

  open fun onHighlightAyah(
    page: Int,
    sura: Int,
    ayah: Int,
    type: HighlightType,
    scrollToAyah: Boolean
  ): Boolean {
    return onHighlightAyah(page, sura, ayah, -1, type, scrollToAyah)
  }

  open fun onHighlightAyah(
    page: Int,
    sura: Int,
    ayah: Int,
    word: Int,
    type: HighlightType,
    scrollToAyah: Boolean
  ): Boolean {
    return false
  }

  open fun onHighlightAyat(page: Int, ayahKeys: Set<String>, type: HighlightType) {}
  open fun onUnHighlightAyah(page: Int, sura: Int, ayah: Int, type: HighlightType) {
    return onUnHighlightAyah(page, sura, ayah, -1, type)
  }
  open fun onUnHighlightAyah(page: Int, sura: Int, ayah: Int, word: Int, type: HighlightType) {}
  open fun onUnHighlightAyahType(type: HighlightType) {}
  open fun onAyahIndicators(indicators: Map<SuraAyah, AyahIndicators>) {}

  open fun getToolBarPosition(page: Int, sura: Int, ayah: Int): SelectionIndicator = SelectionIndicator.None

  open fun getToolBarPosition(page: Int, word: AyahWord): SelectionIndicator =
      getToolBarPosition(page, word.ayah.sura, word.ayah.ayah)

  open fun getToolBarPosition(page: Int, glyph: AyahGlyph): SelectionIndicator =
      getToolBarPosition(page, glyph.ayah.sura, glyph.ayah.ayah)

  open fun getAyahForPosition(page: Int, x: Float, y: Float): SuraAyah? = null

  open fun getGlyphForPosition(page: Int, x: Float, y: Float): AyahGlyph? = null

  /** True if the given screen position is over this page's ayahs. */
  open fun isPointOnPage(page: Int, x: Float, y: Float): Boolean = false

  /** Reveals the ayah at the given screen position if hafiz mode is covering it. */
  open fun revealHafizAyah(page: Int, x: Float, y: Float) {}

  /**
   * Applies the configured short press action to a covered ayah.
   * @return true if the action was handled.
   */
  open fun revealHafizAyahOnTap(
    page: Int,
    sura: Int,
    ayah: Int,
    style: HafizRevealStyle
  ): Boolean = false

  /**
   * Applies the configured long press action to a covered ayah.
   * @return true if the action was handled.
   */
  open fun revealHafizAyahOnLongPress(
    page: Int,
    sura: Int,
    ayah: Int,
    style: HafizRevealStyle
  ): Boolean = false

  /** Reveals the next ayah of the page (per page mode). */
  open fun revealNextHafizPageAyah(page: Int): Boolean = false

  /** Hides the last revealed ayah of the page (per page mode). */
  open fun hidePreviousHafizPageAyah(page: Int): Boolean = false

  /** Applies a page level hafiz action (reveal all, hide all, first word only). */
  open fun applyHafizPageAction(action: HafizPageAction): Boolean = false

  open fun getQuranAyahInfo(sura: Int, ayah: Int): QuranAyahInfo? = null

  open fun getLocalTranslations(): Array<LocalTranslation>? = null

  open fun getAyahView(): View? = null
}
