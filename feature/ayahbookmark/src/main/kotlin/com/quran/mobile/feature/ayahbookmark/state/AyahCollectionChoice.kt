package com.quran.mobile.feature.ayahbookmark.state

import androidx.compose.runtime.Immutable

/** One of the user's ayah collections, shown directly in the bookmark sheet. */
@Immutable
data class AyahCollectionChoice(
  val id: Long,
  val name: String,
  val entryCount: Int
)
