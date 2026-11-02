<div align="center">

<img src="https://raw.githubusercontent.com/quran/quran_android/main/app/src/madani/res/drawable-xxhdpi/icon.png" alt='Mengaji logo'/>

# Mengaji

A Quran reading app for Android, built for daily reading and memorization (hifz).

Mengaji is a fork of the excellent [Quran for Android](https://github.com/quran/quran_android)
project created by the Quran.com team and its contributors.

</div>

## What you can do

- **Read the Quran** using the beautiful Madani page images, with translations and tafsir in many
  languages.
- **Listen to recitations** from several reciters, with repeat and playback speed controls.
- **Memorize with Hifz Mode** — cover the ayahs on a page and reveal them as you recite:
  - **Per ayah**: tap an ayah to reveal or hide it, or reveal it word by word.
  - **Per page**: reveal from the beginning of the page; swipe down to reveal the next ayah and
    swipe up to hide the previous one.
  - **Show only the first word** (or the first few words) of every ayah, then reveal more with each
    long press.
  - Keep the ayah number markers visible while the text is covered.
- **Bookmark** pages and ayahs, tag them, and keep track of your reading progress.
- **Search** the Quran and translations.
- **Adjust the reading experience**: night mode, page themes, text size, and more.

## Install

The app is built from source. You need the Android SDK and JDK 17 or newer.

```bash
./gradlew assembleMadaniDebug
```

The APK is written to `app/build/outputs/apk/madani/debug/app-madani-debug.apk`. Install it on a
device or emulator with:

```bash
adb install -r app/build/outputs/apk/madani/debug/app-madani-debug.apk
```

## Release builds

The `Release` GitHub Actions workflow builds a release APK and a Play Store AAB, either manually
(**Actions → Release → Run workflow**) or when a `v*` tag is pushed. Tag releases are published
on the GitHub releases page.

Add these repository secrets to sign the artifacts:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the release keystore (`base64 -i release-key.jks`) |
| `ANDROID_STORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Without the secrets the workflow still builds, but the APK and AAB are unsigned. The pipeline
builds with `-PdisableFirebase`, so analytics and crash reporting are not included; register the
app id (`com.xaepudin.mengaji`) in your own Firebase project if you want them back.

## About this fork

This repository is a fork of [quran/quran_android](https://github.com/quran/quran_android). All
credit for the original application, its architecture, data, and translations belongs to the
original authors and contributors.

The main addition in this fork is **Hifz Mode**, a memorization aid that covers the page and lets
you reveal ayahs as you recite them.

## Credits

- Original project: [Quran for Android](https://github.com/quran/quran_android) by the Quran.com
  team and its [contributors](https://github.com/quran/quran_android/blob/main/CONTRIBUTORS.md).
- Madani images from the [quran images project](https://github.com/quran/quran.com-images) on
  GitHub.
- Qaloon images used with permission of Nous Memes Editions Et Diffusion (Tunisia).
- Naskh images used with permission of SHL Info Systems.
- Translation, tafsir and Arabic data come from [quranenc](https://quranenc.com) and
  [King Saud University](https://quran.ksu.edu.sa). A small number of translations also come from
  [tanzil](http://tanzil.net).

May Allah reward all the awesome contributors and translators of the original project.

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).

Please keep use of this code for non-profit purposes only. The data is licensed under the licenses
of the data's authors (typically [CC BY-NC-ND](https://creativecommons.org/licenses/by-nc-nd/2.0/),
but may differ depending on the source of the data).

## Open Source Projects Used

- [AndroidX](https://developer.android.com/jetpack/androidx/)
- [Kotlin](https://kotlinlang.org)
- [Material Design Components](https://github.com/material-components/material-components-android)
- [AndroidSlidingUpPanel](https://github.com/umano/AndroidSlidingUpPanel)
- [OkHttp](https://github.com/square/okhttp)
- [RxJava 2](https://github.com/ReactiveX/RxJava)
- [RxAndroid](https://github.com/ReactiveX/RxAndroid)
- [Moshi](https://github.com/square/moshi)
- [Dagger2](https://google.github.io/dagger/)
- [Timber](https://github.com/JakeWharton/timber)
- [dnsjava](http://dnsjava.org)
- [NumberPicker](https://github.com/ShawnLin013/NumberPicker)
