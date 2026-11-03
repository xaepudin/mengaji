package com.quran.mobile.collection

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.collection.model.AyahCollection
import com.quran.mobile.collection.model.AyahCollectionEntry
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Local persistence for user-created ayah collections.
 *
 * Entries are ordered by their [AyahCollectionEntry.position]. New entries are appended at the
 * end; drag-and-drop reordering rewrites the positions for the whole collection.
 */
@SingleIn(AppScope::class)
class AyahCollectionDao @Inject constructor(
  private val database: CollectionsDatabase
) {
  private val queries
    get() = database.collectionQueries

  fun collectionsFlow(): Flow<List<AyahCollection>> {
    return queries.allCollections()
      .asFlow()
      .mapToList(Dispatchers.IO)
      .map { rows -> rows.map { it.toModel() } }
  }

  fun collectionFlow(collectionId: Long): Flow<AyahCollection?> {
    return queries.collectionById(collectionId)
      .asFlow()
      .mapToOneOrNull(Dispatchers.IO)
      .map { row -> row?.toModel() }
  }

  fun entriesFlow(collectionId: Long): Flow<List<AyahCollectionEntry>> {
    return queries.entriesForCollection(collectionId)
      .asFlow()
      .mapToList(Dispatchers.IO)
      .map { rows -> rows.map { it.toModel() } }
  }

  suspend fun collections(): List<AyahCollection> = withContext(Dispatchers.IO) {
    queries.allCollections().executeAsList().map { it.toModel() }
  }

  suspend fun collection(collectionId: Long): AyahCollection? = withContext(Dispatchers.IO) {
    queries.collectionById(collectionId).executeAsOneOrNull()?.toModel()
  }

  suspend fun entries(collectionId: Long): List<AyahCollectionEntry> =
    withContext(Dispatchers.IO) {
      queries.entriesForCollection(collectionId).executeAsList().map { it.toModel() }
    }

  suspend fun createCollection(name: String): Long = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    queries.transactionWithResult {
      queries.insertCollection(name = name, created_at = now, updated_at = now)
      queries.lastInsertRowId().executeAsOne()
    }
  }

  suspend fun renameCollection(collectionId: Long, name: String) = withContext(Dispatchers.IO) {
    queries.renameCollection(name = name, updated_at = System.currentTimeMillis(), _id = collectionId)
  }

  suspend fun deleteCollection(collectionId: Long) = withContext(Dispatchers.IO) {
    queries.transaction {
      queries.deleteEntriesForCollection(collectionId)
      queries.deleteCollection(collectionId)
    }
  }

  suspend fun addEntry(
    collectionId: Long,
    name: String,
    start: SuraAyah,
    end: SuraAyah
  ): Long = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    queries.transactionWithResult {
      val position = queries.nextEntryPosition(collectionId).executeAsOne()
      queries.insertEntry(
        collection_id = collectionId,
        name = name,
        start_sura = start.sura.toLong(),
        start_ayah = start.ayah.toLong(),
        end_sura = end.sura.toLong(),
        end_ayah = end.ayah.toLong(),
        position = position,
        created_at = now
      )
      queries.lastInsertRowId().executeAsOne()
    }
  }

  suspend fun renameEntry(entryId: Long, name: String) = withContext(Dispatchers.IO) {
    queries.renameEntry(name = name, _id = entryId)
  }

  suspend fun deleteEntry(entryId: Long) = withContext(Dispatchers.IO) {
    queries.deleteEntry(entryId)
  }

  suspend fun reorderEntries(orderedEntryIds: List<Long>) =
    withContext(Dispatchers.IO) {
      queries.transaction {
        orderedEntryIds.forEachIndexed { index, entryId ->
          queries.updateEntryPosition(position = index.toLong(), _id = entryId)
        }
      }
    }

  private fun AllCollections.toModel() = AyahCollection(
    id = _id,
    name = name,
    entryCount = entry_count.toInt()
  )

  private fun CollectionById.toModel() = AyahCollection(
    id = _id,
    name = name,
    entryCount = entry_count.toInt()
  )

  private fun EntriesForCollection.toModel() = AyahCollectionEntry(
    id = _id,
    collectionId = collection_id,
    name = name,
    startSura = start_sura.toInt(),
    startAyah = start_ayah.toInt(),
    endSura = end_sura.toInt(),
    endAyah = end_ayah.toInt(),
    position = position.toInt()
  )
}
