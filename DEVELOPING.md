# Developing Mengaji

This file keeps the technical setup and release notes out of the user-facing
README.

## Build requirements

The app is built from source. You need the Android SDK and JDK 17 or newer.

```bash
./gradlew assembleMadaniDebug
```

The APK is written to
`app/build/outputs/apk/madani/debug/app-madani-debug.apk`. Install it on a
device or emulator with:

```bash
adb install -r app/build/outputs/apk/madani/debug/app-madani-debug.apk
```

## Release builds

The `Release` GitHub Actions workflow builds a release APK and a Play Store AAB,
either manually (**Actions → Release → Run workflow**) or when a `v*` tag is
pushed. Tag releases are published on the GitHub releases page.

Add these repository secrets to sign the artifacts:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the release keystore (`base64 -i release-key.jks`) |
| `ANDROID_STORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Without the secrets the workflow still builds, but the APK and AAB are unsigned.
The pipeline builds with `-PdisableFirebase`, so analytics and crash reporting
are not included; register the app id (`com.xaepudin.mengaji`) in your own
Firebase project if you want them back.

## Ayah search data

The ayah search feature uses the index from
[Lafzi](https://github.com/lafzi/lafzi-android) and
[lafzi-indexer](https://github.com/lafzi/lafzi-indexer), both licensed under
GPL-3.0. The bundled index lives at
`app/src/main/assets/ayah_search/lafzi.sqlite.gz`. It is stored compressed in
the repository so a checkout stays small; the Android build tools expand `.gz`
assets, so the app reads `assets/ayah_search/lafzi.sqlite` and copies it into
private storage on first use.

## Open source projects used

- [AndroidX](https://developer.android.com/jetpack/androidx/)
- [Kotlin](https://kotlinlang.org)
- [Lafzi](https://github.com/lafzi/lafzi-android)
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
