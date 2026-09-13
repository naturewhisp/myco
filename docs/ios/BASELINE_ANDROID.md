# Android protected baseline before iOS development

- Recorded: 2026-09-13 (Europe/Rome)
- Git commit: `562d94aff30825b4c7c7d2f7fe9d2b23d606f3c6`
- Android version: `1.2.0` (`versionCode 2`)
- Source branch at inspection: `master`, clean and aligned with `origin/master`
- Integration branch: `codex/ios-native`
- Build contract: compile/target SDK 36, min SDK 31, Java 17, Gradle 9.7.1, AGP 9.3.2

## Gate status

| Gate | Status | Evidence |
|---|---|---|
| `lintDebug` | PASS | `0 errors, 0 warnings` (20 informational hints). |
| `compileDebugKotlin` | PASS | Kotlin debug sources compiled successfully. |
| `testDebugUnitTest` | PASS | 105 test completed, 0 failures/errors; il golden master P1 contiene 23 test e tutti i 20 scenari richiesti. |
| `assembleDebug` | PASS | Debug APK assembled successfully. |

Al momento della baseline iniziale il checkout non conteneva `gradlew` o `gradle-wrapper.jar` e l'host non aveva una toolchain Android. La verifica ha quindi usato copie temporanee di Gradle 9.7.1, Temurin JDK 17.0.20.1, Android platform 36 e Build Tools 36.0.0 sotto `/private/tmp`. Il Gradle Wrapper 9.7.1 è stato successivamente ripristinato e versionato perché necessario per i gate riproducibili e per la build KMP avviata da Xcode.

The combined first run exposed a Gradle 9.7.1 concurrent test-results aggregation race (`in-progress-results-generic.bin` missing). Re-running tests with `--max-workers=1` succeeded; lint, compile, and assemble then succeeded with the same single-worker setting. This is a tooling execution note, not an application test failure.

## Main Android dependencies

- Jetpack Compose with Material 3
- AndroidX Lifecycle, DataStore, and Core KTX
- Retrofit 2, Gson, and OkHttp
- OsmDroid
- Google Play Services Location
- Google AI Edge AICore
- JUnit, MockK, kotlinx-coroutines-test, and Turbine

## Debug APK

The final post-KMP debug APK is 62 MiB. SHA-256: `9b1bd24cb7525c087b0f55a9eb880303c394278f7fe3d0c699cfdd2b37aa5866`.

## Existing warning/error policy

No compiler or lint warning was observed. Le assegnazioni Groovy deprecate del build Android sono state convertite alla sintassi Gradle 10-compatible; una verifica con `--warning-mode all` non riporta più deprecazioni originate dagli script del progetto.
