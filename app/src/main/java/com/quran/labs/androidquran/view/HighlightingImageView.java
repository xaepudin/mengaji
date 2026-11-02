package com.quran.labs.androidquran.view;

import static com.quran.data.model.highlight.HighlightType.Mode.BACKGROUND;
import static com.quran.data.model.highlight.HighlightType.Mode.COLOR;
import static com.quran.data.model.highlight.HighlightType.Mode.HIDE;
import static com.quran.data.model.highlight.HighlightType.Mode.HIGHLIGHT;
import static com.quran.data.model.highlight.HighlightType.Mode.UNDERLAY;
import static com.quran.data.model.highlight.HighlightType.Mode.UNDERLINE;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.view.DisplayCutoutCompat;

import com.quran.data.model.SuraAyah;
import com.quran.data.model.highlight.HighlightType;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.HafizRevealStyle;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.helpers.AyahHighlight;
import com.quran.labs.androidquran.ui.helpers.AyahHighlight.SingleAyahHighlight;
import com.quran.labs.androidquran.ui.helpers.AyahHighlight.TransitionAyahHighlight;
import com.quran.labs.androidquran.ui.helpers.HighlightAnimationConfig;
import com.quran.labs.androidquran.ui.helpers.HighlightTypes;
import com.quran.page.common.data.AyahBounds;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;
import com.quran.page.common.draw.ImageDrawHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import dev.chrisbanes.insetter.Insetter;
import kotlin.Unit;

public class HighlightingImageView extends AppCompatImageView {
  // for debugging / visualizing glyph bounds:
  // when enabled, will draw bounds around each glyph to visualize the glyph bounds
  private static final boolean DEBUG_BOUNDS = false;

  // delay between words while revealing word by word continuously
  private static final long HAFIZ_CONTINUOUS_DELAY_MS = 300L;

  private static int overlayTextColor = -1;
  private static int headerFooterSize;
  private static int headerFooterFontSize;
  private static int scrollableHeaderFooterSize;
  private static int scrollableHeaderFooterFontSize;
  private static int dualPageHeaderFooterSize;
  private static int dualPageHeaderFooterFontSize;

  // Sorted map so we use highest priority highlighting when iterating
  private final SortedMap<HighlightType, Set<AyahHighlight>> currentHighlights = new TreeMap<>();

  // Hafiz mode covers every ayah on the page until it is revealed
  private boolean hafizModeEnabled = false;
  private boolean hafizPerPage = false;
  private boolean hafizShowAyahNumbers = true;
  private boolean hafizModeApplied = false;
  private int hafizFirstWordsCount = 0;
  private final Set<String> hafizHiddenAyahs = new HashSet<>();
  // per ayah reveal progress: key -> number of revealed words
  private final Map<String, Integer> hafizRevealedWords = new HashMap<>();
  // per page reveal progress, in words from the start of the page
  private int hafizPageRevealedWords = 0;
  private final RectF hafizCoverRect = new RectF();
  private final Handler hafizHandler = new Handler(Looper.getMainLooper());
  private Runnable hafizContinuousRunnable = null;

  private boolean isNightMode;
  private boolean isColorFilterOn;
  private int nightModeTextBrightness = Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
  private int nightModeBackgroundBrightness = Constants.DEFAULT_NIGHT_MODE_BACKGROUND_BRIGHTNESS;

  // Params for drawing text
  private int fontSize;
  private OverlayParams overlayParams = null;
  private RectF pageBounds = null;
  private boolean didDraw = false;
  private PageCoordinates pageCoordinates;
  private AyahCoordinates ayahCoordinates;
  private Map<AyahHighlight, List<AyahBounds>> highlightCoordinates;
  private Set<ImageDrawHelper> imageDrawHelpers;
  private ValueAnimator animator;

  private int topSafeOffset = 0;
  private int bottomSafeOffset = 0;
  private int horizontalSafeOffset = 0;
  private int verticalOffsetForScrolling = 0;

  private final ImageDrawHelper underlayHighlightsDrawer = new HighlightsDrawer(
      () -> ayahCoordinates,
      () -> highlightCoordinates,
      () -> currentHighlights,
      c -> { super.onDraw(c); return Unit.INSTANCE; },
      () -> isNightMode,
      UNDERLAY, UNDERLINE
  );

  // Draws highlights that need to run before the page image is drawn (to apply clippings)
  private final ImageDrawHelper clippingHighlightsDrawer = new HighlightsDrawer(
      () -> ayahCoordinates,
      () -> highlightCoordinates,
      () -> currentHighlights,
      c -> { super.onDraw(c); return Unit.INSTANCE; },
      () -> isNightMode,
      COLOR, HIDE
  );

  // Draws remaining highlights that need to run after the page image is drawn
  private final ImageDrawHelper highlightsDrawer = new HighlightsDrawer(
      () -> ayahCoordinates,
      () -> highlightCoordinates,
      () -> currentHighlights,
      c -> { super.onDraw(c); return null; },
      () -> isNightMode,
      HIGHLIGHT, BACKGROUND
  );

  private final ImageDrawHelper glyphBoundsDebuggingDrawer = DEBUG_BOUNDS ?
      new GlyphBoundsDebuggingDrawer(() -> ayahCoordinates) : null;

  public HighlightingImageView(Context context) {
    this(context, null);
  }

  public HighlightingImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    if (overlayTextColor == -1) {
      final Resources res = context.getResources();
      overlayTextColor = ContextCompat.getColor(context, R.color.overlay_text_color);
      headerFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size);
      scrollableHeaderFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size_scrollable);
      dualPageHeaderFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size_dualPage);
      headerFooterFontSize = res.getDimensionPixelSize(R.dimen.page_overlay_font_size);
      scrollableHeaderFooterFontSize =
          res.getDimensionPixelSize(R.dimen.page_overlay_font_size_scrollable);
      dualPageHeaderFooterFontSize =
          res.getDimensionPixelSize(R.dimen.page_overlay_font_size_dualPage);
    }

    Insetter.builder()
        .setOnApplyInsetsListener((view, insets, initialState) -> {
          final DisplayCutoutCompat cutout = insets.getDisplayCutout();
          if (cutout != null) {
            topSafeOffset = cutout.getSafeInsetTop();
            bottomSafeOffset = cutout.getSafeInsetBottom();
            horizontalSafeOffset = Math.max(cutout.getSafeInsetLeft(), cutout.getSafeInsetRight());
            setPadding(horizontalSafeOffset,
                topSafeOffset + verticalOffsetForScrolling,
                horizontalSafeOffset,
                bottomSafeOffset + verticalOffsetForScrolling);
          }
        })
        .applyToView(this);
  }

  public void setIsScrollable(boolean scrollable, boolean landscape) {
    int topBottom = scrollable ? scrollableHeaderFooterSize :
        landscape ? dualPageHeaderFooterSize : headerFooterSize;
    verticalOffsetForScrolling = topBottom;
    setPadding(horizontalSafeOffset,
        topBottom + topSafeOffset,
        horizontalSafeOffset,
        topBottom + bottomSafeOffset);
    fontSize = scrollable ? scrollableHeaderFooterFontSize :
        landscape ? dualPageHeaderFooterFontSize : headerFooterFontSize;
  }

  public void unHighlight(int surah, int ayah, HighlightType type) {
    unHighlight(surah, ayah, -1, type);
  }

  public void unHighlight(int surah, int ayah, int word, HighlightType type) {
    Set<AyahHighlight> highlights = currentHighlights.get(type);
    if (highlights != null && highlights.remove(new SingleAyahHighlight(surah, ayah, word))) {
      invalidate();
    }
  }

  public void highlightAyat(Set<String> ayahKeys, HighlightType type) {
    if (shouldRevealForHighlight(type)) {
      for (String ayahKey : ayahKeys) {
        revealAyah(ayahKey);
      }
    }
    Set<AyahHighlight> highlights = currentHighlights.get(type);
    if (highlights == null) {
      highlights = new HashSet<>();
      currentHighlights.put(type, highlights);
    }
    highlights.addAll(SingleAyahHighlight.createSet(ayahKeys));
  }

  public void unHighlight(HighlightType type) {
    if (!currentHighlights.isEmpty()) {
      currentHighlights.remove(type);
      if (type.isTransitionAnimated()) {
        //stop animation here
        if (animator != null) {
          // this check is essential because
          // if playing first time and stopping
          // before animation is setup
          animator.cancel();
        }
      }
      invalidate();
    }
  }

  public void setPageData(PageCoordinates pageCoordinates, Set<ImageDrawHelper> imageDrawHelpers) {
    this.imageDrawHelpers = imageDrawHelpers;
    this.pageCoordinates = pageCoordinates;
  }

  public void setAyahData(AyahCoordinates ayahCoordinates) {
    this.ayahCoordinates = ayahCoordinates;
    highlightCoordinates = new HashMap<>();
    for (Map.Entry<String, List<AyahBounds>> entry: ayahCoordinates.getAyahCoordinates().entrySet()) {
      highlightCoordinates.put(new SingleAyahHighlight(entry.getKey()), entry.getValue());
    }
    if (hafizModeEnabled && !hafizModeApplied) {
      // the coordinates arrived after hafiz mode was turned on, so apply it now
      if (hafizFirstWordsCount > 0) {
        applyHafizFirstWords();
      } else {
        hideAllAyahs();
      }
    }
  }

  public boolean isHafizModeEnabled() {
    return hafizModeEnabled;
  }

  public void setHafizModeEnabled(
      boolean enabled, boolean perPage, boolean showAyahNumbers, int firstWordsCount) {
    final boolean optionsChanged = hafizPerPage != perPage
        || hafizShowAyahNumbers != showAyahNumbers
        || hafizFirstWordsCount != firstWordsCount;
    if (hafizModeEnabled == enabled && !optionsChanged) {
      return;
    }

    hafizModeEnabled = enabled;
    hafizPerPage = perPage;
    hafizShowAyahNumbers = showAyahNumbers;
    hafizModeApplied = false;
    hafizFirstWordsCount = 0;
    hafizHiddenAyahs.clear();
    hafizRevealedWords.clear();
    hafizPageRevealedWords = 0;
    stopHafizContinuousReveal();
    if (enabled) {
      if (firstWordsCount > 0) {
        hafizFirstWordsCount = firstWordsCount;
        applyHafizFirstWords();
      } else {
        hideAllAyahs();
      }
    }
    invalidate();
  }

  public boolean isAyahHidden(int sura, int ayah) {
    if (!hafizModeEnabled) {
      return false;
    }
    if (hafizPerPage) {
      return getHafizWordCount(sura, ayah) > 0 && getHafizRevealedWords(sura, ayah) <= 0;
    }
    return hafizHiddenAyahs.contains(ayahKey(sura, ayah));
  }

  /** Number of words in the ayah that are currently revealed while hafiz mode is on. */
  public int getHafizRevealedWords(int sura, int ayah) {
    final int totalWords = getHafizWordCount(sura, ayah);
    // the first words baseline applies to every ayah on the page
    final int baseline = Math.min(hafizFirstWordsCount, totalWords);
    if (hafizPerPage) {
      final int wordsBefore = getWordsBeforeAyah(sura, ayah);
      if (wordsBefore < 0) {
        return baseline;
      }
      final int revealedWords =
          Math.max(0, Math.min(totalWords, hafizPageRevealedWords - wordsBefore));
      return Math.max(baseline, revealedWords);
    }
    final Integer revealedWords = hafizRevealedWords.get(ayahKey(sura, ayah));
    final int revealed = revealedWords == null ? 0 : revealedWords;
    return Math.max(baseline, revealed);
  }

  /** True when only part of the ayah is revealed (hafiz mode progressive reveal). */
  public boolean isAyahPartiallyRevealed(int sura, int ayah) {
    final int revealedWords = getHafizRevealedWords(sura, ayah);
    return hafizModeEnabled
        && revealedWords > 0
        && revealedWords < getHafizWordCount(sura, ayah);
  }

  /**
   * Applies the configured short press action. In per page mode this reveals the next ayah or
   * word, always starting from the beginning of the page.
   */
  public void revealHafizOnTap(int sura, int ayah, HafizRevealStyle style) {
    if (!hafizModeEnabled) {
      return;
    }
    clearHafizFirstWordsBaseline();
    stopHafizContinuousReveal();
    if (hafizPerPage) {
      if (style == HafizRevealStyle.WORD_BY_WORD) {
        revealNextHafizPageWord();
      } else {
        revealNextHafizPageAyah();
      }
    } else if (style == HafizRevealStyle.WORD_BY_WORD) {
      revealNextHafizWord(sura, ayah);
    } else {
      toggleAyahVisibility(sura, ayah);
    }
  }

  /**
   * Applies the configured long press action. In per page mode this reveals everything from the
   * beginning of the page up to the pressed ayah.
   */
  public void revealHafizOnLongPress(int sura, int ayah, HafizRevealStyle style) {
    if (!hafizModeEnabled) {
      return;
    }
    clearHafizFirstWordsBaseline();
    stopHafizContinuousReveal();
    if (style == HafizRevealStyle.WORD_BY_WORD_CONTINUOUS) {
      startHafizContinuousReveal(sura, ayah);
    } else if (hafizPerPage) {
      revealHafizUpToAyah(sura, ayah);
    } else if (style == HafizRevealStyle.WORD_BY_WORD) {
      revealNextHafizWord(sura, ayah);
    } else {
      toggleAyahVisibility(sura, ayah);
    }
  }

  /**
   * Reveals word by word while the finger is held down. In per page mode the reveal runs from the
   * beginning of the page up to the pressed ayah; in per ayah mode it reveals the pressed ayah.
   */
  public void startHafizContinuousReveal(int sura, int ayah) {
    if (!hafizModeEnabled) {
      return;
    }
    clearHafizFirstWordsBaseline();
    stopHafizContinuousReveal();

    final boolean perPage = hafizPerPage;
    if (perPage) {
      final int wordsBefore = getWordsBeforeAyah(sura, ayah);
      final int totalWords = getHafizWordCount(sura, ayah);
      if (wordsBefore < 0 || totalWords <= 0) {
        return;
      }
    }

    hafizContinuousRunnable = new Runnable() {
      @Override
      public void run() {
        if (!hafizModeEnabled || hafizPerPage != perPage) {
          hafizContinuousRunnable = null;
          return;
        }
        boolean revealed = false;
        if (perPage) {
          final int totalWords = getHafizWordCount(sura, ayah);
          if (totalWords > 0 && getHafizRevealedWords(sura, ayah) < totalWords) {
            revealNextHafizPageWord();
            revealed = true;
          }
        } else {
          final int totalWords = getHafizWordCount(sura, ayah);
          if (totalWords > 0 && getHafizRevealedWords(sura, ayah) < totalWords) {
            revealNextHafizWord(sura, ayah);
            revealed = true;
          }
        }
        if (revealed) {
          hafizHandler.postDelayed(this, HAFIZ_CONTINUOUS_DELAY_MS);
        } else {
          hafizContinuousRunnable = null;
        }
      }
    };
    hafizHandler.postDelayed(hafizContinuousRunnable, HAFIZ_CONTINUOUS_DELAY_MS);
  }

  /** Stops an ongoing continuous reveal (e.g. when the finger is lifted). */
  public void stopHafizContinuousReveal() {
    if (hafizContinuousRunnable != null) {
      hafizHandler.removeCallbacks(hafizContinuousRunnable);
      hafizContinuousRunnable = null;
    }
  }

  /**
   * Toggles the hafiz mode cover for an ayah. A covered or partially revealed ayah is fully
   * revealed, and an already revealed ayah is covered again.
   *
   * @return true if the ayah's visibility changed
   */
  public boolean toggleAyahVisibility(int sura, int ayah) {
    if (!hafizModeEnabled) {
      return false;
    }
    clearHafizFirstWordsBaseline();

    final String key = ayahKey(sura, ayah);
    if (ayahCoordinates == null || !ayahCoordinates.getAyahCoordinates().containsKey(key)) {
      return false;
    }

    if (hafizHiddenAyahs.contains(key) || hafizRevealedWords.containsKey(key)) {
      uncoverAyah(key);
    } else {
      coverAyah(key);
    }
    return true;
  }

  public void revealAyah(int sura, int ayah) {
    revealAyah(ayahKey(sura, ayah));
  }

  public void revealAyah(String ayahKey) {
    if (!hafizModeEnabled) {
      return;
    }
    clearHafizFirstWordsBaseline();
    if (hafizPerPage) {
      final SuraAyah ayah = parseAyahKey(ayahKey);
      if (ayah != null) {
        revealHafizUpToAyah(ayah.sura, ayah.ayah);
      }
      return;
    }
    hafizRevealedWords.remove(ayahKey);
    if (hafizHiddenAyahs.remove(ayahKey)) {
      invalidate();
    }
  }

  /** Reveals one more word of the ayah (per ayah mode). */
  public void revealNextHafizWord(int sura, int ayah) {
    if (!hafizModeEnabled || hafizPerPage) {
      return;
    }
    clearHafizFirstWordsBaseline();
    final int totalWords = getHafizWordCount(sura, ayah);
    if (totalWords <= 0) {
      revealAyah(sura, ayah);
      return;
    }
    if (!hafizHiddenAyahs.contains(ayahKey(sura, ayah))
        && !hafizRevealedWords.containsKey(ayahKey(sura, ayah))) {
      // already fully revealed
      return;
    }
    setHafizRevealedWords(sura, ayah, getHafizRevealedWords(sura, ayah) + 1);
  }

  /** Reveals the next word of the page (per page mode). */
  public void revealNextHafizPageWord() {
    if (!hafizModeEnabled || !hafizPerPage) {
      return;
    }
    for (SuraAyah ayah : pageAyahs()) {
      final int totalWords = getHafizWordCount(ayah.sura, ayah.ayah);
      if (totalWords <= 0) {
        continue;
      }
      final int revealedWords = getHafizRevealedWords(ayah.sura, ayah.ayah);
      if (revealedWords < totalWords) {
        final int wordsBefore = getWordsBeforeAyah(ayah.sura, ayah.ayah);
        hafizPageRevealedWords =
            Math.max(hafizPageRevealedWords, wordsBefore + revealedWords + 1);
        invalidate();
        return;
      }
    }
  }

  /** Reveals the next ayah of the page (per page mode). */
  public void revealNextHafizPageAyah() {
    if (!hafizModeEnabled || !hafizPerPage) {
      return;
    }
    clearHafizFirstWordsBaseline();
    for (SuraAyah ayah : pageAyahs()) {
      final int totalWords = getHafizWordCount(ayah.sura, ayah.ayah);
      if (totalWords <= 0) {
        continue;
      }
      final int wordsBefore = getWordsBeforeAyah(ayah.sura, ayah.ayah);
      final int revealedWords =
          Math.max(0, Math.min(totalWords, hafizPageRevealedWords - wordsBefore));
      if (revealedWords < totalWords) {
        hafizPageRevealedWords = wordsBefore + totalWords;
        invalidate();
        return;
      }
    }
  }

  /** Hides the last revealed ayah of the page (per page mode). */
  public void hidePreviousHafizPageAyah() {
    if (!hafizModeEnabled || !hafizPerPage) {
      return;
    }
    clearHafizFirstWordsBaseline();
    int wordsBefore = 0;
    int targetStart = -1;
    for (SuraAyah ayah : pageAyahs()) {
      final int totalWords = getHafizWordCount(ayah.sura, ayah.ayah);
      if (totalWords <= 0) {
        continue;
      }
      final int revealedWords =
          Math.max(0, Math.min(totalWords, hafizPageRevealedWords - wordsBefore));
      if (revealedWords > 0) {
        // this is the last ayah with at least one revealed word
        targetStart = wordsBefore;
      }
      wordsBefore += totalWords;
    }
    if (targetStart >= 0) {
      hafizPageRevealedWords = targetStart;
      invalidate();
    }
  }

  /** Reveals every ayah on the page (used by the hafiz mode toolbar menu). */
  public void revealAllHafizAyahs() {
    if (!hafizModeEnabled) {
      return;
    }
    hafizFirstWordsCount = 0;
    hafizHiddenAyahs.clear();
    hafizRevealedWords.clear();
    hafizPageRevealedWords = getTotalPageWords();
    hafizModeApplied = true;
    invalidate();
  }

  /** Covers every ayah on the page (used by the hafiz mode toolbar menu). */
  public void hideAllHafizAyahs() {
    if (!hafizModeEnabled) {
      return;
    }
    hafizFirstWordsCount = 0;
    hafizHiddenAyahs.clear();
    hafizRevealedWords.clear();
    hafizPageRevealedWords = 0;
    if (!hafizPerPage && ayahCoordinates != null) {
      hafizHiddenAyahs.addAll(ayahCoordinates.getAyahCoordinates().keySet());
    }
    hafizModeApplied = true;
    invalidate();
  }

  /** Reveals only the first word of every ayah on the page. */
  public void revealFirstWordOfEachAyah() {
    if (!hafizModeEnabled) {
      return;
    }
    hafizFirstWordsCount = 1;
    applyHafizFirstWords();
  }

  private void applyHafizFirstWords() {
    if (ayahCoordinates == null) {
      // the coordinates are not ready yet, setAyahData will apply this state later
      return;
    }
    hafizHiddenAyahs.clear();
    hafizRevealedWords.clear();
    hafizPageRevealedWords = 0;
    hafizModeApplied = true;
    invalidate();
  }

  private void clearHafizFirstWordsBaseline() {
    if (hafizFirstWordsCount <= 0 || hafizPerPage) {
      // in per page mode the first words of every ayah stay revealed while the page reveal
      // continues, so the baseline is kept
      return;
    }
    final int count = hafizFirstWordsCount;
    hafizFirstWordsCount = 0;
    if (ayahCoordinates != null) {
      // keep the revealed first words of each ayah and continue from there
      for (String key : ayahCoordinates.getAyahCoordinates().keySet()) {
        final SuraAyah ayah = parseAyahKey(key);
        if (ayah != null) {
          final int totalWords = getHafizWordCount(ayah.sura, ayah.ayah);
          if (totalWords > 0) {
            hafizRevealedWords.put(key, Math.min(count, totalWords));
          }
        }
      }
    }
  }

  /** Reveals everything from the start of the page up to the given ayah (per page mode). */
  public void revealHafizUpToAyah(int sura, int ayah) {
    if (!hafizModeEnabled || !hafizPerPage) {
      return;
    }
    clearHafizFirstWordsBaseline();
    final int wordsBefore = getWordsBeforeAyah(sura, ayah);
    final int totalWords = getHafizWordCount(sura, ayah);
    if (wordsBefore < 0 || totalWords <= 0) {
      return;
    }
    hafizPageRevealedWords = Math.max(hafizPageRevealedWords, wordsBefore + totalWords);
    invalidate();
  }

  private void setHafizRevealedWords(int sura, int ayah, int revealedWords) {
    final String key = ayahKey(sura, ayah);
    final int totalWords = getHafizWordCount(sura, ayah);
    if (totalWords <= 0) {
      revealAyah(key);
      return;
    }

    if (revealedWords <= 0) {
      hafizRevealedWords.remove(key);
      coverAyah(key);
    } else if (revealedWords >= totalWords) {
      uncoverAyah(key);
    } else {
      hafizHiddenAyahs.remove(key);
      hafizRevealedWords.put(key, revealedWords);
      invalidate();
    }
  }

  private void coverAyah(String ayahKey) {
    hafizHiddenAyahs.add(ayahKey);
    hafizRevealedWords.remove(ayahKey);
    invalidate();
  }

  private void uncoverAyah(String ayahKey) {
    hafizHiddenAyahs.remove(ayahKey);
    hafizRevealedWords.remove(ayahKey);
    invalidate();
  }

  private int getHafizWordCount(int sura, int ayah) {
    final AyahCoordinates coordinates = ayahCoordinates;
    if (coordinates == null) {
      return 0;
    }
    return HafizRevealBounds.INSTANCE.wordCount(
        coordinates, new SuraAyah(sura, ayah), hafizShowAyahNumbers);
  }

  private int getTotalPageWords() {
    int totalWords = 0;
    for (SuraAyah ayah : pageAyahs()) {
      totalWords += getHafizWordCount(ayah.sura, ayah.ayah);
    }
    return totalWords;
  }

  private int getWordsBeforeAyah(int sura, int ayah) {
    int wordsBefore = 0;
    for (SuraAyah pageAyah : pageAyahs()) {
      if (pageAyah.sura == sura && pageAyah.ayah == ayah) {
        return wordsBefore;
      }
      wordsBefore += getHafizWordCount(pageAyah.sura, pageAyah.ayah);
    }
    return -1;
  }

  private List<SuraAyah> pageAyahs() {
    final AyahCoordinates coordinates = ayahCoordinates;
    if (coordinates == null) {
      return Collections.emptyList();
    }
    final List<SuraAyah> ayahs = new ArrayList<>();
    for (String key : coordinates.getAyahCoordinates().keySet()) {
      final SuraAyah ayah = parseAyahKey(key);
      if (ayah != null) {
        ayahs.add(ayah);
      }
    }
    Collections.sort(ayahs);
    return ayahs;
  }

  private static SuraAyah parseAyahKey(String ayahKey) {
    final int separator = ayahKey.indexOf(':');
    if (separator <= 0) {
      return null;
    }
    try {
      return new SuraAyah(
          Integer.parseInt(ayahKey.substring(0, separator)),
          Integer.parseInt(ayahKey.substring(separator + 1)));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void hideAllAyahs() {
    final AyahCoordinates coordinates = ayahCoordinates;
    if (coordinates == null) {
      return;
    }

    final Set<String> ayahKeys = coordinates.getAyahCoordinates().keySet();
    if (ayahKeys.isEmpty()) {
      return;
    }

    hafizRevealedWords.clear();
    hafizPageRevealedWords = 0;
    hafizHiddenAyahs.clear();
    for (String ayahKey : ayahKeys) {
      final boolean active = isActivelyHighlighted(ayahKey);
      if (hafizPerPage) {
        if (active) {
          // keep the currently selected / playing ayah visible
          revealAyah(ayahKey);
        }
      } else if (!active) {
        hafizHiddenAyahs.add(ayahKey);
      }
    }
    hafizModeApplied = true;
    invalidate();
  }

  private boolean isActivelyHighlighted(String ayahKey) {
    return containsHighlight(currentHighlights.get(HighlightTypes.SELECTION), ayahKey)
        || containsHighlight(currentHighlights.get(HighlightTypes.AUDIO), ayahKey);
  }

  private static boolean containsHighlight(Set<AyahHighlight> highlights, String ayahKey) {
    if (highlights == null) {
      return false;
    }
    for (AyahHighlight highlight : highlights) {
      if (ayahKey.equals(highlight.getKey())) {
        return true;
      }
    }
    return false;
  }

  private boolean shouldRevealForHighlight(HighlightType type) {
    return hafizModeEnabled
        && (type == HighlightTypes.SELECTION || type == HighlightTypes.AUDIO);
  }

  private static String ayahKey(int sura, int ayah) {
    return sura + ":" + ayah;
  }

  public void setNightMode(boolean isNightMode, int textBrightness, int backgroundBrightness) {
    this.isNightMode = isNightMode;
    if (isNightMode) {
      nightModeTextBrightness = textBrightness;
      nightModeBackgroundBrightness = backgroundBrightness;
      // we need a new color filter now
      isColorFilterOn = false;
    }
    // Re-init overlay params color based on the new night mode setting
    initOverlayParamsColor();
    adjustNightMode();
  }

  class AnimationUpdateListener implements ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {
    /*
    This is an inner class because it needs access to invalidate()
     */
    Set<AyahHighlight> highlights;
    TransitionAyahHighlight transitionHighlight;

    public AnimationUpdateListener(Set<AyahHighlight> highlights, TransitionAyahHighlight transitionHighlight) {
      this.highlights = highlights;
      this.transitionHighlight = transitionHighlight;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
      List<AyahBounds> value = (List<AyahBounds>) animation.getAnimatedValue();
      highlightCoordinates.put(transitionHighlight, value);
      invalidate();
    }

    @Override
    public void onAnimationStart(Animator animation) {

    }

    @Override
    public void onAnimationEnd(Animator animation) {
      highlightCoordinates.remove(transitionHighlight);
      highlights.remove(transitionHighlight);
      highlights.add(transitionHighlight.getDestination());
    }

    @Override
    public void onAnimationCancel(Animator animation) {
      highlightCoordinates.remove(transitionHighlight);
      highlights.remove(transitionHighlight);
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }
  }

  private void highlightFloatableAyah(Set<AyahHighlight> highlights, AyahHighlight destinationHighlight, HighlightAnimationConfig config) {
    AyahHighlight previousHighlight = highlights.iterator().next();
    AyahHighlight sourceHighlight;

    List<AyahBounds> startingBounds;
    if (previousHighlight.isTransition()) {
      // The ayah changed during animating
      startingBounds = (List<AyahBounds>)animator.getAnimatedValue();
      animator.cancel();
      sourceHighlight = ((TransitionAyahHighlight)previousHighlight).getSource();
    } else {
      sourceHighlight = previousHighlight;
      startingBounds = highlightCoordinates.get(sourceHighlight);
    }

    final TransitionAyahHighlight transitionHighlight = new TransitionAyahHighlight(sourceHighlight, destinationHighlight);

    if (startingBounds == null) {
      startingBounds = new ArrayList<>();
    }

    // yes we make copies, because normalizing the bounds will change them
    List<AyahBounds> sourceBounds = new ArrayList<>(startingBounds);

    List<AyahBounds> destinationBounds = new ArrayList<>();
    final List<AyahBounds> source = highlightCoordinates.get(destinationHighlight);
    if (source != null) {
      destinationBounds.addAll(source);
    }

    highlights.clear();
    highlights.add(transitionHighlight);

    animator = ValueAnimator.ofObject(config.getTypeEvaluator(), sourceBounds, destinationBounds);

    animator.setDuration(config.getDuration());

    AnimationUpdateListener listener = new AnimationUpdateListener(highlights, transitionHighlight);
    animator.addUpdateListener(listener);
    animator.addListener(listener);
    animator.setInterpolator(config.getInterpolator());
    animator.start();
  }

  private boolean shouldFloatHighlight(Set<AyahHighlight> highlights, HighlightType type, AyahHighlight destinationHighlight) {
    // only animating AUDIO highlights, for now
    if (!type.isTransitionAnimated()) {
      return false;
    }

    // can only animate from one ayah to another, for now
    if (highlights.size() != 1 || highlightCoordinates == null) {
      return false;
    }

    AyahHighlight previousAyahHighlight = highlights.iterator().next();

    if (destinationHighlight.equals(previousAyahHighlight)) {
      // can't animate to the same location
      return false;
    }

    List<AyahBounds> startingBounds;
    if (previousAyahHighlight.isTransition()) {
      if (animator == null) {
        return false;
      }
      startingBounds = (List<AyahBounds>) animator.getAnimatedValue();
    } else {
      startingBounds = highlightCoordinates.get(previousAyahHighlight);
    }

    List<AyahBounds> destinationBounds = highlightCoordinates.get(destinationHighlight);
    // normalization needs rectangles on both sides, including when retargeting a transition.
    return startingBounds != null && !startingBounds.isEmpty()
        && destinationBounds != null && !destinationBounds.isEmpty();
  }

  public void highlightAyah(int surah, int ayah, int word, HighlightType type) {
    if (shouldRevealForHighlight(type)) {
      revealAyah(surah, ayah);
    }
    final Set<AyahHighlight> highlights = currentHighlights.get(type);
    final SingleAyahHighlight singleAyahHighlight = new SingleAyahHighlight(surah, ayah, word);
    if (highlights == null) {
      final Set<AyahHighlight> updatedHighlights = new HashSet<>();
      updatedHighlights.add(singleAyahHighlight);
      currentHighlights.put(type, updatedHighlights);
    } else if (type.isSingle()) {
      // If multiple highlighting not allowed (e.g. audio)
      // clear all others of this type first
      // only if highlight type is floatable
      if (shouldFloatHighlight(highlights, type, singleAyahHighlight)) {
        highlightFloatableAyah(highlights, singleAyahHighlight, HighlightTypes.INSTANCE.getAnimationConfig(type));
      } else {
        // cancel before replacing highlights, since its callbacks modify this set.
        if (type.isTransitionAnimated() && animator != null) {
          animator.cancel();
        }
        highlights.clear();
        highlights.add(singleAyahHighlight);
      }
    } else {
      highlights.add(singleAyahHighlight);
      currentHighlights.put(type, highlights);
    }
  }

  @Override
  public void setImageDrawable(Drawable bitmap) {
    // clear the color filter before setting the image
    clearColorFilter();
    // this allows the filter to be enabled again if needed
    isColorFilterOn = false;

    super.setImageDrawable(bitmap);
    if (bitmap != null) {
      adjustNightMode();
    }
  }

  public void adjustNightMode() {
    if (isNightMode && !isColorFilterOn) {
      float[] matrix =
          NightModeColorMatrix.create(nightModeTextBrightness, nightModeBackgroundBrightness);
      setColorFilter(new ColorMatrixColorFilter(matrix));
      isColorFilterOn = true;
    } else if (!isNightMode) {
      clearColorFilter();
      isColorFilterOn = false;
    }

    invalidate();
  }

  private static class OverlayParams {
    boolean init = false;
    Paint paint = null;
    float offsetX;
    float topBaseline;
    float bottomBaseline;
    String suraText = null;
    String juzText = null;
    String pageText = null;
    String rub3Text = null;
    String manzilText = null;
  }

  public void setOverlayText(String suraText, String juzText, String pageText, String rub3Text, String manzilText) {
    // Calculate page bounding rect from ayahinfo db
    if (pageBounds == null) {
      return;
    }

    overlayParams = new OverlayParams();
    overlayParams.suraText = suraText;
    overlayParams.juzText = juzText;
    overlayParams.pageText = pageText;
    overlayParams.rub3Text = rub3Text;
    overlayParams.manzilText = manzilText;
    overlayParams.paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
    overlayParams.paint.setTextSize(fontSize);
//    if (juzText.contains("ج")) {
//      // change typeface for Arabic
//      overlayParams.paint.setTypeface(TypefaceManager.getHeaderFooterTypeface(context));
//    }
    if (!didDraw) {
      invalidate();
    }
  }

  public void setPageBounds(RectF rect) {
    pageBounds = rect;
  }

  private boolean initOverlayParams(Matrix matrix) {
    if (overlayParams == null || pageBounds == null) {
      return false;
    }

    // Overlay params previously initiated; skip
    if (overlayParams.init) {
      return true;
    }

    initOverlayParamsColor();

    // Use font metrics to calculate the maximum possible height of the text
    FontMetrics fm = overlayParams.paint.getFontMetrics();

    final RectF mappedRect = new RectF();
    matrix.mapRect(mappedRect, pageBounds);

    // Calculate where the text's baseline should be
    // (for top text and bottom text)
    // (p.s. parts of the glyphs will be below the baseline such as a
    // 'y' or 'ي')
    overlayParams.topBaseline = -fm.top;
    overlayParams.bottomBaseline = getHeight() - fm.bottom;

    // Calculate the horizontal margins off the edge of screen
    overlayParams.offsetX = Math.min(
        mappedRect.left, getWidth() - mappedRect.right);

    overlayParams.init = true;
    return true;
  }

  private void initOverlayParamsColor() {
    if (overlayParams == null) {
      return;
    }
    int overlayColor = overlayTextColor;
    if (isNightMode) {
      overlayColor = Color.rgb(nightModeTextBrightness,
          nightModeTextBrightness, nightModeTextBrightness);
    }
    overlayParams.paint.setColor(overlayColor);
  }

  private void overlayText(Canvas canvas, Matrix matrix) {
    if (overlayParams == null || !initOverlayParams(matrix)) {
      return;
    }

    overlayParams.paint.setTextAlign(Align.LEFT);
    canvas.drawText(overlayParams.suraText,
        overlayParams.offsetX + horizontalSafeOffset,
        overlayParams.topBaseline + topSafeOffset,
        overlayParams.paint);
    overlayParams.paint.setTextAlign(Align.CENTER);
    canvas.drawText(overlayParams.pageText,
        getWidth() / 2.0f,
        overlayParams.bottomBaseline - bottomSafeOffset,
        overlayParams.paint);
    // Merge the current rub3 text with the juz' text
    overlayParams.paint.setTextAlign(Align.RIGHT);
    canvas.drawText(overlayParams.juzText + overlayParams.rub3Text + overlayParams.manzilText,
        (getWidth() - overlayParams.offsetX) - horizontalSafeOffset,
        overlayParams.topBaseline + topSafeOffset,
        overlayParams.paint);
    didDraw = true;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (overlayParams != null) {
      overlayParams.init = false;
    }
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    final Drawable d = getDrawable();
    if (d == null) {
      // no image, forget it.
      return;
    }

    // draw the highlights that go under the page image first
    if (pageCoordinates != null) {
      underlayHighlightsDrawer.draw(pageCoordinates, canvas, this);
    }

    // Save the canvas before applying any clippings
    canvas.save();

    // Draw highlights that involve clipping the canvas (HIDE, COLOR)
    // Note: this must be done before the super.onDraw call so that HighlightsDrawer has a chance
    //       to apply canvas clippings (e.g. hiding) before the image is drawn
    if (pageCoordinates != null) {
      clippingHighlightsDrawer.draw(pageCoordinates, canvas, this);
    }

    // Clip out the parts of the page that are still covered by hafiz mode
    if (hafizModeEnabled) {
      drawHafizCovers(canvas);
    }

    // Draw the page image (excluding clipped out sections)
    super.onDraw(canvas);

    // Restore the canvas to remove any clippings so the remaining highlights/drawers don't get clipped
    canvas.restore();

    // Draw remaining highlights (other than HIDE, COLOR)
    if (pageCoordinates != null) {
      highlightsDrawer.draw(pageCoordinates, canvas, this);
    }

    // Draw overlay text
    didDraw = false;
    if (overlayParams != null) {
      overlayText(canvas, getImageMatrix());
    }

    // run additional image draw helpers if any
    if (imageDrawHelpers != null && pageCoordinates != null) {
      for (ImageDrawHelper imageDrawHelper : imageDrawHelpers) {
        imageDrawHelper.draw(pageCoordinates, canvas, this);
      }
    }

    // for debugging
    if (DEBUG_BOUNDS && pageCoordinates != null) {
      glyphBoundsDebuggingDrawer.draw(pageCoordinates, canvas, this);
    }
  }

  private void drawHafizCovers(Canvas canvas) {
    final AyahCoordinates coordinates = ayahCoordinates;
    if (coordinates == null) {
      return;
    }

    if (hafizPerPage) {
      for (SuraAyah ayah : pageAyahs()) {
        final int totalWords = getHafizWordCount(ayah.sura, ayah.ayah);
        if (totalWords <= 0) {
          continue;
        }
        final int revealedWords = getHafizRevealedWords(ayah.sura, ayah.ayah);
        if (revealedWords >= totalWords) {
          continue;
        }
        clipHafizCover(canvas, coordinates, ayah, revealedWords);
      }
    } else {
      for (String key : coordinates.getAyahCoordinates().keySet()) {
        final SuraAyah ayah = parseAyahKey(key);
        if (ayah == null) {
          continue;
        }
        if (hafizHiddenAyahs.contains(key)) {
          clipHafizCover(canvas, coordinates, ayah, 0);
          continue;
        }
        final int totalWords = getHafizWordCount(ayah.sura, ayah.ayah);
        if (hafizRevealedWords.containsKey(key)) {
          final int revealedWords = hafizRevealedWords.get(key);
          if (totalWords <= 0 || revealedWords < totalWords) {
            clipHafizCover(canvas, coordinates, ayah, revealedWords);
          }
        } else if (hafizFirstWordsCount > 0 && totalWords > 0) {
          // ayahs that are not tracked are fully revealed, but the first words baseline
          // keeps their leading words covered
          final int revealedWords = Math.min(hafizFirstWordsCount, totalWords);
          if (revealedWords < totalWords) {
            clipHafizCover(canvas, coordinates, ayah, revealedWords);
          }
        }
      }
    }
  }

  private void clipHafizCover(
      Canvas canvas,
      AyahCoordinates coordinates,
      SuraAyah ayah,
      int revealedWords
  ) {
    final List<RectF> coveredBounds = HafizRevealBounds.INSTANCE.coveredBounds(
        coordinates, ayah, revealedWords, hafizShowAyahNumbers);
    for (RectF bounds : coveredBounds) {
      hafizCoverRect.set(bounds);
      getImageMatrix().mapRect(hafizCoverRect);
      hafizCoverRect.offset(getPaddingLeft(), getPaddingTop());
      clipOutRect(canvas, hafizCoverRect);
    }
  }

  private static void clipOutRect(Canvas canvas, RectF rect) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      canvas.clipOutRect(rect);
    } else {
      canvas.clipRect(rect, Region.Op.DIFFERENCE);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    stopHafizContinuousReveal();
    super.onDetachedFromWindow();
  }
}
