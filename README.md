# MobileD Android

App Android nativa per controllo Color, Legacy, Matrix e Music dei dispositivi MobileD. Il progetto si può modificare e compilare da terminale; Android Studio è facoltativo.

Versione corrente verificata: **2.0.11**, versionCode **24**. Aggiorna About con due schede prodotto, MobileD 20 W e MobileD XL 40 W, e mostra la versione App automaticamente da `BuildConfig.VERSION_NAME`. Le specifiche distinte comprendono batterie, ricarica, dimensioni e peso; i dati provengono dal manuale inglese 2026 fornito.

La build release 2.0.11 è riuscita in 7 minuti e 41 secondi; 26 test JVM sono passati senza failure/error/skip e il lint finale non riporta errori/fatal. Il pacchetto ha superato la verifica dei 30 file consegnati, della firma e dell'asset About corrispondente ai sorgenti, con modelli 20/40 W e versione automatica.

L'installazione sul telefono è riuscita e l'avvio a freddo COLD ha restituito Status OK in 993 ms. Sono verificati versione 2.0.11, versionCode 24 e lastUpdateTime del 22 ottobre 2026 alle 14:45:54, ora Europe/Rome. I dettagli sono in `dist/build-verification.json`. La prova visiva della schermata About resta in attesa dell'apertura manuale: il dispositivo ha rifiutato i tap ADB; nessuna impostazione di sicurezza è stata modificata.

Il riferimento corrente è [MobileD_MobileApps_Functional_Reference_v1.8.md](MobileD_MobileApps_Functional_Reference_v1.8.md), disponibile anche in PDF. Versioni storiche e checkpoint mobile precedenti sono conservati in `docs/archive`.

## Build locale

La configurazione usa Gradle wrapper 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.0.0 e Android SDK 34. minSdk è 26; la cattura playback Music richiede Android 10/API 29 o successivo, consenso MediaProjection e una sorgente che consenta la cattura.

Nel terminale PowerShell aperto nella cartella del progetto, usare un JDK 21 installato. Per l'ambiente locale corrente:

```powershell
$env:JAVA_HOME = 'C:\Users\andre\.jdks\jbr-21.0.11'
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
.\gradlew.bat --offline --console=plain :app:testDebugUnitTest :app:assembleDebug
```

`local.properties` deve indicare l'SDK locale; nell'ambiente corrente si trova in `C:\Users\andre\AppData\Local\Android\Sdk`. La modalità `--offline` richiede che dipendenze e wrapper siano già nella cache. Per la release usare `:app:assembleRelease` con la configurazione di firma locale prevista dal progetto. Le credenziali restano nei file locali e non devono comparire nei report o negli archivi distribuibili.

La verifica completa della consegna esegue `:app:testDebugUnitTest :app:assembleRelease :app:lintDebug`. Dopo una build finale riuscita, con lo snapshot dei sorgenti e il log registrati in `build/reports/alignment`, il comando `python .\scripts\package-android-release.py` verifica e prepara la cartella `dist`. Lo script controlla i test JVM attesi per Music e routing, l'assenza di errori/fatal nel lint finale, metadati della release, firma tramite apksigner SDK 34, permessi audio, servizio di cattura e risorse icona; arresta il confezionamento se una verifica fallisce. Non compila e non installa l'APK.

Gli APK prodotti da Gradle si trovano in `app/build/outputs/apk/debug` e `app/build/outputs/apk/release`. L'applicationId è `com.mobiled.android.test`; una build non comporta l'installazione automatica su telefono.

## Consegna e verifica

- `dist`: APK e report della consegna Android.
- `dist/docs`: riferimento corrente Markdown/PDF.
- `dist/build-verification.json`: versioni, artefatti ed esiti registrati della consegna.
- `build/reports/alignment/final-build.log`: log della verifica di build corrente.
- `app/build/reports/tests/testDebugUnitTest`: report dei test JVM.
- `docs/archive`: documenti precedenti, conservati per confronto.
- `.maintenance/2026-10-22/source-backup`: copie dei sorgenti anteriori alle correzioni della sessione.

La presenza di istruzioni di build non certifica che siano state eseguite: verificare il report della consegna. I test su telefono, cattura audio reale e DMX elettrico restano distinti dai test JVM e dalla compilazione. Il riferimento v1.8 contiene le prove funzionali da eseguire sui dispositivi.

I comandi JSON usano UDP 8889; Discovery usa UDP 8232; gli universi degli effetti usano sACN/E1.31 multicast UDP 5568. GPort è un identificatore di gruppo, non una porta di destinazione. L'App mobile non aggiunge in questa revisione un aggiornamento firmware nativo; combined e OTA restano nei progetti firmware.

Il Master viene salvato come unico M anche quando altri membri sono offline; le identificazioni seguono l'IP Discovery corrente. La navigazione Indietro e i callback di autorizzazione Music rispettano il ciclo del controller e la disponibilità delle API Android. Le prove di cattura e DMX su dispositivi restano elencate nel riferimento funzionale.

Tap Sync degli effetti Matrix 101 e 105 usa il pulsante con LED nello stile PC e aggiorna BPM, slider e valore Speed visibili. Il flash dura 120 ms al tocco; non è un metronomo automatico. Sono passati 4 test Tap Tempo, 15 Music, 6 routing e 1 test esistente, per 26 test JVM complessivi della 2.0.11.

APK: `dist/MobileD_Android_2.0.11.apk`; SHA-256 `6205f485fdaa104541bcac06e771bf9e444cf8f0c8ed7d985ee648232587ee57`. Lo snapshot contiene 320 file con impronta `e15436cad4ccffcb31511b016653d9448652efc0c0d3e95ffcef55b19746955b`, corrispondenti agli input della build.

About è verificata nell'asset confezionato; restano da verificare la sua resa visiva sul telefono, Tap Sync fisico, cattura audio reale e comportamento UDP/DMX dei fari. Installazione e avvio non sostituiscono queste prove funzionali.
