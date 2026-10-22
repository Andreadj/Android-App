# MobileD Android — Stato corrente

**22 ottobre 2026 — App 2.0.11, versionCode 24; riferimento funzionale v1.8.**

Il checkpoint storico Android/iOS del 6 ottobre 2026 è conservato in `docs/archive`. Questo aggiornamento riguarda Android; non dichiara nuove build o verifiche iOS.

La revisione 2.0.11 aggiorna About: schede separate MobileD 20 W e MobileD XL 40 W con batterie, tempi di ricarica, dimensioni e pesi specifici; dati comuni di ottiche, IP20, Wi-Fi e DMX. La versione visibile viene da BuildConfig.VERSION_NAME. Le caratteristiche sono ricavate dal manuale inglese 2026 fornito; il riferimento v1.8 riporta fonte e limiti dei dati.

Il contratto Android viene allineato al riferimento PC v8.6 e ai firmware ESP-IDF 1.0.10 / Arduino 3.3.70: frame Music sACN completi da 638 byte, routing Music esplicitamente ON senza riscrivere GState, palette RGBW BPM/Spectrum, gestione del ciclo di cattura, identità APName con IP/online Discovery correnti, ritrasmissioni per generazione e strip Matrix fino a PixelID 1023. Il numero di pixel resta stabile durante la sessione e la preview Matrix mostra al massimo 20 posizioni.

La cattura Music implementata è playback tramite AudioPlaybackCapture/MediaProjection su Android 10 o successivo, con consenso e sorgente che consenta la cattura. Non è un acquisitore del microfono del telefono. Gli effetti Matrix audio usano invece il microfono del MobileD.

I frame Music comprendono tutta la strip configurata, incluse le posizioni divenute offline, per mantenere completa la replica dei fari RGBW esterni sul DMX OUT. Il riempimento separa gli universi ai confini 127/128 e include ID 1023; ogni coda inutilizzata resta a zero nei 512 slot. La presenza online governa gli invii, senza rimuovere pixel dalla strip della sessione.

Matrix e Music dei tre firmware inoltrano l'intero universo di 512 canali al DMX OUT. Music non dipende dal ruolo GState precedente; Matrix usa Master/Slave. Legacy distribuisce il colore uniforme RGBW sui canali 1–4. Il combined 8 MB è in chiaro e resta distinto dall'OTA 8 MB cifrata. Nessun aggiornamento firmware nativo viene aggiunto all'App mobile in questa revisione.

La differenza individuata tra il limite UI Matrix PC 255 e il contratto firmware/Android 1023 è dichiarata nel riferimento v1.8. Questa revisione Android non dichiara una correzione del runtime PC.

L'assegnazione del Master aggiorna a Slave anche gli altri membri offline, evitando un secondo Master al loro ritorno. L'identificazione usa l'IP Discovery corrente. Il controller gestisce Indietro tramite il dispatcher; i callback della cattura controllano permessi, richiesta attiva e disponibilità API prima dell'avvio. Le risorse grafiche dell'icona MobileD restano nel progetto; la loro presenza nell'APK è verificata durante il confezionamento e non sostituisce una prova nel launcher di un telefono.

Tap Sync Matrix 101/105 ora presenta pulsante e LED nello stile PC, BPM numerico corretto, flash ambra di 120 ms con cancellazione del timer precedente e aggiornamento dello slider/valore Speed visibili. Le formule PC restano invariate; non viene aggiunto un metronomo né un reset della fase firmware. Il controllo finale dei sorgenti non ha individuato criticità che richiedano modifiche all'APK congelata. I 4 test del calcolo Tap Tempo risultano passati.

La build release 2.0.11 è riuscita in 7 minuti e 41 secondi. Sono passati 26 test JVM (15 Music, 6 routing, 4 Tap Tempo, 1 esistente) senza failure/error/skip; lintDebug riporta zero errori/fatal. Il pacchetto ha superato la verifica di firma, metadati e 30 file consegnati. L'asset About confezionato corrisponde ai sorgenti e contiene i due modelli 20/40 W e la versione automatica. Gli esiti sono in `dist/build-verification.json` e `build/reports/alignment/final-build.log`.

I 320 file dello snapshot corrispondono agli input della build; impronta SHA-256 aggregata: `e15436cad4ccffcb31511b016653d9448652efc0c0d3e95ffcef55b19746955b`. L'APK `dist/MobileD_Android_2.0.11.apk` ha SHA-256 `6205f485fdaa104541bcac06e771bf9e444cf8f0c8ed7d985ee648232587ee57`.

La versione 2.0.11 è stata installata sul telefono con esito Success e avviata a freddo COLD con Status OK in 993 ms. Sono confermati versionName 2.0.11, versionCode 24 e lastUpdateTime del 22 ottobre 2026 alle 14:45:54, ora Europe/Rome. La precedente 2.0.10 resta archiviata nella manutenzione della sua release.

La resa visiva della nuova About sul telefono non è ancora verificata: Xiaomi ha rifiutato `adb input tap` con `SecurityException: INJECT_EVENTS`; è stata richiesta l'apertura manuale Menu → About. Non sono state cambiate impostazioni di sicurezza. Restano da verificare l'interazione fisica Tap Sync, la cattura audio reale, l'invio/ricezione UDP sui MobileD e il DMX elettrico. Non si dichiarano nuove prove su emulatore.

Per comandi di build e disposizione degli artefatti consultare `README.md`; per comportamento dettagliato e prove ancora necessarie consultare `MobileD_MobileApps_Functional_Reference_v1.8.md` e il PDF corrispondente.
