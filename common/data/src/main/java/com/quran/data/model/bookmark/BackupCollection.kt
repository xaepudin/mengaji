package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

/** A collection in a bookmark backup. IDs are intentionally omitted: imports merge by name. */
@JsonClass(generateAdapter = true)
data class BackupCollection(
  val name: String,
  val entries: List<BackupCollectionEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupCollectionEntry(
  val name: String,
  val startSura: Int,
  val startAyah: Int,
  val endSura: Int,
  val endAyah: Int
)
