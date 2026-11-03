package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BookmarkData @JvmOverloads constructor(
  val tags: List<Tag> = emptyList(),
  val bookmarks: List<Bookmark> = emptyList(),
  val recentPages: List<RecentPage> = emptyList(),
  val readingBookmarks: List<BackupReadingBookmark> = emptyList(),
  val pageType: String? = null,
  val collections: List<BackupCollection> = emptyList()
) {

  fun getCommaSeparatedNames() =
    "type, sura, ayah, page, timestamp, tags, slot"

  fun getRecentPagesByLine() =
    recentPages
      .map { "${it.getCommaSeparatedValues()} \n" }
      .reduceOrNull { acc, recent -> "$acc$recent" }

  fun getBookmarksByLine() =
    bookmarks
      .map { "${it.getCommaSeparatedValues(tags)} \n" }
      .reduceOrNull { acc, bookmark -> "$acc$bookmark" }

  fun getCollectionsHeader() =
    "collection, name, entry, start_sura, start_ayah, end_sura, end_ayah"

  fun getCollectionsByLine(): String? =
    collections
      .map { collection ->
        if (collection.entries.isEmpty()) {
          "collection, ${collection.name}, , , , ,  \n"
        } else {
          collection.entries.joinToString(separator = "") { entry ->
            "collection, ${collection.name}, ${entry.name}, ${entry.startSura}, " +
              "${entry.startAyah}, ${entry.endSura}, ${entry.endAyah} \n"
          }
        }
      }
      .reduceOrNull { acc, lines -> "$acc$lines" }

  fun getReadingBookmarksByLine() =
    readingBookmarks
      .map { "${it.getCommaSeparatedValues()} \n" }
      .reduceOrNull { acc, readingBookmark -> "$acc$readingBookmark" }
}
