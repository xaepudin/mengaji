package com.quran.data.model.bookmark

/** Reading bookmark colors, in mobile-sync slot order (1, 2, and 3). */
enum class ReadingBookmarkType {
  TEAL, ORANGE, RED;

  /** Stable name used by backup JSON and CSV, independent of R8 renaming. */
  val backupName: String
    get() = when (this) {
      TEAL -> "TEAL"
      ORANGE -> "ORANGE"
      RED -> "RED"
    }
}
