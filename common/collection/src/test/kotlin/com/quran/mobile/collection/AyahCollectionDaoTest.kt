package com.quran.mobile.collection

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import java.util.Properties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class AyahCollectionDaoTest {

  private lateinit var driver: SqlDriver
  private lateinit var dao: AyahCollectionDao

  @Before
  fun setUp() {
    driver = JdbcSqliteDriver(
      JdbcSqliteDriver.IN_MEMORY,
      Properties(),
      CollectionsDatabase.Schema
    )
    dao = AyahCollectionDao(CollectionsDatabase(driver))
  }

  @After
  fun tearDown() {
    driver.close()
  }

  @Test
  fun `creates a collection and appends entries in order`() = runBlocking<Unit> {
    val collectionId = dao.createCollection("Doa-doa")
    dao.addEntry(collectionId, "Pembuka", SuraAyah(1, 1), SuraAyah(1, 7))
    dao.addEntry(collectionId, "Ikhlas", SuraAyah(112, 1), SuraAyah(112, 4))

    val collections = dao.collections()
    assertThat(collections).hasSize(1)
    assertThat(collections.first().name).isEqualTo("Doa-doa")
    assertThat(collections.first().entryCount).isEqualTo(2)

    val entries = dao.entries(collectionId)
    assertThat(entries.map { it.name }).containsExactly("Pembuka", "Ikhlas").inOrder()
    assertThat(entries.map { it.position }).containsExactly(0, 1).inOrder()
    assertThat(entries.first().start).isEqualTo(SuraAyah(1, 1))
    assertThat(entries.first().end).isEqualTo(SuraAyah(1, 7))
  }

  @Test
  fun `reorders entries and persists their new positions`() = runBlocking<Unit> {
    val collectionId = dao.createCollection("Doa-doa")
    val pembuka = dao.addEntry(collectionId, "Pembuka", SuraAyah(1, 1), SuraAyah(1, 7))
    val ikhlas = dao.addEntry(collectionId, "Ikhlas", SuraAyah(112, 1), SuraAyah(112, 4))

    dao.reorderEntries(listOf(ikhlas, pembuka))

    val entries = dao.entries(collectionId)
    assertThat(entries.map { it.name }).containsExactly("Ikhlas", "Pembuka").inOrder()
    assertThat(entries.map { it.position }).containsExactly(0, 1).inOrder()
  }

  @Test
  fun `renames and deletes collections and entries`() = runBlocking<Unit> {
    val collectionId = dao.createCollection("Doa-doa")
    val entryId = dao.addEntry(collectionId, "Pembuka", SuraAyah(1, 1), SuraAyah(1, 7))

    dao.renameCollection(collectionId, "Doa Harian")
    dao.renameEntry(entryId, "Al-Fatihah")

    assertThat(dao.collection(collectionId)?.name).isEqualTo("Doa Harian")
    assertThat(dao.entries(collectionId).single().name).isEqualTo("Al-Fatihah")

    dao.deleteEntry(entryId)
    assertThat(dao.entries(collectionId)).isEmpty()

    dao.deleteCollection(collectionId)
    assertThat(dao.collections()).isEmpty()
    assertThat(dao.entries(collectionId)).isEmpty()
  }

  @Test
  fun `collection flows emit the current data`() = runBlocking<Unit> {
    val collectionId = dao.createCollection("Doa-doa")
    dao.addEntry(collectionId, "Pembuka", SuraAyah(1, 1), SuraAyah(1, 7))

    val collection = dao.collectionFlow(collectionId).first()
    val entries = dao.entriesFlow(collectionId).first()

    assertThat(collection?.name).isEqualTo("Doa-doa")
    assertThat(collection?.entryCount).isEqualTo(1)
    assertThat(entries.map { it.name }).containsExactly("Pembuka")
  }
}
