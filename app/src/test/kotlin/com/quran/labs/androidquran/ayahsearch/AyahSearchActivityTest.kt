package com.quran.labs.androidquran.ayahsearch

import android.content.Context
import android.os.Looper
import android.preference.PreferenceManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.util.QuranSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AyahSearchActivityTest {

  @Test
  fun `inflates the search screen with an empty initial state`() {
    Robolectric.buildActivity(AyahSearchActivity::class.java).use { controller ->
      val activity = controller.setup().get()

      assertThat(activity.findViewById<View>(R.id.search_input)).isNotNull()
      assertThat(activity.findViewById<View>(R.id.search_input_layout)).isNotNull()
      assertThat(activity.findViewById<View>(R.id.results_list)).isNotNull()
      assertThat(activity.findViewById<View>(R.id.status_area)).isNotNull()
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(activity.findViewById<View>(R.id.search_input).hasFocus()).isTrue()
      assertThat(activity.isFinishing).isFalse()
    }
  }

  @Test
  fun `pressing search populates the result list`() {
    Robolectric.buildActivity(AyahSearchActivity::class.java).use { controller ->
      val activity = controller.setup().get()
      val input = activity.findViewById<TextInputEditText>(R.id.search_input)
      input.setText("bismillah")
      input.onEditorAction(EditorInfo.IME_ACTION_SEARCH)

      val results = activity.findViewById<RecyclerView>(R.id.results_list)
      val deadline = System.currentTimeMillis() + 30_000
      while (System.currentTimeMillis() < deadline && (results.adapter?.itemCount ?: 0) == 0) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(25)
      }
      shadowOf(Looper.getMainLooper()).idle()

      assertThat(results.adapter?.itemCount ?: 0).isGreaterThan(0)
    }
  }

  @Test
  fun `show translation preference uses the settings key`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val key = context.getString(R.string.prefs_ayah_search_show_translation)
    preferences.edit().remove(key).commit()

    // QuranSettings is a process-wide singleton; make sure this test reads the current test context.
    QuranSettings.setInstance(null)
    val settings = QuranSettings.getInstance(context)
    assertThat(settings.isAyahSearchShowTranslation()).isTrue()

    preferences.edit().putBoolean(key, false).commit()
    assertThat(settings.isAyahSearchShowTranslation()).isFalse()

    preferences.edit().putBoolean(key, true).commit()
    QuranSettings.setInstance(null)
  }

  @Test
  fun `show similarity preference defaults to false and uses the settings key`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val key = context.getString(R.string.prefs_ayah_search_show_similarity)
    preferences.edit().remove(key).commit()

    QuranSettings.setInstance(null)
    val settings = QuranSettings.getInstance(context)
    assertThat(settings.isAyahSearchShowSimilarity()).isFalse()

    preferences.edit().putBoolean(key, true).commit()
    assertThat(settings.isAyahSearchShowSimilarity()).isTrue()

    preferences.edit().putBoolean(key, false).commit()
    QuranSettings.setInstance(null)
  }

  @Test
  fun `similarity badge is hidden by default and shown when enabled`() {
    Robolectric.buildActivity(AyahSearchActivity::class.java).use { controller ->
      val activity = controller.setup().get()
      val adapter = AyahSearchAdapter(
        context = activity,
        quranInfo = activity.quranInfo,
        quranDisplayData = activity.quranDisplayData,
        onResultClick = {}
      )
      adapter.submitItems(
        listOf(
          AyahSearchResult(
            ayahId = 1,
            sura = 1,
            ayah = 1,
            arabicText = "بِسْمِ اللَّهِ",
            highlightRanges = emptyList(),
            relevance = 87
          )
        )
      )

      val parent = FrameLayout(activity)
      val holder = adapter.onCreateViewHolder(parent, 0)
      adapter.onBindViewHolder(holder, 0)
      val badge = holder.itemView.findViewById<TextView>(R.id.result_similarity)
      val bar = holder.itemView.findViewById<LinearProgressIndicator>(R.id.result_similarity_bar)
      assertThat(badge.visibility).isEqualTo(View.GONE)
      assertThat(bar.visibility).isEqualTo(View.GONE)

      adapter.showSimilarity = true
      adapter.onBindViewHolder(holder, 0)
      assertThat(badge.visibility).isEqualTo(View.VISIBLE)
      assertThat(badge.text.toString())
        .isEqualTo(activity.getString(R.string.ayah_search_similarity, 87))
      assertThat(bar.visibility).isEqualTo(View.VISIBLE)
      assertThat(bar.progress).isEqualTo(87)
    }
  }
}
