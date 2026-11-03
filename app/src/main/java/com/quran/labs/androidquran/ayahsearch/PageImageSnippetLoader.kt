package com.quran.labs.androidquran.ayahsearch

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.data.AyahInfoDatabaseProvider
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.view.NightModeColorMatrix
import com.quran.page.common.data.AyahCoordinates
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads a cropped image of an ayah directly from the downloaded Mushaf page.
 *
 * This makes search results and collection reading show the exact page calligraphy instead of a
 * font-rendered approximation. If the page image or its ayah coordinates are unavailable, callers
 * fall back to the text rendering.
 */
@SingleIn(AppScope::class)
class PageImageSnippetLoader @Inject constructor(
  @ApplicationContext private val context: Context,
  private val quranFileUtils: QuranFileUtils,
  quranScreenInfo: QuranScreenInfo,
  private val quranSettings: QuranSettings
) {
  private val widthParam = quranScreenInfo.widthParam
  private val ayahInfoDatabaseProvider by lazy {
    AyahInfoDatabaseProvider(context, widthParam, quranFileUtils)
  }

  private val lock = Any()
  private val pageCache = LruCache<Int, Bitmap>(2)
  private val coordinatesCache = LruCache<Int, AyahCoordinates>(4)
  private val snippetCache = LruCache<String, Bitmap>(20)

  /**
   * Returns a page image crop for the ayah, scaled so the ayah keeps its page-relative size.
   *
   * [targetWidth] is the content width of the result card in pixels.
   */
  suspend fun load(
    page: Int,
    sura: Int,
    ayah: Int,
    targetWidth: Int
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (targetWidth <= 0) {
      return@withContext null
    }

    val cacheKey = "$page:$sura:$ayah:$targetWidth"
    synchronized(lock) { snippetCache.get(cacheKey) }?.let { return@withContext it }

    val pageBitmap = pageBitmap(page) ?: return@withContext null
    val coordinates = coordinates(page) ?: return@withContext null
    val bounds = coordinates.ayahCoordinates["$sura:$ayah"] ?: return@withContext null
    if (bounds.isEmpty()) {
      return@withContext null
    }

    val segments = bounds.map { it.bounds }
    val union = RectF(segments.first())
    for (index in 1 until segments.size) {
      union.union(segments[index])
    }

    val padding = (pageBitmap.width * 0.004f).toInt().coerceAtLeast(4)
    val left = (union.left - padding).toInt().coerceIn(0, pageBitmap.width - 1)
    val top = (union.top - padding).toInt().coerceIn(0, pageBitmap.height - 1)
    val right = (union.right + padding).toInt().coerceIn(left + 1, pageBitmap.width)
    val bottom = (union.bottom + padding).toInt().coerceIn(top + 1, pageBitmap.height)

    // Build the snippet line by line so a multi-line ayah does not pull in the neighbouring
    // ayahs that share its first/last lines.
    val cropped = try {
      val composite = Bitmap.createBitmap(
        right - left,
        bottom - top,
        Bitmap.Config.RGB_565
      )
      composite.eraseColor(pageBitmap.getPixel(left, top))
      val canvas = Canvas(composite)
      val paint = Paint().apply { isFilterBitmap = true }
      for (segment in segments) {
        val srcLeft = segment.left.toInt().coerceIn(0, pageBitmap.width - 1)
        val srcTop = segment.top.toInt().coerceIn(0, pageBitmap.height - 1)
        val srcRight = segment.right.toInt().coerceIn(srcLeft + 1, pageBitmap.width)
        val srcBottom = segment.bottom.toInt().coerceIn(srcTop + 1, pageBitmap.height)
        val source = Rect(srcLeft, srcTop, srcRight, srcBottom)
        val destination = Rect(
          srcLeft - left,
          srcTop - top,
          srcRight - left,
          srcBottom - top
        )
        canvas.drawBitmap(pageBitmap, source, destination, paint)
      }
      composite
    } catch (exception: IllegalArgumentException) {
      return@withContext null
    } catch (exception: OutOfMemoryError) {
      return@withContext null
    }

    val scale = targetWidth.toFloat() / pageBitmap.width
    val scaledWidth = (cropped.width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (cropped.height * scale).toInt().coerceAtLeast(1)
    val scaled = if (scaledWidth != cropped.width || scaledHeight != cropped.height) {
      Bitmap.createScaledBitmap(cropped, scaledWidth, scaledHeight, true).also {
        if (it !== cropped) {
          cropped.recycle()
        }
      }
    } else {
      cropped
    }

    synchronized(lock) { snippetCache.put(cacheKey, scaled) }
    scaled
  }

  fun nightModeFilter(context: Context): ColorFilter? {
    // Match the app UI, not the separate reader page theme: a dark reader setting should not
    // invert page snippets on a light search/collection screen. Use the view's context so an
    // app-level light/dark override is respected instead of the system configuration.
    val uiNightMode = (context.resources.configuration.uiMode and
      Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    if (!uiNightMode) {
      return null
    }
    return ColorMatrixColorFilter(
      NightModeColorMatrix.create(
        quranSettings.nightModeTextBrightness,
        quranSettings.nightModeBackgroundBrightness
      )
    )
  }

  private fun pageBitmap(page: Int): Bitmap? {
    synchronized(lock) { pageCache.get(page) }?.let { return it }

    val file = File(quranFileUtils.getQuranImagesDirectory(widthParam), QuranFileUtils.getPageFileName(page))
    if (!file.exists()) {
      return null
    }
    val options = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    synchronized(lock) { pageCache.put(page, bitmap) }
    return bitmap
  }

  private fun coordinates(page: Int): AyahCoordinates? {
    synchronized(lock) { coordinatesCache.get(page) }?.let { return it }

    val handler = ayahInfoDatabaseProvider.getAyahInfoHandler() ?: return null
    val coordinates = try {
      handler.getVersesBoundsForPage(page)
    } catch (exception: Exception) {
      return null
    }
    synchronized(lock) { coordinatesCache.put(page, coordinates) }
    return coordinates
  }
}
