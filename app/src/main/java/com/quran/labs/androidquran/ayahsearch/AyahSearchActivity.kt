package com.quran.labs.androidquran.ayahsearch

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.data.ReaderReadinessTracker
import com.quran.labs.androidquran.ui.helpers.QuranNavigator
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Lafzi style ayah search: type how an ayah sounds (for example `bismillah`) and pick the ayah
 * from the result list to open it in the reader.
 *
 * Latin queries use Lafzi's phonetic trigram index. Arabic queries use the bundled FTS index.
 */
class AyahSearchActivity : AppCompatActivity() {

  @Inject lateinit var lafziSearchRepository: LafziSearchRepository
  @Inject lateinit var pageSnippetLoader: PageImageSnippetLoader
  @Inject lateinit var ayahSearchTranslationRepository: AyahSearchTranslationRepository
  @Inject lateinit var quranNavigatorFactory: QuranNavigator.Factory
  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var readerReadinessTracker: ReaderReadinessTracker

  private val quranNavigator by lazy { quranNavigatorFactory.create(this) }

  private lateinit var searchInputLayout: TextInputLayout
  private lateinit var searchInput: TextInputEditText
  private lateinit var statusView: TextView
  private lateinit var emptyState: View
  private lateinit var emptyText: TextView
  private lateinit var progress: ProgressBar
  private lateinit var resultsList: RecyclerView
  private lateinit var adapter: AyahSearchAdapter

  private var searchJob: Job? = null
  private var lastQuery: String? = null
  private var isVocal = true

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    (application as QuranApplication).applicationComponent.inject(this)
    setContentView(R.layout.activity_ayah_search)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    searchInputLayout = findViewById(R.id.search_input_layout)
    searchInput = findViewById(R.id.search_input)
    statusView = findViewById(R.id.status_area)
    emptyState = findViewById(R.id.empty_state)
    emptyText = findViewById(R.id.empty_text)
    progress = findViewById(R.id.search_progress)
    resultsList = findViewById(R.id.results_list)

    adapter = AyahSearchAdapter(
      context = this,
      quranInfo = quranInfo,
      quranDisplayData = quranDisplayData,
      pageSnippetLoader = pageSnippetLoader,
      scope = lifecycleScope,
      onResultClick = ::openResult
    )
    resultsList.layoutManager = LinearLayoutManager(this)
    resultsList.adapter = adapter

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
      resultsList.setPadding(
        resultsList.paddingLeft,
        resultsList.paddingTop,
        resultsList.paddingRight,
        insets.bottom + dp(16)
      )
      windowInsets
    }

    isVocal = savedInstanceState?.getBoolean(STATE_VOCAL)
      ?: quranSettings.isAyahSearchVocal()

    setupSearchInput()
    prepareSearchDatabase()

    val restoredQuery = savedInstanceState?.getString(STATE_QUERY)
    if (!restoredQuery.isNullOrBlank()) {
      searchInput.setText(restoredQuery)
      searchInput.setSelection(restoredQuery.length)
      submitSearch(restoredQuery)
    } else {
      focusSearchInput()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    menuInflater.inflate(R.menu.ayah_search_menu, menu)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    menu.findItem(R.id.action_vocal_search)?.isChecked = isVocal
    menu.findItem(R.id.action_show_translation)?.isChecked =
      quranSettings.isAyahSearchShowTranslation()
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        finish()
        true
      }

      R.id.action_vocal_search -> {
        isVocal = !isVocal
        item.isChecked = isVocal
        quranSettings.setAyahSearchVocal(isVocal)
        rerunCurrentSearch()
        true
      }

      R.id.action_show_translation -> {
        val showTranslation = !quranSettings.isAyahSearchShowTranslation()
        item.isChecked = showTranslation
        quranSettings.setAyahSearchShowTranslation(showTranslation)
        rerunCurrentSearch()
        true
      }

      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(STATE_QUERY, lastQuery ?: searchInput.text?.toString())
    outState.putBoolean(STATE_VOCAL, isVocal)
  }

  /**
   * The database is copied out of the APK asset on first use. Start that while the user is still
   * typing so the first search does not have to wait for it.
   */
  private fun prepareSearchDatabase() {
    lifecycleScope.launch {
      try {
        lafziSearchRepository.warmUp()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Timber.w(exception, "Unable to prepare the ayah search database")
      }
    }
  }

  private fun setupSearchInput() {
    searchInput.setOnEditorActionListener { _, actionId, event ->
      if (actionId == EditorInfo.IME_ACTION_SEARCH ||
        (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
          event.action == KeyEvent.ACTION_DOWN)
      ) {
        submitSearch()
        true
      } else {
        false
      }
    }

    searchInput.doAfterTextChanged { editable ->
      searchInputLayout.error = null
      if (editable.isNullOrEmpty() && lastQuery != null) {
        searchJob?.cancel()
        resetToInitialState()
      }
    }
  }

  private fun rerunCurrentSearch() {
    val query = lastQuery ?: searchInput.text?.toString()?.trim()
    if (!query.isNullOrEmpty() && query.length >= MIN_QUERY_LENGTH) {
      submitSearch(query)
    }
  }

  private fun submitSearch(rawQuery: String? = searchInput.text?.toString()) {
    val query = rawQuery?.trim().orEmpty()
    if (query.length < MIN_QUERY_LENGTH) {
      searchInputLayout.error = getString(R.string.ayah_search_query_too_short)
      return
    }

    searchInputLayout.error = null
    lastQuery = query
    hideKeyboard()

    progress.visibility = View.VISIBLE
    emptyState.visibility = View.GONE
    statusView.visibility = View.VISIBLE
    statusView.text = getString(R.string.ayah_search_searching)
    adapter.clear()

    searchJob?.cancel()
    searchJob = lifecycleScope.launch {
      try {
        val results = lafziSearchRepository.search(query, isVocal)
        renderResults(query, attachTranslations(results))
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Timber.e(exception, "Ayah search failed")
        renderError()
      }
    }
  }

  private suspend fun attachTranslations(
    results: List<AyahSearchResult>
  ): List<AyahSearchResult> {
    if (results.isEmpty() || !quranSettings.isAyahSearchShowTranslation()) {
      return results
    }

    val translations = ayahSearchTranslationRepository.translationsFor(
      results.map { it.ayahId }
    )
    if (translations.isEmpty()) {
      return results
    }
    return results.map { result ->
      result.copy(translation = translations[result.ayahId])
    }
  }

  private fun renderResults(query: String, results: List<AyahSearchResult>) {
    progress.visibility = View.GONE
    if (results.isEmpty()) {
      statusView.visibility = View.GONE
      emptyText.text = getString(R.string.ayah_search_no_results, query)
      emptyState.visibility = View.VISIBLE
      resultsList.scrollToPosition(0)
      return
    }

    adapter.submitItems(results)
    statusView.visibility = View.VISIBLE
    statusView.text = resources.getQuantityString(
      R.plurals.ayah_search_result_count,
      results.size,
      results.size
    )
    emptyState.visibility = View.GONE
    resultsList.scrollToPosition(0)
  }

  private fun renderError() {
    progress.visibility = View.GONE
    statusView.visibility = View.VISIBLE
    statusView.text = getString(R.string.ayah_search_error)
    emptyState.visibility = View.GONE
  }

  private fun resetToInitialState() {
    lastQuery = null
    progress.visibility = View.GONE
    adapter.clear()
    statusView.visibility = View.VISIBLE
    statusView.text = getString(R.string.ayah_search_initial)
    emptyState.visibility = View.GONE
  }

  private fun focusSearchInput() {
    searchInput.requestFocus()
    searchInput.post {
      val inputMethodManager = getSystemService(InputMethodManager::class.java)
      inputMethodManager?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }
  }

  private fun hideKeyboard() {
    val inputMethodManager = getSystemService(InputMethodManager::class.java)
    inputMethodManager?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    searchInput.clearFocus()
  }

  private fun openResult(result: AyahSearchResult) {
    hideKeyboard()
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

  private companion object {
    const val MIN_QUERY_LENGTH = 3
    const val STATE_QUERY = "STATE_QUERY"
    const val STATE_VOCAL = "STATE_VOCAL"
  }
}
