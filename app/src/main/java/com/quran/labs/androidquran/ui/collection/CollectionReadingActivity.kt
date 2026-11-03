package com.quran.labs.androidquran.ui.collection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ayahsearch.AyahSearchAdapter
import com.quran.labs.androidquran.ayahsearch.AyahSearchResult
import com.quran.labs.androidquran.ayahsearch.AyahSearchTranslationRepository
import com.quran.labs.androidquran.ayahsearch.LafziSearchRepository
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.data.ReaderReadinessTracker
import com.quran.labs.androidquran.ui.helpers.QuranNavigator
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.collection.AyahCollectionDao
import com.quran.mobile.collection.model.AyahCollection
import com.quran.mobile.collection.model.AyahCollectionEntry
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Read-only view of every ayah in a collection.
 *
 * Ayahs from all entries are expanded, de-duplicated in entry order, and shown in the same card
 * style as ayah search results. Tapping a card opens that ayah in the reader.
 */
class CollectionReadingActivity : AppCompatActivity() {

  @Inject lateinit var collectionDao: AyahCollectionDao
  @Inject lateinit var lafziSearchRepository: LafziSearchRepository
  @Inject lateinit var translationRepository: AyahSearchTranslationRepository
  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranNavigatorFactory: QuranNavigator.Factory
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var readerReadinessTracker: ReaderReadinessTracker

  private val quranNavigator by lazy { quranNavigatorFactory.create(this) }

  private lateinit var adapter: AyahSearchAdapter
  private lateinit var progress: ProgressBar
  private lateinit var emptyView: View
  private var collectionId: Long = -1

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    (application as QuranApplication).applicationComponent.inject(this)
    setContentView(R.layout.activity_collection_reading)

    collectionId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1L)
    if (collectionId <= 0L) {
      finish()
      return
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    val readingList = findViewById<RecyclerView>(R.id.reading_list)
    progress = findViewById(R.id.reading_progress)
    emptyView = findViewById(R.id.reading_empty)
    adapter = AyahSearchAdapter(
      context = this,
      quranInfo = quranInfo,
      quranDisplayData = quranDisplayData,
      onResultClick = ::openAyah
    )
    readingList.layoutManager = LinearLayoutManager(this)
    readingList.adapter = adapter

    val root = findViewById<View>(R.id.root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        leftMargin = insets.left
        rightMargin = insets.right
      }
      readingList.setPadding(
        readingList.paddingLeft,
        readingList.paddingTop,
        readingList.paddingRight,
        insets.bottom + dp(16)
      )
      windowInsets
    }

    loadCollection()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private fun loadCollection() {
    lifecycleScope.launch {
      val loaded = try {
        withContext(Dispatchers.IO) {
          val collection = collectionDao.collection(collectionId)
            ?: return@withContext null
          val entries = collectionDao.entries(collectionId)
          val ids = collectAyahIds(entries)
          val texts = if (ids.isEmpty()) {
            emptyList()
          } else {
            lafziSearchRepository.ayahs(ids)
          }
          val translations = if (
            ids.isEmpty() || !quranSettings.isAyahSearchShowTranslation()
          ) {
            emptyMap()
          } else {
            translationRepository.translationsFor(ids)
          }
          collection to texts.map { text ->
            AyahSearchResult(
              ayahId = text.ayahId,
              sura = text.sura,
              ayah = text.ayah,
              arabicText = text.text,
              highlightRanges = emptyList(),
              relevance = 0,
              translation = translations[text.ayahId]
            )
          }
        }
      } catch (exception: Exception) {
        Timber.e(exception, "Unable to load collection reading data")
        null
      }

      if (loaded == null) {
        finish()
        return@launch
      }

      val (collection, results) = loaded
      showCollectionTitle(collection)
      progress.visibility = View.GONE
      if (results.isEmpty()) {
        emptyView.visibility = View.VISIBLE
      } else {
        adapter.submitItems(results)
      }
    }
  }

  private fun showCollectionTitle(collection: AyahCollection) {
    supportActionBar?.title = collection.name
    supportActionBar?.subtitle = getString(R.string.collection_reading)
  }

  private fun collectAyahIds(entries: List<AyahCollectionEntry>): List<Int> {
    val ids = LinkedHashSet<Int>()
    for (entry in entries) {
      if (entry.startSura == entry.endSura) {
        if (entry.startAyah > entry.endAyah) {
          continue
        }
        for (ayah in entry.startAyah..entry.endAyah) {
          ids.add(quranInfo.getAyahId(entry.startSura, ayah))
        }
      } else {
        for (ayah in entry.startAyah..quranInfo.getNumberOfAyahs(entry.startSura)) {
          ids.add(quranInfo.getAyahId(entry.startSura, ayah))
        }
        for (sura in entry.startSura + 1 until entry.endSura) {
          for (ayah in 1..quranInfo.getNumberOfAyahs(sura)) {
            ids.add(quranInfo.getAyahId(sura, ayah))
          }
        }
        for (ayah in 1..entry.endAyah) {
          ids.add(quranInfo.getAyahId(entry.endSura, ayah))
        }
      }
    }
    return ids.toList()
  }

  private fun openAyah(result: AyahSearchResult) {
    if (canOpenReaderDirectly()) {
      quranNavigator.jumpTo(SuraAyah(result.sura, result.ayah), showTranslation = false)
    } else {
      startActivity(Intent(this, QuranDataActivity::class.java))
      finish()
    }
  }

  private fun canOpenReaderDirectly(): Boolean {
    return quranSettings.haveMigratedLegacyBookmarksToMobileSync() &&
      readerReadinessTracker.isReady(quranSettings.pageType)
  }

  private fun dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
  }

  companion object {
    private const val EXTRA_COLLECTION_ID = "collection_id"

    fun intent(context: Context, collectionId: Long): Intent {
      return Intent(context, CollectionReadingActivity::class.java)
        .putExtra(EXTRA_COLLECTION_ID, collectionId)
    }
  }
}
