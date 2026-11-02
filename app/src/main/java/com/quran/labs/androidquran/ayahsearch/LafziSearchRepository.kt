package com.quran.labs.androidquran.ayahsearch

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Provides the Lafzi ayah search engine.
 *
 * The 14 MB SQLite index is bundled as an APK asset. It is copied into the app's private storage
 * the first time the user searches, and reused on later launches. The copy happens on an IO
 * dispatcher so the search screen can show its progress indicator while it happens.
 */
@SingleIn(AppScope::class)
class LafziSearchRepository @Inject constructor(
  @ApplicationContext private val context: Context
) {
  @Volatile
  private var engine: LafziSearchEngine? = null

  /**
   * Copies the bundled database into private storage ahead of the first search. Callers may
   * ignore failures; [search] will retry and surface the error if it still cannot open it.
   */
  suspend fun warmUp() {
    withContext(Dispatchers.IO) {
      searchEngine()
    }
  }

  suspend fun search(query: String, isVocal: Boolean): List<AyahSearchResult> =
    withContext(Dispatchers.IO) {
      searchEngine().search(query, isVocal)
    }

  private fun searchEngine(): LafziSearchEngine {
    engine?.let { return it }

    synchronized(this) {
      engine?.let { return it }

      val databaseFile = ensureDatabaseFile()
      val database = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
      )
      return LafziSearchEngine(database).also { engine = it }
    }
  }

  private fun ensureDatabaseFile(): File {
    val directory = File(context.filesDir, DATABASE_DIRECTORY)
    if (!directory.exists() && !directory.mkdirs()) {
      throw IOException("Unable to create the ayah search directory")
    }

    val databaseFile = File(directory, DATABASE_NAME)
    val versionFile = File(directory, VERSION_FILE)
    val installedVersion = if (versionFile.exists()) {
      versionFile.readText().trim().toIntOrNull()
    } else {
      null
    }

    if (databaseFile.exists() && installedVersion == DATABASE_VERSION) {
      return databaseFile
    }

    Timber.d("Copying the ayah search database")
    copyDatabase(directory, databaseFile)
    versionFile.writeText(DATABASE_VERSION.toString())
    return databaseFile
  }

  private fun copyDatabase(directory: File, destination: File) {
    val temporaryFile = File(directory, "$DATABASE_NAME.tmp")
    try {
      context.assets.open(DATABASE_ASSET).use { assetStream ->
        temporaryFile.outputStream().buffered().use { outputStream ->
          assetStream.copyTo(outputStream)
        }
      }

      if (destination.exists() && !destination.delete()) {
        throw IOException("Unable to replace the ayah search database")
      }
      if (!temporaryFile.renameTo(destination)) {
        temporaryFile.copyTo(destination, overwrite = true)
        if (!temporaryFile.delete()) {
          Timber.w("Unable to delete the temporary ayah search database")
        }
      }
    } catch (exception: Exception) {
      Timber.e(exception, "Unable to copy the ayah search database")
      throw exception
    } finally {
      if (temporaryFile.exists() && !temporaryFile.delete()) {
        Timber.w("Unable to delete the temporary ayah search database")
      }
    }
  }

  private companion object {
    const val DATABASE_DIRECTORY = "ayah_search"
    const val DATABASE_NAME = "lafzi.sqlite"
    const val DATABASE_ASSET = "ayah_search/lafzi.sqlite"
    const val VERSION_FILE = "lafzi.version"

    /**
     * Bump this whenever the bundled `lafzi.sqlite` changes so existing installs replace their
     * copy.
     */
    const val DATABASE_VERSION = 1
  }
}
