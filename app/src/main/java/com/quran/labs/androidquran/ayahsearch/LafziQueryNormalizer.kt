package com.quran.labs.androidquran.ayahsearch

import java.util.Locale

/**
 * Port of Lafzi's `QueryUtil.normalizeQuery` (lafzi-android, GPL-3.0).
 *
 * Converts an Indonesian/Latin transliteration of an ayah into the phonetic code that is stored
 * in the bundled Lafzi index. Keeping this in sync with the index is essential: a mismatch means
 * the trigram lookups silently return nothing.
 */
object LafziQueryNormalizer {
  private val whitespace = Regex("\\s+")
  private val nonPhoneticCharacters = Regex("[^A-Z`'\\-\\s]")
  private val repeatedConsonants =
    Regex("(B|C|D|F|G|H|J|K|L|M|N|P|Q|R|S|T|V|W|X|Y|Z)\\s?\\1+")
  private val repeatedDigraphs = Regex("(KH|CH|SH|TS|SY|DH|TH|ZH|DZ|GH)\\s?\\1+")
  private val repeatedVowels = Regex("(A|I|U|E|O)\\1+")
  private val wordInitialVowel = Regex("^(A|I|U)")
  private val vowelAfterWhitespace = Regex("\\s(A|I|U)")
  private val hamzahBeforeVowel = Regex("I(A|U)")
  private val hamzahAfterVowel = Regex("U(A|I)")
  private val nasalBeforeConsonant =
    Regex("(A|I|U)NG\\s?(D|F|J|K|P|Q|S|T|V|Z)")
  private val nasalBeforeB = Regex("N\\s?B")
  private val nasalAssimilation = Regex("N\\s?(N|M|L|R|Y|W)")
  private val ngBeforeVowel = Regex("NG(A|I|U)")
  private val hamzahLetter = Regex("'|`")
  private val qafKaf = Regex("Q|K")
  private val faVp = Regex("F|V|P")
  private val jimZai = Regex("J|Z")
  private val allWhitespace = Regex("\\s")
  private val vowels = Regex("A|I|U")

  fun normalize(query: String, isVocal: Boolean): String {
    val first = query.uppercase(Locale.ROOT)
      .replace(whitespace, " ")
      .replace('-', ' ')
      .replace(nonPhoneticCharacters, "")

    val second = first
      .replace("O", "A")
      .replace("E", "I")

    val third = second
      .replace(repeatedConsonants, "$1")
      .replace(repeatedDigraphs, "$1")

    val fourth = third.replace(repeatedVowels, "$1")

    val fifth = fourth
      .replace("AI", "AY")
      .replace("AU", "AW")

    val sixth = fifth
      .replace(wordInitialVowel, "X$1")
      .replace(vowelAfterWhitespace, "X$1")
      .replace(hamzahBeforeVowel, "IX$1")
      .replace(hamzahAfterVowel, "UX$1")

    val seventh = sixth.replace(nasalBeforeConsonant, "$1N$2")

    val eighth = seventh.replace(nasalBeforeB, "MB")

    val ninth = eighth
      .replace("DUNYA", "DUN_YA")
      .replace("BUNYAN", "BUN_YAN")
      .replace("QINWAN", "KIN_WAN")
      .replace("KINWAN", "KIN_WAN")
      .replace("SINWAN", "SIN_WAN")
      .replace("SHINWAN", "SIN_WAN")
      .replace(nasalAssimilation, "$1")
      .replace("DUN_YA", "DUNYA")
      .replace("BUN_YAN", "BUNYAN")
      .replace("KIN_WAN", "KINWAN")
      .replace("SIN_WAN", "SINWAN")

    val tenth = ninth
      .replace("KH", "H")
      .replace("CH", "H")
      .replace("SH", "S")
      .replace("TS", "S")
      .replace("SY", "S")
      .replace("DH", "D")
      .replace("ZH", "Z")
      .replace("DZ", "Z")
      .replace("TH", "T")
      .replace(ngBeforeVowel, "X$1")
      .replace("GH", "G")

    val eleventh = tenth
      .replace(hamzahLetter, "X")
      .replace(qafKaf, "K")
      .replace(faVp, "F")
      .replace(jimZai, "Z")

    val twelfth = eleventh.replace(allWhitespace, "")

    return if (isVocal) {
      twelfth
    } else {
      twelfth.replace(vowels, "")
    }
  }
}
