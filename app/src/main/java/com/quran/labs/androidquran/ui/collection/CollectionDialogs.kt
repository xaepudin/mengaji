package com.quran.labs.androidquran.ui.collection

import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.mobile.collection.AyahCollectionDao
import com.quran.mobile.collection.model.AyahCollection
import com.quran.mobile.collection.model.AyahCollectionEntry
import kotlinx.coroutines.launch

/**
 * Dialogs for creating, renaming, deleting, and adding to collections.
 *
 * These are shared by the Collections tab, the collection detail screen, and the ayah selection
 * toolbar in the reader.
 */
object CollectionDialogs {

  fun showCreateCollection(
    activity: FragmentActivity,
    dao: AyahCollectionDao,
    onCreated: (AyahCollection) -> Unit = {}
  ) {
    showTextInputDialog(
      activity = activity,
      titleRes = R.string.collection_new,
      hintRes = R.string.collection_name_hint,
      initialText = "",
      positiveRes = R.string.collection_create,
      requiredRes = R.string.collection_name_required
    ) { name ->
      activity.lifecycleScope.launch {
        val id = dao.createCollection(name)
        onCreated(AyahCollection(id = id, name = name, entryCount = 0))
      }
    }
  }

  fun showRenameCollection(
    activity: FragmentActivity,
    dao: AyahCollectionDao,
    collection: AyahCollection,
    onRenamed: () -> Unit = {}
  ) {
    showTextInputDialog(
      activity = activity,
      titleRes = R.string.collection_rename,
      hintRes = R.string.collection_name_hint,
      initialText = collection.name,
      positiveRes = R.string.collection_save,
      requiredRes = R.string.collection_name_required
    ) { name ->
      activity.lifecycleScope.launch {
        dao.renameCollection(collection.id, name)
        onRenamed()
      }
    }
  }

  fun showDeleteCollection(
    activity: FragmentActivity,
    dao: AyahCollectionDao,
    collection: AyahCollection,
    onDeleted: () -> Unit = {}
  ) {
    AlertDialog.Builder(activity)
      .setTitle(R.string.collection_delete)
      .setMessage(activity.getString(R.string.collection_delete_message, collection.name))
      .setPositiveButton(R.string.delete) { _, _ ->
        activity.lifecycleScope.launch {
          dao.deleteCollection(collection.id)
          onDeleted()
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  fun showRenameEntry(
    activity: FragmentActivity,
    dao: AyahCollectionDao,
    entry: AyahCollectionEntry,
    onRenamed: () -> Unit = {}
  ) {
    showTextInputDialog(
      activity = activity,
      titleRes = R.string.collection_entry_rename,
      hintRes = R.string.collection_entry_name,
      initialText = entry.name,
      positiveRes = R.string.collection_save,
      requiredRes = R.string.collection_entry_name_required
    ) { name ->
      activity.lifecycleScope.launch {
        dao.renameEntry(entry.id, name)
        onRenamed()
      }
    }
  }

  fun showDeleteEntry(
    activity: FragmentActivity,
    dao: AyahCollectionDao,
    entry: AyahCollectionEntry,
    onDeleted: () -> Unit = {}
  ) {
    AlertDialog.Builder(activity)
      .setTitle(R.string.collection_entry_delete)
      .setMessage(activity.getString(R.string.collection_entry_delete_message, entry.name))
      .setPositiveButton(R.string.delete) { _, _ ->
        activity.lifecycleScope.launch {
          dao.deleteEntry(entry.id)
          onDeleted()
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  fun showAddToCollection(
    activity: FragmentActivity,
    dao: AyahCollectionDao,
    quranDisplayData: QuranDisplayData,
    start: SuraAyah,
    end: SuraAyah,
    onAdded: (AyahCollection) -> Unit
  ) {
    activity.lifecycleScope.launch {
      val collections = dao.collections()
      val labels = collections.map { it.name } + activity.getString(R.string.collection_new)
      val builder = AlertDialog.Builder(activity)
        .setTitle(R.string.add_to_collection)
        .setItems(labels.toTypedArray()) { _, which ->
          if (which < collections.size) {
            showEntryName(
              activity,
              dao,
              quranDisplayData,
              collections[which],
              start,
              end,
              onAdded
            )
          } else {
            showCreateCollection(activity, dao) { created ->
              showEntryName(
                activity,
                dao,
                quranDisplayData,
                created,
                start,
                end,
                onAdded
              )
            }
          }
        }
        .setNegativeButton(android.R.string.cancel, null)
      if (collections.isEmpty()) {
        builder.setMessage(R.string.collection_list_empty)
      }
      builder.show()
    }
  }

  private fun showEntryName(
    activity: FragmentActivity,
    dao: AyahCollectionDao,
    quranDisplayData: QuranDisplayData,
    collection: AyahCollection,
    start: SuraAyah,
    end: SuraAyah,
    onAdded: (AyahCollection) -> Unit
  ) {
    val view = LayoutInflater.from(activity)
      .inflate(R.layout.dialog_add_to_collection_entry, null)
    val rangeInfo = view.findViewById<TextView>(R.id.range_info)
    val inputLayout = view.findViewById<TextInputLayout>(R.id.text_input_layout)
    val input = view.findViewById<TextInputEditText>(R.id.text_input)

    val rangeLabel = collectionRangeLabel(activity, quranDisplayData, start, end)
    rangeInfo.text = rangeLabel
    inputLayout.hint = activity.getString(R.string.collection_entry_name)
    input.setText(rangeLabel)
    input.setSelection(rangeLabel.length)

    val dialog = AlertDialog.Builder(activity)
      .setTitle(R.string.add_to_collection)
      .setView(view)
      .setPositiveButton(R.string.collection_entry_add, null)
      .setNegativeButton(android.R.string.cancel, null)
      .create()
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val name = input.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
          inputLayout.error = activity.getString(R.string.collection_entry_name_required)
          return@setOnClickListener
        }
        dialog.dismiss()
        activity.lifecycleScope.launch {
          dao.addEntry(collection.id, name, start, end)
          onAdded(collection)
        }
      }
    }
    dialog.show()
  }

  private fun showTextInputDialog(
    activity: FragmentActivity,
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    initialText: String,
    @StringRes positiveRes: Int,
    @StringRes requiredRes: Int,
    onPositive: (String) -> Unit
  ) {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_text, null)
    val inputLayout = view.findViewById<TextInputLayout>(R.id.text_input_layout)
    val input = view.findViewById<TextInputEditText>(R.id.text_input)

    inputLayout.hint = activity.getString(hintRes)
    input.setText(initialText)
    input.setSelection(initialText.length)

    val dialog = AlertDialog.Builder(activity)
      .setTitle(titleRes)
      .setView(view)
      .setPositiveButton(positiveRes, null)
      .setNegativeButton(android.R.string.cancel, null)
      .create()
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
          inputLayout.error = activity.getString(requiredRes)
          return@setOnClickListener
        }
        dialog.dismiss()
        onPositive(text)
      }
    }
    dialog.show()
  }
}
