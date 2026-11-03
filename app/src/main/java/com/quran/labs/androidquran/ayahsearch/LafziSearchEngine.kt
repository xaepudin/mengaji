package com.quran.labs.androidquran.ayahsearch

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.min

/**
 * Runs Lafzi searches against the bundled `lafzi.sqlite` database.
 *
 * The database is the index produced by the Lafzi project (lafzi-android / lafzi-indexer,
 * GPL-3.0). It contains the Uthmani ayah text, the vocal and non-vocal trigram indexes, and the
 * `quran_text` FTS table used for direct Arabic searches.
 *
 * This class is intentionally synchronous; callers should invoke it off the main thread.
 */
class LafziSearchEngine(private val database: SQLiteDatabase) {

  /**
   * Returns matching ayahs for [query]. Latin queries use Lafzi's phonetic trigram search, while
   * Arabic queries use the bundled FTS table.
   */
  fun search(query: String, isVocal: Boolean): List<AyahSearchResult> {
    val trimmed = normalizePunctuation(query.trim())
    if (trimmed.length < MIN_QUERY_LENGTH) {
      return emptyList()
    }
    return if (isLatinQuery(trimmed)) {
      searchLatin(trimmed, isVocal)
    } else {
      searchArabic(trimmed)
    }
  }

  /**
   * Loads the Arabic text for the given global ayah ids. Used by the collection reading screen,
   * which needs text without running a search.
   */
  fun loadAyahTexts(ids: List<Int>): List<AyahText> {
    val results = ArrayList<AyahText>(ids.size)
    for (id in ids) {
      val ayah = loadAyah(id, isVocal = true) ?: continue
      results += AyahText(ayah.id, ayah.sura, ayah.ayah, ayah.displayText)
    }
    return results
  }

  /**
   * Mobile keyboards often turn straight apostrophes into typographic ones. Lafzi's phonetic
   * encoding treats an apostrophe as hamzah, so normalise the common variants before deciding
   * whether the query is Latin or Arabic.
   */
  private fun normalizePunctuation(query: String): String {
    return query
      .replace('\u2018', '\'')
      .replace('\u2019', '\'')
      .replace('\u201B', '\'')
  }

  private fun isLatinQuery(query: String): Boolean = query.all { it.code < 0x80 }

  // region Latin / phonetic search

  private fun searchLatin(rawQuery: String, isVocal: Boolean): List<AyahSearchResult> {
    val query = LafziQueryNormalizer.normalize(rawQuery, isVocal)
    if (query.length < 3) {
      return emptyList()
    }

    // Lafzi tries a strict threshold first and progressively relaxes it. The final threshold it
    // checks is 0.7, after which it gives up rather than showing very loose matches.
    var documents = emptyList<MatchedDocument>()
    for (tenths in 9 downTo 7) {
      documents = findDocuments(query, isVocal, tenths / 10.0)
      if (documents.isNotEmpty()) {
        break
      }
    }
    if (documents.isEmpty()) {
      return emptyList()
    }

    val maxScore = query.length - 2
    val results = ArrayList<RankedResult>(documents.size)
    for (document in documents) {
      val ayah = loadAyah(document.ayahId, isVocal) ?: continue
      val computedRanges = highlightLatinMatch(document.lis, ayah.mappingPositions)
      document.score += wordBoundaryBonus(ayah.text, computedRanges)
      // Muqathaat ayahs display a shorter Uthmani form than the text the positions refer to.
      val highlightRanges = if (ayah.canHighlight) computedRanges else emptyList()
      val relevance = if (maxScore > 0) {
        min(floor(document.score / maxScore * 100).toInt(), 100)
      } else {
        0
      }
      results += RankedResult(document.ayahId, document.score, ayah, highlightRanges, relevance)
    }

    results.sortWith(
      compareByDescending<RankedResult> { it.score }.thenBy { it.ayahId }
    )
    return results.map { it.toSearchResult() }
  }

  private fun findDocuments(
    query: String,
    isVocal: Boolean,
    threshold: Double
  ): List<MatchedDocument> {
    val trigrams = LafziTrigramExtractor.extract(query)
    if (trigrams.isEmpty()) {
      return emptyList()
    }

    val documents = HashMap<Int, MatchedDocument>()
    for ((term, trigram) in trigrams) {
      val post = indexPost(term, isVocal) ?: continue
      val keys = post.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val ayahId = key.toIntOrNull() ?: continue
        val positions = post.optJSONArray(key)?.toIntList() ?: continue

        val document = documents[ayahId]
        if (document == null) {
          documents[ayahId] = MatchedDocument(
            ayahId = ayahId,
            matchedTrigramsCount = 1,
            matchedTerms = hashMapOf(term to positions)
          )
        } else {
          // A repeated trigram in the query should not count more often than it occurs in the
          // ayah. This mirrors Lafzi's `min(query frequency, ayah frequency)` rule.
          document.matchedTrigramsCount += min(trigram.frequency, positions.size)
          document.matchedTerms[term] = positions
        }
      }
    }

    val minScore = threshold * (query.length - 2)
    val filtered = documents.values.filter { it.matchedTrigramsCount >= minScore }
    for (document in filtered) {
      val flattened = document.matchedTerms.values.flatten()
      val lis = LafziScoring.longestContiguousSubsequence(flattened, MAX_GAP)
      document.lis = lis
      document.score = lis.size * LafziScoring.reciprocalDiffAverage(lis)
    }
    return filtered
  }

  private fun indexPost(term: String, isVocal: Boolean): JSONObject? {
    val table = if (isVocal) VOCAL_INDEX_TABLE else NON_VOCAL_INDEX_TABLE
    database.rawQuery(
      "SELECT $COL_POST FROM $table WHERE $COL_TERM = ? LIMIT 1",
      arrayOf(term)
    ).use { cursor ->
      if (!cursor.moveToFirst()) {
        return null
      }
      val json = cursor.getString(0) ?: return null
      return try {
        JSONObject(json)
      } catch (exception: Exception) {
        null
      }
    }
  }

  private fun highlightLatinMatch(
    lis: List<Int>,
    mappingPositions: List<Int>
  ): List<IntRange> {
    if (lis.isEmpty() || mappingPositions.isEmpty()) {
      return emptyList()
    }

    // Each matched trigram covers three phonetic characters. Map those back to positions in the
    // original Arabic text so the result list can highlight the words the user searched for.
    val phoneticPositions = LinkedHashSet<Int>()
    for (position in lis) {
      phoneticPositions.add(position)
      phoneticPositions.add(position + 1)
      phoneticPositions.add(position + 2)
    }

    val realPositions = phoneticPositions.mapNotNull { phoneticPosition ->
      val index = phoneticPosition - 1
      if (index in mappingPositions.indices) mappingPositions[index] else null
    }
    return LafziScoring.longestHighlightLookforward(realPositions, HIGHLIGHT_LOOK_AHEAD)
  }

  // endregion

  // region Arabic FTS search

  private fun searchArabic(rawQuery: String): List<AyahSearchResult> {
    val matchQuery = buildArabicMatchQuery(rawQuery)
    if (matchQuery.isEmpty()) {
      return emptyList()
    }

    val matches = ArrayList<ArabicMatch>()
    database.rawQuery(ARABIC_SEARCH_SQL, arrayOf(matchQuery)).use { cursor ->
      while (cursor.moveToNext()) {
        val ayahId = cursor.getInt(0)
        val offsets = cursor.getString(1)
        val fullLength = cursor.getInt(2)
        val shortLength = cursor.getInt(3)
        val matchInfo = cursor.getBlob(4)
        val score = if (matchInfo != null && matchInfo.isNotEmpty()) {
          matchInfo[0].toInt() and 0xFF
        } else {
          0
        }
        matches += ArabicMatch(
          ayahId = ayahId,
          score = score,
          positions = parseOffsets(offsets, fullLength, shortLength)
        )
      }
    }

    matches.sortWith(compareByDescending<ArabicMatch> { it.score }.thenBy { it.ayahId })
    return matches.mapNotNull { match ->
      val ayah = loadAyah(match.ayahId, isVocal = true) ?: return@mapNotNull null
      AyahSearchResult(
        ayahId = ayah.id,
        sura = ayah.sura,
        ayah = ayah.ayah,
        arabicText = ayah.displayText,
        highlightRanges = if (ayah.canHighlight) {
          LafziScoring.longestHighlightLookforward(match.positions, HIGHLIGHT_LOOK_AHEAD)
        } else {
          emptyList()
        },
        relevance = 0
      )
    }
  }

  /**
   * Lafzi stores the reversed ayah text in its FTS index, so it prefixes every search term with `*`
   * and reverses the whole query before matching.
   */
  private fun buildArabicMatchQuery(query: String): String {
    val terms = query.split(' ')
      .filter { it.length > LAZY_ARABIC_MIN_TERM_LENGTH }
    if (terms.isEmpty()) {
      return ""
    }
    return terms.joinToString(" ") { "*$it" }.reversed()
  }

  /**
   * Decodes the space separated output of FTS4 `offsets()` into character positions.
   *
   * Port of Lafzi's `QuranText.splitOffsets`. The calculations compensate for the difference in
   * length between the fully vocalised ayah and the shorter text that was indexed.
   */
  private fun parseOffsets(
    offsets: String?,
    fullLength: Int,
    shortLength: Int
  ): List<Int> {
    if (offsets.isNullOrBlank() || shortLength <= 0) {
      return emptyList()
    }

    val parts = offsets.split(' ')
    val positions = ArrayList<Int>()
    var biggestMinus = 0
    var index = 0
    while (index + 3 < parts.size) {
      val offset = parts[index + 2].toIntOrNull() ?: break
      val size = parts[index + 3].toIntOrNull() ?: break
      val adjusted =
        (shortLength - offset - size * (fullLength.toDouble() / shortLength)).toInt()
      if (adjusted < biggestMinus) {
        biggestMinus = adjusted
      }
      for (position in 0 until size) {
        positions += adjusted + position
      }
      index += 4
    }

    if (biggestMinus < 0) {
      for (position in positions.indices) {
        positions[position] -= biggestMinus
      }
    }
    return LafziScoring.longestContiguousSubsequence(positions, MAX_GAP)
  }

  // endregion

  private fun loadAyah(ayahId: Int, isVocal: Boolean): AyahRecord? {
    val mappingColumn = if (isVocal) VOCAL_MAPPING_COLUMN else NON_VOCAL_MAPPING_COLUMN
    database.rawQuery(
      "SELECT $COL_ID, $COL_SURA, $COL_AYAH, $COL_ARABIC, $COL_MUQATHAAT, $mappingColumn " +
        "FROM $AYAH_TABLE WHERE $COL_ID = ? LIMIT 1",
      arrayOf(ayahId.toString())
    ).use { cursor ->
      if (!cursor.moveToFirst()) {
        return null
      }
      val id = cursor.getInt(0)
      val sura = cursor.getInt(1)
      val ayah = cursor.getInt(2)
      val arabic = cursor.getString(3).orEmpty()
      val muqathaat = cursor.getString(4)
      val mappingPositions = cursor.getString(5).toPositionList()
      return AyahRecord(
        id = id,
        sura = sura,
        ayah = ayah,
        text = arabic,
        muqathaat = muqathaat,
        mappingPositions = mappingPositions
      )
    }
  }

  private fun String?.toPositionList(): List<Int> {
    if (this.isNullOrEmpty()) {
      return emptyList()
    }
    return split(',').mapNotNull { it.toIntOrNull() }
  }

  /**
   * Lafzi gives a tiny bonus when the match ends on a word boundary. It only breaks ties, but it
   * is part of the result ordering users see in Lafzi.
   */
  private fun wordBoundaryBonus(text: String, ranges: List<IntRange>): Double {
    val lastEnd = ranges.lastOrNull()?.last ?: return 0.0
    var endPosition = lastEnd
    if (endPosition > text.length - 3) {
      endPosition = text.length - 3
    }
    if (endPosition < 0) {
      return 0.0
    }

    return if (text.length - 1 <= endPosition + 1 || text.getOrNull(endPosition + 1) == ' ') {
      WORD_BOUNDARY_BONUS
    } else if (text.length - 1 <= endPosition + 2 || text.getOrNull(endPosition + 2) == ' ') {
      WORD_BOUNDARY_BONUS
    } else if (text.length - 1 <= endPosition + 3 || text.getOrNull(endPosition + 3) == ' ') {
      WORD_BOUNDARY_BONUS
    } else {
      0.0
    }
  }

  private fun JSONArray.toIntList(): List<Int> {
    val values = ArrayList<Int>(length())
    for (index in 0 until length()) {
      values += optInt(index)
    }
    return values
  }

  private data class AyahRecord(
    val id: Int,
    val sura: Int,
    val ayah: Int,
    val text: String,
    val muqathaat: String?,
    val mappingPositions: List<Int>
  ) {
    val displayText: String
      get() = if (muqathaat.isNullOrEmpty()) text else muqathaat

    /**
     * Highlight positions are relative to [text]. Muqathaat ayahs display a different, shorter
     * Uthmani form, so highlighting their displayed text would be misleading.
     */
    val canHighlight: Boolean
      get() = displayText == text
  }

  private data class MatchedDocument(
    val ayahId: Int,
    var matchedTrigramsCount: Int,
    val matchedTerms: MutableMap<String, List<Int>>
  ) {
    var lis: List<Int> = emptyList()
    var score: Double = 0.0
  }

  private data class ArabicMatch(
    val ayahId: Int,
    val score: Int,
    val positions: List<Int>
  )

  private data class RankedResult(
    val ayahId: Int,
    val score: Double,
    val ayah: AyahRecord,
    val highlightRanges: List<IntRange>,
    val relevance: Int
  ) {
    fun toSearchResult(): AyahSearchResult = AyahSearchResult(
      ayahId = ayahId,
      sura = ayah.sura,
      ayah = ayah.ayah,
      arabicText = ayah.displayText,
      highlightRanges = highlightRanges,
      relevance = relevance
    )
  }

  private companion object {
    const val MIN_QUERY_LENGTH = 3
    const val MAX_GAP = 7
    const val HIGHLIGHT_LOOK_AHEAD = 6
    const val LAZY_ARABIC_MIN_TERM_LENGTH = 2
    const val WORD_BOUNDARY_BONUS = 0.001

    const val AYAH_TABLE = "ayat_quran"
    const val COL_ID = "_id"
    const val COL_SURA = "surah_no"
    const val COL_AYAH = "ayat_no"
    const val COL_ARABIC = "ayat_arabic"
    const val COL_MUQATHAAT = "ayat_muqathaat"
    const val VOCAL_MAPPING_COLUMN = "vocal_mapping_position"
    const val NON_VOCAL_MAPPING_COLUMN = "nonvocal_mapping_position"

    const val VOCAL_INDEX_TABLE = "vocal_index"
    const val NON_VOCAL_INDEX_TABLE = "nonvocal_index"
    const val COL_TERM = "term"
    const val COL_POST = "post"

    const val ARABIC_SEARCH_SQL = """
      SELECT
        docid,
        offsets(quran_text) AS pos,
        ayat_quran.arabic_text_length AS full_length,
        ayat_quran.short_arabic_text_length AS short_length,
        matchinfo(quran_text, 's') AS subsequence
      FROM quran_text
      JOIN ayat_quran ON ayat_quran._id = quran_text.docid
      WHERE text_reverse MATCH ?
    """
  }
}
