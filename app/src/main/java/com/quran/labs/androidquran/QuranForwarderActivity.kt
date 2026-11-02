package com.quran.labs.androidquran

import android.app.Activity
import android.os.Bundle
import com.quran.data.core.QuranInfo
import dev.zacsweers.metro.Inject

class QuranForwarderActivity : Activity() {
  @Inject
  lateinit var quranInfo: QuranInfo

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    // handle urls of type quran://sura/ayah/endAyah
    val intent = intent
    if (intent != null) {
      val data = intent.data
      if (data != null) {
        val urlString = data.toString()
        val pieces = urlString.split("/".toRegex()).toTypedArray()

        var sura: Int? = null
        var ayah: Int? = null
        var endAyah: Int? = null
        for (s in pieces) {
          try {
            val i = s.toInt()
            if (sura == null) {
              sura = i
            } else if (ayah == null) {
              ayah = i
            } else {
              endAyah = i
              break
            }
          } catch (nfe: NumberFormatException) {
            // leave it as null
          }
        }

        if (sura != null) {
          if (ayah == null) {
            ayah = 1
          }
          if (endAyah == null) {
            endAyah = ayah
          }
          val page = quranInfo.getPageFromSuraAyah(sura, ayah)
          val showSuraIntent = QuranDataActivity.openPageIntent(
            context = this,
            page = page,
            sura = sura,
            ayah = ayah
          )
          startActivity(showSuraIntent)
        }
      }
    }
    finish()
  }
}
