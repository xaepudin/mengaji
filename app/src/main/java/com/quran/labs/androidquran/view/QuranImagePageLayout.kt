package com.quran.labs.androidquran.view

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.quran.labs.androidquran.data.HafizModeType
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.util.PageController
import com.quran.labs.androidquran.util.QuranSettings
import kotlin.math.abs

/**
 * Layout class for a single Arabic page of the Quran, with margins/background.
 * <p>
 * Note that the image of the Quran page to be displayed is set by users of this class by calling
 * {@link #getImageView()} and calling the appropriate methods on that view.
 */
open class QuranImagePageLayout(context: Context) : QuranPageLayout(context) {

  private lateinit var imageView: HighlightingImageView

  init {
    this.initialize()
  }

  override fun generateContentView(context: Context, isLandscape: Boolean): View {
    imageView = HighlightingImageView(context).apply {
      adjustViewBounds = true
      setIsScrollable(isLandscape && shouldWrapWithScrollView(), isLandscape)
    }
    return imageView
  }

  override fun updateView(quranSettings: QuranSettings) {
    super.updateView(quranSettings)
    imageView.setNightMode(
      quranSettings.isNightMode,
      quranSettings.pageTextBrightness,
      quranSettings.nightModeBackgroundBrightness
    )
    imageView.setHafizModeEnabled(
      quranSettings.isHafizModeEnabled,
      HafizModeType.fromKey(quranSettings.hafizModeType) == HafizModeType.PAGE,
      quranSettings.isHafizShowAyahNumbers,
      quranSettings.hafizFirstWordsCount
    )
  }

  override fun setPageController(controller: PageController?, pageNumber: Int, skips: Int) {
    super.setPageController(controller, pageNumber, skips)
    val gestureDetector = GestureDetector(context, PageGestureDetector())
    val gestureListener = OnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
          imageView.stopHafizContinuousReveal()
      }
      gestureDetector.onTouchEvent(event)
    }
    imageView.setOnTouchListener(gestureListener)
    imageView.isClickable = true
    imageView.isLongClickable = true
  }

  fun getImageView(): HighlightingImageView = imageView

  inner class PageGestureDetector : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean = true

    override fun onSingleTapUp(event: MotionEvent): Boolean {
      return pageController.handleTouchEvent(
        event,
        AyahSelectedListener.EventType.SINGLE_TAP,
        pageNumber
      )
    }

    override fun onDoubleTap(event: MotionEvent): Boolean {
      return pageController.handleTouchEvent(
        event,
        AyahSelectedListener.EventType.DOUBLE_TAP,
        pageNumber
      )
    }

    override fun onLongPress(event: MotionEvent) {
      pageController.handleTouchEvent(
        event,
        AyahSelectedListener.EventType.LONG_PRESS,
        pageNumber
      )
    }

    override fun onFling(
      e1: MotionEvent?,
      e2: MotionEvent,
      velocityX: Float,
      velocityY: Float
    ): Boolean {
      if (e1 == null) return false
      val deltaY = e2.y - e1.y
      val deltaX = e2.x - e1.x
      // only handle vertical swipes, horizontal swipes change pages
      if (abs(deltaY) <= abs(deltaX)) return false
      if (abs(deltaY) < SWIPE_MIN_DISTANCE_DP * resources.displayMetrics.density) return false
      val eventType = if (deltaY > 0) {
        AyahSelectedListener.EventType.SWIPE_DOWN
      } else {
        AyahSelectedListener.EventType.SWIPE_UP
      }
      return pageController.handleTouchEvent(e2, eventType, pageNumber)
    }
  }

  companion object {
    private const val SWIPE_MIN_DISTANCE_DP = 48f
  }
}
