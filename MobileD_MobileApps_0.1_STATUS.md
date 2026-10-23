# MobileD Android — Stato verificato 23 ottobre 2026

Release corrente: **2.0.12**, versionCode **25**, applicationId `com.mobiled.android.test`.

## Problema e correzione

Il log del telefono con 2.0.11 confermava `IllegalStateException: Slider value(255.0) ... valueTo(100.0)` durante il disegno della schermata. La connessione AP o alla rete non era la causa. Dal commit `380e9f7` del 22 ottobre il frame conservava correttamente Brightness Matrix fino a 255, ma Colore continuava a usare direttamente quel valore come percentuale. Ora la UI converte 255→100%, 128→50%, 0→0%, lasciando invariato il frame.

La precedente compilazione, l'avvio dell'app e i test JVM non coprivano l'apertura di questa schermata nello stato Matrix a 255. Sono state aggiunte prove Android con disegno effettivo delle viste.

Ulteriori correzioni verificate:

- La cache locale `prevLightCommand`, che può contenere una palette JSONArray, non viene serializzata tra Activity. Identità e Discovery restano integri per luci e gruppi.
- I preset Colore non possono assegnare percentuali fuori 0–100 ai cursori.
- Il ripristino automatico della gerarchia non sovrascrive la luminosità colore con il valore del bianco a causa dell'ID condiviso dai due slider.
- I callback del controller usano i frammenti effettivamente ripristinati dopo la ricreazione.
- Controller e righe Home rimuovono soltanto i propri listener al distacco o alla distruzione, li ripristinano al riaggancio e ignorano callback obsoleti. Gli aggiornamenti grafici offline dei gruppi avvengono sul main thread.

## Verifica

- Build finale riuscita: test JVM, assembleRelease, lintDebug, assembleDebug e assembleDebugAndroidTest.
- **29 test JVM passati**, nessun failure/error/skip.
- **16 test Android passati** nell'emulatore API 34: 9 aperture/preset/ricreazione/listener, 6 prove dei payload ON/OFF, 1 verifica del package.
- Lint: zero errori/fatal; firma, versione, permessi, risorse e asset About verificati.
- Release installata sul telefono Xiaomi M2101K9AG / Android 13 senza cancellazione dati. Avvio COLD riuscito in 1636 ms. Hash dell'APK installata identico alla consegna.
- **Apertura della luce reale confermata dall'utente: «Sì, ora funziona».**
- La suite automatica sul telefono non è dichiarata passata: MIUI ha negato gli avvii Activity in background (`MIUILOG- Permission Denied Activity`). Il tentativo è conservato nel report dedicato; il pacchetto ausiliario di test è stato rimosso e sul telefono è presente la release finale.

La cattura audio reale, Tap Sync fisico e DMX elettrico non sono inclusi in questa verifica del crash.

## Menu laterale e consegna

Help & Support, Terms and Conditions e About sono stati controllati. About usa `BuildConfig.VERSION_NAME` e visualizza 2.0.12. Gli altri contenuti descrivono ancora correttamente le funzioni disponibili; non è presente una voce FAQ separata. La regola di verificarli e aggiornarli sempre insieme all'app è salvata in `AGENTS.md`.

Il riferimento funzionale v1.8 resta invariato come revisione del contratto; la sua intestazione 2.0.11 è storica. La versione della consegna corrente è quella indicata qui e nel README.

APK: `MobileD_Android_2.0.12.apk`.

SHA-256 APK: `685de51b9fa2c52b8c66a055a2b5a25992bd0e447a14d5d7cd8cd05a41303992`.

Snapshot: 322 file, SHA-256 aggregato `892d3969620baaf2dc6df2b40c4d76aa8795ae9c2dbcc24e5f6dc86608a8539d`.

I report sono in `dist/build-verification.json` e `dist/reports`. La consegna precedente, il log originale del crash e i tentativi intermedi sono conservati in `.maintenance/2026-10-23/controller-crash-2.0.11`.
