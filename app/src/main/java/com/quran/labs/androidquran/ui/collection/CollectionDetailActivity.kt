package com.quran.labs.androidquran.ui.collection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.data.ReaderReadinessTracker
import com.quran.labs.androidquran.ui.helpers.QuranNavigator
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.collection.AyahCollectionDao
import com.quran.mobile.collection.model.AyahCollection
import com.quran.mobile.collection.model.AyahCollectionEntry
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

/**
 * Shows the named entries of one [AyahCollection]. Entries can be reordered by long-press
 * dragging, renamed or deleted from their overflow menu, and opened in the reader with the saved
 * ayah range highlighted.
 */
class CollectionDetailActivity : AppCompatActivity() {

  @Inject lateinit var collectionDao: AyahCollectionDao
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranNavigatorFactory: QuranNavigator.Factory
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var readerReadinessTracker: ReaderReadinessTracker

  private val quranNavigator by lazy { quranNavigatorFactory.create(this) }

  private lateinit var adapter: CollectionEntriesAdapter
  private lateinit var entriesList: RecyclerView
  private lateinit var emptyView: View
  private var collectionId: Long = -1
  private var collection: AyahCollection? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    (application as QuranApplication).applicationComponent.inject(this)
    setContentView(R.layout.activity_collection_detail)

    collectionId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1L)
    if (collectionId <= 0L) {
      finish()
      return
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    entriesList = findViewById(R.id.entries_list)
    emptyView = findViewById(R.id.empty_entries)
    adapter = CollectionEntriesAdapter(
      quranDisplayData = quranDisplayData,
      onClick = ::openEntry,
      onRename = { entry ->
        CollectionDialogs.showRenameEntry(this, collectionDao, entry)
      },
      onDelete = { entry ->
        CollectionDialogs.showDeleteEntry(this, collectionDao, entry)
      }
    )
    entriesList.layoutManager = LinearLayoutManager(this)
    entriesList.adapter = adapter
    setupReorder()

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
      entriesList.setPadding(
        entriesList.paddingLeft,
        entriesList.paddingTop,
        entriesList.paddingRight,
        insets.bottom + dp(16)
      )
      windowInsets
    }

    observeCollection()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    menuInflater.inflate(R.menu.collection_detail_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        finish()
        true
      }

      R.id.action_read_collection -> {
        startActivity(CollectionReadingActivity.intent(this, collectionId))
        true
      }

      R.id.action_rename_collection -> {
        collection?.let {
          CollectionDialogs.showRenameCollection(this, collectionDao, it)
        }
        true
      }

      R.id.action_delete_collection -> {
        collection?.let {
          CollectionDialogs.showDeleteCollection(this, collectionDao, it) { finish() }
        }
        true
      }

      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun observeCollection() {
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          collectionDao.collectionFlow(collectionId).collect { current ->
            collection = current
            if (current == null) {
              finish()
            } else {
              supportActionBar?.title = current.name
            }
          }
        }
        launch {
          collectionDao.entriesFlow(collectionId).collect { entries ->
            adapter.submitList(entries)
            emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
          }
        }
      }
    }
  }

  private fun setupReorder() {
    val callback = object : ItemTouchHelper.SimpleCallback(
      ItemTouchHelper.UP or ItemTouchHelper.DOWN,
      0
    ) {
      override fun isLongPressDragEnabled(): Boolean = true

      override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
      ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
          return false
        }
        adapter.moveItem(from, to)
        return true
      }

      override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

      override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        persistOrder()
      }
    }
    ItemTouchHelper(callback).attachToRecyclerView(entriesList)
  }

  private fun persistOrder() {
    lifecycleScope.launch {
      collectionDao.reorderEntries(adapter.currentIds())
    }
  }

  private fun openEntry(entry: AyahCollectionEntry) {
    if (canOpenReaderDirectly()) {
      quranNavigator.jumpTo(entry.start, entry.end, showTranslation = false)
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
      return Intent(context, CollectionDetailActivity::class.java)
        .putExtra(EXTRA_COLLECTION_ID, collectionId)
    }
  }
}
