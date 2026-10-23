# MobileD Android

App Android nativa per controllo Color, Legacy, Matrix e Music dei dispositivi MobileD. Il progetto si può modificare e compilare da terminale; Android Studio è facoltativo.

Release corrente: **2.0.15**, versionCode **28**. Le informazioni del menu e gli avvisi usano istruzioni pratiche per il cliente, senza dettagli interni di implementazione. Color, Legacy, Matrix e Music mantengono le loro funzioni.

Build release, **39 test JVM** e lint senza errori/fatal passano per la 2.0.15. I tre asset About, Help & Support e Terms and Conditions sono confrontati con quelli nell'APK e controllati per evitare dettagli riservati. About mantiene la versione automatica.

La 2.0.15 è installata sul telefono Xiaomi M2101K9AG / Android 13 senza cancellazione dati; hash della release installata, versione e avvio a freddo sono verificati. Non sono stati ripetuti test strumentali o prove fisiche delle luci per questa revisione dei testi; le 20 prove Android e la conferma dei dieci slot restano evidenze della 2.0.14.

Il riferimento corrente è [MobileD_MobileApps_Functional_Reference_v1.8.md](MobileD_MobileApps_Functional_Reference_v1.8.md), disponibile anche in PDF. Include la correzione del contratto Legacy per Android 2.0.13 e PC 0.20.46 e la griglia Color di Android 2.0.14. Versioni storiche e checkpoint mobile precedenti sono conservati in `docs/archive`.

## Informazioni nell'app: 2.0.15

About, Help & Support, Terms and Conditions e i messaggi dell'interfaccia devono descrivere le funzioni disponibili, i requisiti e le azioni da compiere con un linguaggio adatto all'utente. I dettagli interni della comunicazione tra app e dispositivi non appartengono a queste informazioni. La versione di About continua a provenire da `BuildConfig.VERSION_NAME`.

La revisione mantiene le funzioni e i dieci slot colore. Build, test JVM, firma, asset informativi nell'APK e installazione sono verificati nei report della 2.0.15.

I report in `dist` documentano la 2.0.15; la consegna 2.0.14 è archiviata localmente.

## Storico Color: dieci slot nella 2.0.14

La schermata Color presenta dieci slot in due righe da cinque. Un tocco richiama un colore salvato; una pressione prolungata salva il colore corrente nello slot o lo sovrascrive. Ogni slot conserva il colore e i valori di Color Brightness e White Brightness. I cinque salvataggi esistenti mantengono la loro posizione, mentre i cinque slot aggiuntivi sono indipendenti.

Help & Support è aggiornato alla nuova griglia. Terms and Conditions e About sono stati ricontrollati; About continua a usare `BuildConfig.VERSION_NAME`. Le prove dei dieci slot della 2.0.14 sono conservate nello storico locale; i report in `dist` descrivono la release corrente.

## Correzione Legacy 2.0.13

Senza Master configurato, Legacy funziona in locale su tutti i membri della selezione attualmente raggiungibili: non richiede ruoli M/S, ID 0, ID validi o ID consecutivi. I comandi usano il ruolo diretto X, anche se nel gruppo sono rimasti ruoli S.

Se è configurato un Master, Legacy usa la sincronizzazione: deve esserci un unico M con ID 0, selezionato e raggiungibile; partecipano soltanto gli ID consecutivi da 0 fino al primo buco, inclusi tutti i duplicati. Un Master configurato male, non selezionato o non raggiungibile richiede una segnalazione specifica e non deve trasformare il gruppo in locale. Matrix e Music mantengono le rispettive regole.

Help & Support descrive entrambe le modalità. Terms and Conditions e About sono stati ricontrollati; About continua a ricavare la versione da `BuildConfig.VERSION_NAME`. Gli esiti della 2.0.13 sono archiviati localmente prima del confezionamento della 2.0.14; le relative prove restano attribuite alla 2.0.13.

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

L'App controlla i dispositivi MobileD sulla rete locale e distingue gli effetti generati dai fari dalle funzioni Music del telefono. Questa revisione non aggiunge un aggiornamento firmware nativo; la gestione del firmware resta nelle funzioni dedicate del dispositivo.

Il Master viene salvato come unico M anche quando altri membri sono offline; le identificazioni seguono l'IP Discovery corrente. La navigazione Indietro e i callback di autorizzazione Music rispettano il ciclo del controller e la disponibilità delle API Android. Le prove di cattura e DMX su dispositivi restano elencate nel riferimento funzionale.

Tap Sync degli effetti Matrix 101 e 105 usa il pulsante con LED nello stile PC e aggiorna BPM, slider e valore Speed visibili. Il flash dura 120 ms al tocco; non è un metronomo automatico. Sono passati 4 test Tap Tempo, 15 Music, 6 routing, 3 Discovery/selezione gruppo e 1 test esistente, per 29 test JVM complessivi della 2.0.12.

Storico 2.0.12: APK `MobileD_Android_2.0.12.apk`; SHA-256 `685de51b9fa2c52b8c66a055a2b5a25992bd0e447a14d5d7cd8cd05a41303992`. Lo snapshot contiene 322 file con impronta `892d3969620baaf2dc6df2b40c4d76aa8795ae9c2dbcc24e5f6dc86608a8539d`, corrispondenti agli input della build.

About è verificata nell'asset confezionato; restano da verificare la sua resa visiva sul telefono, Tap Sync fisico, cattura audio reale e comportamento UDP/DMX dei fari. Installazione e avvio non sostituiscono queste prove funzionali.

Discovery porta una luce offline soltanto dopo più di 5000 ms senza rilevamenti validi; il primo rilevamento la riporta online. La selezione del gruppo congela i membri online e i loro ID. Color, Power e Legacy senza Master usano questa selezione completa; Legacy sincronizzato con Master, Matrix e Music usano il prefisso consecutivo da ID 0 fino al primo ID mancante. Gli ID duplicati sono ammessi e condividono una posizione. Music può partire anche se altri membri del gruppo hanno ID oltre un buco.

## Correzioni 2.0.12

La regressione del 22 ottobre lasciava passare luminosità Matrix fino a 255 verso uno slider Colore con massimo 100. La UI ora converte correttamente in percentuale e mantiene intatto il valore del protocollo. Sono corretti anche trasporto della cache palette tra Activity, preset fuori intervallo, ripristino dei due slider, frammenti ripristinati e gestione dei listener di controller e righe Home.

I dettagli della verifica sono in [Correzioni Android 2.0.12](dist/docs/Correzioni_Android_2.0.12.md). Il riferimento funzionale v1.8 è stato aggiornato per il contratto Legacy della 2.0.13; le prove della correzione del crash 2.0.12 restano distinte dalla nuova verifica.

La regola di controllare e aggiornare anche Help & Support, Terms and Conditions e About a ogni modifica dell'app è registrata in [AGENTS.md](AGENTS.md). Nella release verificata 2.0.12 About mostra la versione automaticamente. Per la 2.0.13 Help & Support è aggiornato alla distinzione fra Legacy locale e sincronizzato; la verifica degli asset confezionati va ripetuta sulla nuova APK.
