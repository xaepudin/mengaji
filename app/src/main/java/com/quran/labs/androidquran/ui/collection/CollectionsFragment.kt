package com.quran.labs.androidquran.ui.collection

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.mobile.collection.AyahCollectionDao
import com.quran.mobile.collection.model.AyahCollection
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

class CollectionsFragment : Fragment() {

  @Inject
  lateinit var collectionDao: AyahCollectionDao

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return inflater.inflate(R.layout.fragment_collections, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val recyclerView = view.findViewById<RecyclerView>(R.id.collections_list)
    val emptyView = view.findViewById<View>(R.id.empty_collections)
    val adapter = CollectionsAdapter(
      onClick = { collection ->
        startActivity(CollectionDetailActivity.intent(requireContext(), collection.id))
      },
      onLongClick = ::showCollectionOptions
    )
    recyclerView.layoutManager = LinearLayoutManager(requireContext())
    recyclerView.adapter = adapter

    view.findViewById<FloatingActionButton>(R.id.new_collection).setOnClickListener {
      CollectionDialogs.showCreateCollection(requireActivity(), collectionDao)
    }

    ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
      val bottom = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      ).bottom
      val fab = root.findViewById<FloatingActionButton>(R.id.new_collection)
      (fab.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottom + dp(16)
      recyclerView.setPadding(0, 0, 0, bottom + dp(88))
      insets
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        collectionDao.collectionsFlow().collect { collections ->
          adapter.submitList(collections)
          emptyView.visibility = if (collections.isEmpty()) View.VISIBLE else View.GONE
        }
      }
    }
  }

  private fun dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
  }

  private fun showCollectionOptions(collection: AyahCollection) {
    AlertDialog.Builder(requireContext())
      .setTitle(collection.name)
      .setItems(
        arrayOf(
          getString(R.string.collection_rename),
          getString(R.string.collection_delete)
        )
      ) { _, which ->
        if (which == 0) {
          CollectionDialogs.showRenameCollection(requireActivity(), collectionDao, collection)
        } else {
          CollectionDialogs.showDeleteCollection(requireActivity(), collectionDao, collection)
        }
      }
      .show()
  }

  companion object {
    fun newInstance(): CollectionsFragment = CollectionsFragment()
  }
}
