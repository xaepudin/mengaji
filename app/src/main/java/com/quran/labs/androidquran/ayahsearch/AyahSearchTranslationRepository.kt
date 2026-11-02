package com.quran.labs.androidquran.ayahsearch

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Loads the user's currently selected translation for ayah search results.
 *
 * When more than one translation is active, the first one in the translation manager's display
 * order is used, matching what the reader shows first.
 */
@SingleIn(AppScope::class)
class AyahSearchTranslationRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val quranFileUtils: QuranFileUtils,
  private val quranSettings: QuranSettings,
  private val translationsDBAdapter: TranslationsDBAdapter
) {

  suspend fun translationsFor(ayahIds: List<Int>): Map<Int, String> {
    if (ayahIds.isEmpty()) return emptyMap()

    val activeTranslations = quranSettings.activeTranslations
    if (activeTranslations.isEmpty()) return emptyMap()

    return withContext(Dispatchers.IO) {
      try {
        val translation = translationsDBAdapter.getTranslations().first()
          .sortedBy { it.displayOrder }
          .firstOrNull { activeTranslations.contains(it.filename) }
          ?: return@withContext emptyMap()

        DatabaseHandler.getDatabaseHandler(context, translation.filename, quranFileUtils)
          .getTranslationTexts(ayahIds)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Timber.w(exception, "Unable to load translation for ayah search results")
        emptyMap()
      }
    }
  }
}
