# KMP core bootstrap

## Stato

Le fasi P2-P6 sono completate. Il modulo `:core` espone modelli di dominio, catalogo specie, curve biologiche, facade `MycoAnalysisEngine`, parser SPUN e raster heatmap, con tre target:

- Android tramite `com.android.kotlin.multiplatform.library`;
- `iosArm64` per iPhone;
- `iosSimulatorArm64` per simulatori Apple Silicon.

Xcode invoca `:core:embedAndSignAppleFrameworkForXcode` prima della compilazione Swift. Il client usa realmente `MycoCore` per analisi, forecast probabilistico, fattori, specie, SPUN e heatmap. Android dipende da `:core` e delega al core condiviso formula canonica e palette standard; i test di parità impediscono divergenze rispetto alle API Android mantenute per compatibilità.

## Toolchain

- Gradle 9.7.1;
- Android Gradle Plugin 9.3.2;
- Kotlin/Compose plugin 2.4.20;
- Java 17;
- Xcode 26.6, target iOS 18+.

Kotlin è stato allineato a 2.4.20 perché la linea 2.4.10 non supporta Gradle 9.7.1 né AGP 9.3.x per KMP. La matrice ufficiale 2.4.20 certifica Gradle fino a 9.7.0 e AGP fino a 9.3.1; AGP 9.3.2 è stato quindi verificato con build e test reali senza soppressione di warning, ma resta un rischio di compatibilità di patch da riesaminare al prossimo aggiornamento Kotlin.

## Comandi di verifica

```bash
./gradlew :core:compileAndroidMain :core:testAndroidHostTest
./gradlew :core:linkDebugFrameworkIosArm64
./gradlew :core:linkDebugFrameworkIosSimulatorArm64 :core:iosSimulatorArm64Test
```

La build Xcode richiede `JAVA_HOME` verso un JDK 17 e un Android SDK risolvibile da Gradle. Il Gradle Wrapper 9.7.1 è versionato nel repository.

## Gate di parità

`ScientificParityTest` copre formule, engine deterministico, parser SPUN e hash completo del raster. `CrossPlatformScientificParityTest` confronta le curve Android esistenti con il core e verifica l'intera palette 0...100 in modalità chiara e scura. `PerformanceRegressionTest` aggiunge un budget ampio e stabile per intercettare regressioni macroscopiche di analisi e generazione raster.
