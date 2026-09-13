# KMP core bootstrap

## Stato

La fase P2 è attiva con un modulo minimo `:core` e tre target:

- Android tramite `com.android.kotlin.multiplatform.library`;
- `iosArm64` per iPhone;
- `iosSimulatorArm64` per simulatori Apple Silicon.

`MycoCoreInfo` espone il contratto smoke `version()`. Xcode invoca `:core:embedAndSignAppleFrameworkForXcode` prima della compilazione Swift e `ContentView.swift` importa realmente `MycoCore`.

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

## Confine del prossimo batch

P3 trasferirà i modelli puri in gruppi piccoli, iniziando da tier/fattori e catalogo specie. Nessuna formula scientifica viene modificata durante il trasferimento; i 20 scenari P1 devono restare invariati.
