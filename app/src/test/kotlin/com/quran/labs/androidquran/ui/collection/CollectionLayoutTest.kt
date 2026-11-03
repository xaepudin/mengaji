package com.quran.labs.androidquran.ui.collection

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Inflates the collection screens and rows so missing resources, theme attributes, or layout IDs
 * fail in unit tests instead of at runtime.
 */
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class CollectionLayoutTest {

  private val context: Context = ContextThemeWrapper(
    ApplicationProvider.getApplicationContext(),
    R.style.QuranToolBarDayNight
  )
  private val inflater: LayoutInflater = LayoutInflater.from(context)

  @Test
  fun `inflates the collections tab`() {
    val view = inflater.inflate(R.layout.fragment_collections, null)
    assertThat(view.findViewById<View>(R.id.collections_list)).isNotNull()
    assertThat(view.findViewById<View>(R.id.new_collection)).isNotNull()
  }

  @Test
  fun `inflates a collection row and the detail screen`() {
    val row = inflater.inflate(R.layout.item_collection, null)
    assertThat(row.findViewById<View>(R.id.collection_name)).isNotNull()

    val detail = inflater.inflate(R.layout.activity_collection_detail, null)
    assertThat(detail.findViewById<View>(R.id.entries_list)).isNotNull()
  }

  @Test
  fun `inflates an entry row and the reading screen`() {
    val row = inflater.inflate(R.layout.item_collection_entry, null)
    assertThat(row.findViewById<View>(R.id.entry_number)).isNotNull()
    assertThat(row.findViewById<View>(R.id.entry_page)).isNotNull()

    val reading = inflater.inflate(R.layout.activity_collection_reading, null)
    assertThat(reading.findViewById<View>(R.id.reading_list)).isNotNull()
    assertThat(reading.findViewById<View>(R.id.reading_progress)).isNotNull()
  }

  @Test
  fun `inflates the collection dialogs`() {
    assertThat(inflater.inflate(R.layout.dialog_edit_text, null)).isNotNull()
    assertThat(inflater.inflate(R.layout.dialog_add_to_collection_entry, null)).isNotNull()
  }
}
