# MobileD Android — Stato del 23 ottobre 2026

Release corrente: **2.0.15**, versionCode **28**, applicationId `com.mobiled.android.test`. Build release, 39 test JVM, lint e controllo degli asset informativi nell'APK passano.

## 2.0.15: informazioni e messaggi nell'app

La revisione rimuove dettagli interni di implementazione dalle informazioni del menu laterale e dai messaggi rivolti all'utente. About, Help & Support e Terms and Conditions devono restare coerenti con le funzioni disponibili e fornire le indicazioni necessarie all'uso. About mantiene la versione dinamica da `BuildConfig.VERSION_NAME`.

Color con dieci slot, Legacy, Matrix e Music mantengono le loro funzioni. La 2.0.15 è installata sul telefono senza cancellazione dati; hash, versione e avvio sono verificati. Non sono ripetute le prove strumentali o fisiche della 2.0.14. I report in `dist` riguardano solo la nuova consegna; la precedente è archiviata localmente.

## Storico: release 2.0.14 (versionCode 27)

Build release, **39 test JVM** e **20 test Android su emulatore API 34** passano per la 2.0.14.

### Color 2.0.14: dieci slot su due righe

La griglia di salvataggio colori passa da cinque a dieci slot, disposti in due righe da cinque. Il tocco richiama il colore salvato; la pressione prolungata salva o sovrascrive lo slot con colore, Color Brightness e White Brightness correnti. I primi cinque salvataggi vengono conservati; gli slot dal sesto al decimo sono indipendenti.

Help & Support e il riferimento v1.8 descrivono la nuova griglia. About mantiene la versione dinamica da `BuildConfig.VERSION_NAME`; Terms and Conditions non richiede modifiche per questa funzione. I dieci slot sono visibili in due righe da cinque. Salvataggio, richiamo e persistenza dello slot 10 sono verificati nell'emulatore; i valori dei primi cinque slot restano invariati. La 2.0.14 è installata sul telefono senza cancellare dati; l'utente conferma la disposizione corretta. Asset del menu, hash, versione e avvio sono verificati. Report in `dist/build-verification.json` e `dist/reports/color-presets-ui.json`.

## Storico: release 2.0.13 (versionCode 26)

Build release, 39 test JVM e 20 test Android su emulatore API 34 passano per la 2.0.13.

### Correzione Legacy 2.0.13

Il contratto corretto distingue due modalità. Senza Master configurato, Legacy deve inviare comandi diretti X a tutti i membri selezionati attualmente raggiungibili, anche con ID mancanti, non validi o non consecutivi e con ruoli Slave residui. Con Master configurato, deve esserci un unico M a ID 0, selezionato e raggiungibile; si usano soltanto gli ID consecutivi da 0, mantenendo tutti i duplicati. Un Master non valido o non raggiungibile non abilita la modalità locale.

Help & Support e il riferimento v1.8 sono aggiornati a questa regola; About mantiene la versione dinamica da `BuildConfig.VERSION_NAME` e Terms and Conditions non richiede modifiche per questa correzione. La 2.0.13 è installata e avviata sul telefono Xiaomi senza cancellare dati; hash e versione sono verificati. L'utente conferma Legacy e parametri su tutte le luci del gruppo senza Master: «Funziona su tutte le luci». La sincronizzazione M0/prefisso è verificata automaticamente con UDP su loopback nell'emulatore. I report della 2.0.13 sono stati registrati in `dist/build-verification.json` e `dist/Build_Validation.md`; sono archiviati localmente prima del confezionamento della 2.0.14. Le prove storiche qui sotto riguardano soltanto la 2.0.12.

## Storico: release 2.0.12 (versionCode 25)

### Problema e correzione

Il log del telefono con 2.0.11 confermava `IllegalStateException: Slider value(255.0) ... valueTo(100.0)` durante il disegno della schermata. La connessione AP o alla rete non era la causa. Dal commit `380e9f7` del 22 ottobre il frame conservava correttamente Brightness Matrix fino a 255, ma Colore continuava a usare direttamente quel valore come percentuale. Ora la UI converte 255→100%, 128→50%, 0→0%, lasciando invariato il frame.

La precedente compilazione, l'avvio dell'app e i test JVM non coprivano l'apertura di questa schermata nello stato Matrix a 255. Sono state aggiunte prove Android con disegno effettivo delle viste.

Ulteriori correzioni verificate:

- La cache locale `prevLightCommand`, che può contenere una palette JSONArray, non viene serializzata tra Activity. Identità e Discovery restano integri per luci e gruppi.
- I preset Colore non possono assegnare percentuali fuori 0–100 ai cursori.
- Il ripristino automatico della gerarchia non sovrascrive la luminosità colore con il valore del bianco a causa dell'ID condiviso dai due slider.
- I callback del controller usano i frammenti effettivamente ripristinati dopo la ricreazione.
- Controller e righe Home rimuovono soltanto i propri listener al distacco o alla distruzione, li ripristinano al riaggancio e ignorano callback obsoleti. Gli aggiornamenti grafici offline dei gruppi avvengono sul main thread.

### Verifica 2.0.12

- Build finale riuscita: test JVM, assembleRelease, lintDebug, assembleDebug e assembleDebugAndroidTest.
- **29 test JVM passati**, nessun failure/error/skip.
- **16 test Android passati** nell'emulatore API 34: 9 aperture/preset/ricreazione/listener, 6 prove dei payload ON/OFF, 1 verifica del package.
- Lint: zero errori/fatal; firma, versione, permessi, risorse e asset About verificati.
- Release installata sul telefono Xiaomi M2101K9AG / Android 13 senza cancellazione dati. Avvio COLD riuscito in 1636 ms. Hash dell'APK installata identico alla consegna.
- **Apertura della luce reale confermata dall'utente: «Sì, ora funziona».**
- La suite automatica sul telefono non è dichiarata passata: MIUI ha negato gli avvii Activity in background (`MIUILOG- Permission Denied Activity`). Il tentativo è conservato nel report dedicato; il pacchetto ausiliario di test è stato rimosso e sul telefono è presente la release finale.

La cattura audio reale, Tap Sync fisico e DMX elettrico non sono inclusi in questa verifica del crash.

### Menu laterale e consegna 2.0.12

Help & Support, Terms and Conditions e About sono stati controllati. About usa `BuildConfig.VERSION_NAME` e visualizza 2.0.12. Gli altri contenuti descrivono ancora correttamente le funzioni disponibili; non è presente una voce FAQ separata. La regola di verificarli e aggiornarli sempre insieme all'app è salvata in `AGENTS.md`.

Il riferimento funzionale v1.8 contiene ora il contratto Legacy corretto per la 2.0.13. I risultati di build, hash e verifica del telefono elencati in questa sezione restano attribuiti esclusivamente alla 2.0.12.

APK: `MobileD_Android_2.0.12.apk`.

SHA-256 APK: `685de51b9fa2c52b8c66a055a2b5a25992bd0e447a14d5d7cd8cd05a41303992`.

Snapshot: 322 file, SHA-256 aggregato `892d3969620baaf2dc6df2b40c4d76aa8795ae9c2dbcc24e5f6dc86608a8539d`.

I report della consegna 2.0.12 sono stati registrati in `dist/build-verification.json` e `dist/reports`; nella preparazione della nuova release vanno archiviati e sostituiti solo con evidenze della 2.0.13. La consegna precedente, il log originale del crash e i tentativi intermedi sono conservati in `.maintenance/2026-10-23/controller-crash-2.0.11`.
