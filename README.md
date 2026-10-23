# MobileD Android

App Android nativa per controllo Color, Legacy, Matrix e Music dei dispositivi MobileD. Il progetto si può modificare e compilare da terminale; Android Studio è facoltativo.

Versione corrente verificata: **2.0.12**, versionCode **25**. Corregge il crash all'apertura di luci con luminosità Matrix e i problemi collegati a preset, palette, ripristino del controller e listener di rete. About mostra automaticamente la versione da `BuildConfig.VERSION_NAME` e mantiene le schede MobileD 20 W e MobileD XL 40 W.

La release del 23 ottobre 2026 supera **29 test JVM** e **16 test Android su emulatore API 34**. Il lint finale non riporta errori/fatal. Firma, metadati, permessi, servizio Music, icone e asset About identico ai sorgenti sono verificati.

La release 2.0.12 è installata con aggiornamento sul telefono Xiaomi M2101K9AG / Android 13, mantenendo i dati dell'app. L'APK installata corrisponde byte per byte alla consegna e l'avvio a freddo è riuscito. L'utente ha confermato che la luce prima in crash ora si apre correttamente. La suite automatica sul telefono è stata bloccata dai permessi MIUI di avvio Activity in background; non è dichiarata superata. Le prove automatiche Android completate sono quelle nell'emulatore.

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
- `.maintenance/2026-10-23/release-before`: consegna, report e script precedenti alla nuova release.

La presenza di istruzioni di build non certifica che siano state eseguite: verificare il report della consegna. I test su telefono, cattura audio reale e DMX elettrico restano distinti dai test JVM e dalla compilazione. Il riferimento v1.8 contiene le prove funzionali da eseguire sui dispositivi.

I comandi JSON usano UDP 8889; Discovery usa UDP 8232; gli universi degli effetti usano sACN/E1.31 multicast UDP 5568. GPort è un identificatore di gruppo, non una porta di destinazione. L'App mobile non aggiunge in questa revisione un aggiornamento firmware nativo; combined e OTA restano nei progetti firmware.

Il Master viene salvato come unico M anche quando altri membri sono offline; le identificazioni seguono l'IP Discovery corrente. La navigazione Indietro e i callback di autorizzazione Music rispettano il ciclo del controller e la disponibilità delle API Android. Le prove di cattura e DMX su dispositivi restano elencate nel riferimento funzionale.

Tap Sync degli effetti Matrix 101 e 105 usa il pulsante con LED nello stile PC e aggiorna BPM, slider e valore Speed visibili. Il flash dura 120 ms al tocco; non è un metronomo automatico. Sono passati 4 test Tap Tempo, 15 Music, 6 routing, 3 Discovery/selezione gruppo e 1 test esistente, per 29 test JVM complessivi della 2.0.12.

APK: `dist/MobileD_Android_2.0.12.apk`; SHA-256 `685de51b9fa2c52b8c66a055a2b5a25992bd0e447a14d5d7cd8cd05a41303992`. Lo snapshot contiene 322 file con impronta `892d3969620baaf2dc6df2b40c4d76aa8795ae9c2dbcc24e5f6dc86608a8539d`, corrispondenti agli input della build.

About è verificata nell'asset confezionato; restano da verificare la sua resa visiva sul telefono, Tap Sync fisico, cattura audio reale e comportamento UDP/DMX dei fari. Installazione e avvio non sostituiscono queste prove funzionali.

Discovery porta una luce offline soltanto dopo più di 5000 ms senza rilevamenti validi; il primo rilevamento la riporta online. La selezione del gruppo congela i membri online e i loro ID. Color e Power usano questa selezione; Legacy, Mic, Matrix e Music usano il prefisso consecutivo da ID 0 fino al primo ID mancante. Gli ID duplicati sono ammessi e condividono una posizione. Music può partire anche se altri membri del gruppo hanno ID oltre un buco.

## Correzioni 2.0.12

La regressione del 22 ottobre lasciava passare luminosità Matrix fino a 255 verso uno slider Colore con massimo 100. La UI ora converte correttamente in percentuale e mantiene intatto il valore del protocollo. Sono corretti anche trasporto della cache palette tra Activity, preset fuori intervallo, ripristino dei due slider, frammenti ripristinati e gestione dei listener di controller e righe Home.

I dettagli della verifica sono in [Correzioni Android 2.0.12](dist/docs/Correzioni_Android_2.0.12.md). Il riferimento funzionale v1.8 resta la revisione storica del contratto: la sua intestazione 2.0.11 non indica la versione della consegna corrente.

La regola di controllare e aggiornare anche Help & Support, Terms and Conditions e About a ogni modifica dell'app è registrata in [AGENTS.md](AGENTS.md). In questa release About mostra 2.0.12 automaticamente; gli altri contenuti del menu sono stati verificati e restano coerenti con le funzioni disponibili.
