package com.quran.mobile.collection.model

import com.quran.data.model.SuraAyah

/**
 * A user-created collection of ayah ranges. Collections are local to the device and are shown as
 * a first-class tab alongside Surahs, Juz', and Bookmarks.
 */
data class AyahCollection(
  val id: Long,
  val name: String,
  val entryCount: Int
)

/**
 * One named entry inside an [AyahCollection]. An entry always covers at least one ayah and may
 * span multiple ayahs within a sura, or across sura boundaries.
 */
data class AyahCollectionEntry(
  val id: Long,
  val collectionId: Long,
  val name: String,
  val startSura: Int,
  val startAyah: Int,
  val endSura: Int,
  val endAyah: Int,
  val position: Int
) {
  val start: SuraAyah
    get() = SuraAyah(startSura, startAyah)

  val end: SuraAyah
    get() = SuraAyah(endSura, endAyah)
}
