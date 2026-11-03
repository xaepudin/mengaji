package com.quran.labs.androidquran.ayahsearch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the bundled Lafzi database: extraction, vocal index lookup, FTS lookup and mapping
 * matched phonetic positions back to the Arabic text.
 */
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class LafziSearchRepositoryTest {

  private lateinit var repository: LafziSearchRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    repository = LafziSearchRepository(context)
  }

  @Test
  fun `latin vocal search ranks bismillah first`() = runBlocking {
    val results = repository.search("bismillah", isVocal = true)

    assertThat(results).isNotEmpty()
    assertThat(results.first().sura).isEqualTo(1)
    assertThat(results.first().ayah).isEqualTo(1)
    assertThat(results.first().highlightRanges).isNotEmpty()
  }

  @Test
  fun `latin non vocal search also finds bismillah`() = runBlocking {
    val results = repository.search("bismillah", isVocal = false)

    assertThat(results).isNotEmpty()
    assertThat(results.first().sura).isEqualTo(1)
    assertThat(results.first().ayah).isEqualTo(1)
  }

  @Test
  fun `arabic query uses the bundled fts index`() = runBlocking {
    val results = repository.search("بسم الله", isVocal = true)

    assertThat(results).isNotEmpty()
    assertThat(results.map { it.sura to it.ayah }).contains(1 to 1)
    assertThat(results.first().highlightRanges).isNotEmpty()
  }

  @Test
  fun `typographic apostrophe is treated as hamzah`() = runBlocking {
    val results = repository.search("fir\u2019aun", isVocal = true)

    assertThat(results).isNotEmpty()
    assertThat(results.first().sura).isEqualTo(2)
  }

  @Test
  fun `loads ayah texts by id for the collection reading screen`() = runBlocking {
    val texts = repository.ayahs(listOf(1, 6222))

    assertThat(texts.map { it.sura to it.ayah })
      .containsExactly(1 to 1, 112 to 1).inOrder()
    assertThat(texts.first().text).isNotEmpty()
  }

  @Test
  fun `query shorter than three characters returns no results`() = runBlocking {
    assertThat(repository.search("ab", isVocal = true)).isEmpty()
  }
}
