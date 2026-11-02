package com.quran.labs.androidquran.database

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class TranslationTextsQueryTest {

  private lateinit var database: SQLiteDatabase

  @Before
  fun setUp() {
    database = SQLiteDatabase.create(null)
    database.execSQL(
      "CREATE VIRTUAL TABLE verses USING fts3(sura integer, ayah integer, text text)"
    )
    database.execSQL(
      "INSERT INTO verses(rowid, sura, ayah, text) " +
        "VALUES (1, 1, 1, 'first'), (6222, 112, 1, 'second')"
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `reads translation texts by global ayah id`() {
    val result = DatabaseHandler.readTranslationTexts(database, listOf(1, 6222))

    assertThat(result).containsExactly(1, "first", 6222, "second")
  }

  @Test
  fun `ignores unknown ids and empty texts`() {
    database.execSQL(
      "INSERT INTO verses(rowid, sura, ayah, text) VALUES (3, 1, 3, '')"
    )

    val result = DatabaseHandler.readTranslationTexts(database, listOf(3, 999))

    assertThat(result).isEmpty()
  }

  @Test
  fun `empty id list returns an empty map`() {
    assertThat(DatabaseHandler.readTranslationTexts(database, emptyList())).isEmpty()
  }
}
