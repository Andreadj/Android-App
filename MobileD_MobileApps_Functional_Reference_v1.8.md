# MobileD Android — Riferimento funzionale

**Revisione v1.8 — 22 ottobre 2026**  
App Android **2.0.11**, versionCode **24** · PC App **0.20.45**, riferimento **v8.6** · ESP-IDF **1.0.10** · Arduino **3.3.70**

## 1. Ambito e riferimenti

Questo documento descrive il contratto Android corrente per comandi, gruppi, Matrix, Music e integrazione con i firmware MobileD. Sostituisce come riferimento corrente la revisione mobile v1.7 del 19 ottobre 2026. Le copie storiche, compreso il Markdown v1.7 con le modifiche locali preesistenti, sono conservate in `docs/archive` del progetto Android.

Il riferimento PC v8.6 definisce il protocollo condiviso; i riferimenti delle varianti firmware descrivono ricezione, rendering, priorità e DMX OUT. Le funzioni specifiche di Windows, in particolare la cattura WASAPI e gli aggiornamenti firmware dall'App PC, non diventano automaticamente funzioni Android. Questa revisione riguarda Android; non certifica l'allineamento dell'App iOS.

Discovery resta la fonte dello stato riportato dal dispositivo. Un comando inviato non costituisce conferma della sua applicazione. Le verifiche di compilazione e i test automatici sono distinti dalle prove sul telefono e sui fari: gli esiti della consegna sono registrati in `dist/build-verification.json` e nei report di `build/reports/alignment`.

## 2. Protocollo e identificatori

| Elemento | Regola |
|---|---|
| Discovery | UDP 8232 |
| Comandi e configurazione App → MobileD | Unicast UDP 8889 all'IP corrente del destinatario |
| Frame degli effetti distribuiti | sACN/E1.31 multicast UDP 5568 |
| Primary Art-Net | UDP 6454, con universo di ingresso configurato nel firmware |
| `Command` | 0 = OFF, 1 = ON, 2 = FLASH/identificazione |
| `GLights` | 0 = Color; 1–99 = Legacy; 100 = Music; 101–199 = Matrix |
| `GState` | X = locale/diretto; M = Master; S = Slave |
| `GPort` | Identificatore del gruppo nel JSON; non è una porta UDP di trasmissione |
| `GUniverse` | Universo base del gruppo per i frame distribuiti |

Color usa il percorso diretto X. Legacy può essere locale X o distribuito M/S. Matrix usa un Master che genera la strip e Slave che ricevono i propri pixel. Music è generata dall'App e non assegna un Master musicale ai fari: il runtime GLights=100 non dipende dal precedente ruolo Master/Slave.

Tutti i comandi JSON continuano a usare UDP 8889, qualunque sia il valore di GPort. Tutti i frame distribuiti usano UDP 5568. L'indirizzo sACN è `239.255.<byte alto universo>.<byte basso universo>`.

| Gruppo | Identificatore e prenotazione |
|---|---|
| All Devices | `GPort=8890`; base fissa `GUniverse=32000`; blocco riservato 32000–32009 |
| Gruppo utente | Identificatore persistito; universo base assegnato tra 32010 e 35000 inclusi; prenotazione di dieci universi da base a base+9 |

L'allocatore evita sovrapposizioni, salva la base e la conserva nell'import/export. Una base importata assente o incompatibile richiede un blocco libero. Eliminare un gruppo libera la sua prenotazione senza rinumerare gli altri. Il limite 35000 riguarda la base assegnabile: gli universi derivati possono superarlo entro il blocco prenotato.

## 3. Formato sACN e pixel RGBW

Android trasmette frame completi da **638 byte**, contenenti start code DMX zero e **512 slot**. Il precedente formato da 639 byte non descrive il trasporto corrente. Gli offset seguenti sono zero-based; i campi numerici multibyte usano l'ordine big-endian.

| Campo | Offset / valore |
|---|---|
| Preamble / postamble | 0 / 16; 2 / 0 |
| Identificatore ACN | 4; identificatore ASC-E1.17 di 12 byte, con tre zeri finali |
| Root flags/length; vector | 16 / 0x726E; 18 / 4 |
| Framing flags/length; vector | 38 / 0x7258; 40 / 2 |
| Priority | 108 / 100 |
| Sequence; options | 111; 112 |
| Universe | 113; 16 bit |
| DMP flags/length; vector; address type | 115 / 0x720B; 117 / 2; 118 / 0xA1 |
| First property address; address increment | 119 / 0; 121 / 1 |
| Property count | 123 / 513, incluso lo start code |
| Start code; primo slot dati | 125 / 0; 126 |

Ogni posizione della strip contiene quattro valori, in ordine **R, G, B, W**. Con 128 pixel per universo, la mappatura è:

- universo = `GUniverse + floor(PixelID / 128)`;
- primo canale DMX, numerato da 1 = `(PixelID mod 128) × 4 + 1`;
- PixelID 0 usa i canali 1–4 della base; 127 usa 509–512; 128 usa 1–4 della base+1; 1023 usa 509–512 della base+7.

Il campo Priority appartiene al frame del relativo universo; non sostituisce GPort e non assegna la priorità Primary DMX. Ogni universo è un flusso distinto. La trasmissione Music è limitata a circa 30 aggiornamenti al secondo.

## 4. Gruppi, Discovery e selezione

L'appartenenza ai gruppi persiste durante le disconnessioni. L'identità stabile è APName; IP e stato online provengono dal Discovery corrente. Prima dell'invio, anche nelle ritrasmissioni, il destinatario viene risolto di nuovo: un vecchio IP o uno stato online catturato in precedenza non autorizza l'invio.

Un comando viene inviato subito. Se dopo 50 ms non è stato sostituito da un comando più recente per quel destinatario, sono previste tre ritrasmissioni distanziate di 15 ms. La generazione del comando impedisce che una coda precedente sovrascriva una selezione più recente. Il contenuto identico a un invio passato non costituisce una conferma permanente e non deve impedire di riapplicare lo stesso comando dopo una modifica esterna.

All Devices è il gruppo di sistema: comprende l'inventario MobileD, non si rinomina e non consente di modificare manualmente l'appartenenza. Un dispositivo offline resta nell'inventario ma non riceve comandi. La schermata dei gruppi conserva la configurazione anche quando alcuni dispositivi sono temporaneamente assenti.

L'assegnazione del Master mette il dispositivo scelto online a PixelID 0 e conserva un unico ruolo M. Gli altri membri del gruppo diventano S anche se temporaneamente offline: il salvataggio non deve lasciare un vecchio Master offline da ritrovare al ritorno in rete. Un invio di identificazione risolve APName e IP correnti, anziché usare un indirizzo conservato nella riga dell'editor.

La selezione Matrix cattura i partecipanti online con IP valido e conserva il prefisso di PixelID distinti consecutivi da 0 fino al primo vuoto. Per esempio, gli ID `0,1,2,2,4,5` producono una strip di tre posizioni, con entrambi i dispositivi ID 2 partecipanti. I duplicati replicano il pixel e non aumentano PixelCount.

Lo snapshot conserva appartenenza, PixelID e PixelCount durante l'uso: se un partecipante scompare, non vengono spostate le posizioni degli altri. Lo stato online e l'IP restano invece aggiornati dal Discovery. Un ritorno online riutilizza la posizione conservata. Reselezionare il gruppo ricalcola lo snapshot e permette di includere nuovi partecipanti o nuove posizioni.

Music risolve i partecipanti online all'avvio o al cambio gruppo; gli ID distinti devono essere consecutivi da 0. Durante lo streaming il numero di pixel e i relativi slot restano stabili. Un dispositivo offline non interrompe gli altri slot; al ritorno conserva la propria posizione. Il ricalcolo richiede una nuova selezione del gruppo.

La generazione Music riempie la strip configurata completa, comprese le posizioni di partecipanti divenuti offline. L'assenza di un MobileD non azzera il suo pixel nell'universo trasmesso: i fari RGBW collegati al DMX OUT di un altro ricevitore possono replicare tutte le posizioni di quell'universo. Ogni universo contiene 512 slot, con coda inutilizzata a zero. La raggiungibilità corrente governa i destinatari dei comandi e la necessità di trasmettere, non la composizione della strip; se tutti i destinatari configurati sono offline, non vengono inviati frame.

Android e il contratto firmware supportano PixelID **0–1023** per Matrix e Music. La preview Matrix mostra al massimo 20 posizioni e conta gli ID distinti; questo limite visivo non riduce PixelCount o i frame trasmessi. **Differenza PC identificata:** il controller Matrix del sorgente PC 0.20.45 applica ancora un limite UI a 255, sebbene il riferimento PC v8.6 descriva 1023. Il limite Android e firmware qui documentato è 1023; non si dichiara che quel limite UI PC sia stato corretto in questa revisione Android.

## 5. Comandi, potenza e Legacy

ON/OFF copia la configurazione Discovery del singolo destinatario e cambia solo Command. Restano conservati colore, hue/Hue, luminosità, speed, famiglia, ruolo, gruppo, universo, pixel, palette e parametri disponibili. Il firmware accetta sia `hue` sia l'alias Discovery `Hue`. Uno stato locale in attesa di invio non sostituisce il Discovery ricevuto.

La selezione Matrix preserva il Command di ciascun partecipante: un gruppo può quindi contenere contemporaneamente fari ON e OFF. L'ingresso in Music o il cambio del suo gruppo invia invece esplicitamente **Command=1**, con GLights=100. Il payload di selezione Music non aggiunge GState: il ruolo precedente può restare salvato, ma non controlla il runtime Music. Questa regola sostituisce la nota v1.7 secondo cui anche l'ingresso Music preservava lo stato OFF.

Le successive azioni ON/OFF durante Music cambiano la potenza dei fari senza arrestare necessariamente la cattura dell'App. OFF nel firmware mantiene l'uscita nera; ON riattiva la configurazione Music e attende frame validi.

Per Legacy distribuito, i comandi contengono GPort e GUniverse oltre ai campi dell'effetto. Il Master calcola il colore comune e lo distribuisce tramite sACN; gli Slave ricevono quel risultato. Legacy non crea una virtual strip. Breath e Heartbeat mantengono Speed e i campi colore quando cambia il solo parametro temporale.

FLASH/identificazione usa Command=2, è temporaneo e non diventa il nuovo stato di potenza. La selezione del campo PixelID di un dispositivo online con IP valido può inviare l'identificazione a quel singolo destinatario. Tap Sync resta disponibile negli effetti Matrix 101 e 105; Legacy Breath, Strobe e Heartbeat mantengono la regolazione Speed.

## 6. Matrix

Matrix richiede esattamente un Master tra i partecipanti della strip. Deve essere online, avere un IP corrente valido e PixelID 0. Gli altri partecipanti usano il ruolo Slave. I duplicati PixelID sono consentiti quando i ruoli restano coerenti.

La selezione esplicita dell'effetto o del gruppo invia la configurazione completa ai partecipanti dello snapshot attualmente raggiungibili. Il payload comprende Command preservato per destinatario, GLights, Speed, Brightness, GState, GPort, GUniverse, PixelID, PixelCount e i campi palette/parametri applicabili: ColorCount, Random, Custom1/2/3, OnOff1/2 e Colors.

Una modifica dei parametri dello stesso effetto può essere inviata al solo Master quando Discovery conferma l'effetto e il contesto di gruppo attesi per i partecipanti online. In assenza di conferma, l'App ripropone la configurazione completa ai partecipanti online. L'assenza temporanea di un membro dello snapshot non riduce PixelCount.

Il Master firmware genera la strip e distribuisce gli universi RGBW. Gli Slave applicano i quattro valori della propria posizione; l'App Android non ricrea l'effetto separatamente per ogni Slave. Il refresh Matrix continua a trasmettere l'ultimo frame anche se il colore non cambia. Gli effetti Matrix audio 110–112 usano il microfono del MobileD Master o del dispositivo locale X; non acquisiscono l'audio del telefono.

Le palette Matrix contengono colori RGBW e consentono il nero. I parametri salvati vengono ricostruiti senza trattare una palette nera come una palette vuota. Le differenze interne dei firmware restano nei rispettivi riferimenti: Arduino, per esempio, non usa lo stesso percorso NVS e lo stesso analizzatore FFT di ESP-IDF.

### Tap Sync Matrix

Tap Sync è disponibile per gli effetti 101 Color Change e 105 Theater. Il controllo segue lo stile PC: pulsante a larghezza piena, sfondo scuro #202020, bordo #555 e angoli da 5 dp; un LED da 8 dp, normalmente grigio con opacità 0,65, diventa ambra #ffae00 con bagliore per 120 ms a ogni tap. Il bersaglio touch Android è alto 48 dp. Il BPM è centrato sotto il pulsante, con testo da 10 sp. Un tap successivo riavvia la durata del flash; la chiusura del controllo cancella il timer rimasto.

Il primo tap registra il tempo e lampeggia il LED. Dal secondo, Android calcola la media degli ultimi quattro intervalli, conservando al massimo cinque tap. Una pausa maggiore di 2000 ms ricomincia la serie. Il clock è monotono e il BPM calcolato è limitato a 1–200, come nel controller PC.

Dato `beatMs = 60000 / BPM`, il valore Speed viene arrotondato all'intero più vicino e limitato a 0–100:

- effetto 101: `(900 − beatMs) / 7`;
- effetto 105: `(650 − beatMs) / 6,1`.

Per esempio, 120 BPM producono Speed 57 per 101 e Speed 25 per 105. Tap aggiorna il numero BPM, lo slider Speed visibile e il relativo valore, oltre alla configurazione inviata tramite il percorso Matrix esistente. Preserva quindi lo stato ON/OFF del destinatario secondo le regole Matrix.

Il LED lampeggia al tocco, non genera un metronomo automatico. Tap modifica Speed senza inviare un reset di fase firmware. Il BPM visualizzato descrive i tap: durata, pause e modalità dell'effetto possono impedire la corrispondenza esatta di un evento luminoso per battito. Questa revisione conserva le formule PC e non ridefinisce i tempi dei firmware. Quattro test JVM coprono mappatura a 120 BPM, finestra degli intervalli, reset oltre due secondi e limiti; gli esiti effettivi restano nel report della build corrente.

## 7. Music: routing e generazione

L'App genera i pixel Music dall'audio acquisito e li trasmette negli universi del gruppo. Il firmware riceve questi frame: non ricostruisce un secondo effetto Music locale. I sottotipi Android sono Rainbow, Random, Rainbow Chase, BPM, Spectrum e VU Meter.

All'avvio della sessione, prima della richiesta di cattura, Android invia il routing completo: Command=1, GLights=100, GPort, GUniverse, PixelID e PixelCount. Come nel builder Music della PC App, il payload non riscrive GState. Il cambio di gruppo richiede un nuovo routing. Cambiare il solo sottotipo o i parametri nella stessa sessione aggiorna la generazione dell'App senza ripetere il comando di selezione ai fari.

BPM e Spectrum usano i valori RGBW salvati per ogni colore. Nero, colori attenuati e colori con solo White restano validi. Un colore nero salvato è distinto da uno slot resettato/non salvato. Le transizioni interpolano i canali RGBW; non sostituiscono ogni colore con una hue a saturazione e intensità massime. La luminosità globale scala il risultato; il White per colore resta distinto dal controllo White globale dei sottotipi che lo usano.

BPM supporta cambi diretti, fade colore e fade-out/pausa/fade-in. Spectrum analizza il segnale audio e applica le bande ai colori attivi. La configurazione salvata viene caricata prima della generazione. La preview è un'indicazione dell'elaborazione dell'App e non una misura elettrica dell'uscita dei fari.

## 8. Music: cattura audio Android

L'App installabile ha minSdk 26, mentre la cattura Music richiede **Android 10/API 29 o successivo**. Usa AudioPlaybackCapture e MediaProjection con autorizzazione esplicita dell'utente e permesso RECORD_AUDIO. La presenza di RECORD_AUDIO non significa che questa schermata acquisisca il microfono del telefono: il percorso implementato cattura playback delle sorgenti ammesse, con usage media/game/unknown.

L'acquisizione è stereo PCM 16 bit a 48 kHz. Android può escludere l'audio di applicazioni che non consentono la cattura e applica i propri vincoli di profilo e contenuto. La condivisione autorizzata non garantisce che ogni App o contenuto produca campioni utilizzabili. Non è il loopback WASAPI della versione Windows e non usa il suo eseguibile helper.

Il foreground service MediaProjection deve essere pronto prima di avviare la cattura. La sessione mantiene una notifica Music; la gestione della projection e di AudioRecord appartiene al percorso Music. Il consenso non viene riutilizzato dopo la chiusura della sessione. Revoca della condivisione, arresto del servizio, rimozione del task o errore fatale del recorder terminano la sessione e rilasciano le risorse.

I callback dei permessi e del collegamento al servizio verificano ancora la richiesta attiva e la disponibilità API 29 prima di creare la sessione. La navigazione Indietro passa dal dispatcher dell'Activity, così il gesto di sistema e il pulsante della toolbar seguono lo stesso percorso del controller. Tornare da un dialogo o da un'Activity in pausa non autorizza la riapertura di una richiesta Music annullata.

Ogni acquisizione ha una generazione propria: un vecchio worker non deve leggere il recorder di una nuova sessione. La chiusura azzera i campioni e i meter per evitare che l'elaborazione prosegua su audio conservato dopo un errore. Il passaggio in pausa dell'Activity, da solo, non avvia una nuova cattura e non sostituisce il consenso dell'utente.

La continuità in background dipende anche dalle regole Android e del produttore del telefono. Questa documentazione non certifica un test di autonomia, cattura o continuità su un dispositivo specifico. La scelta di Color o di un effetto non Music interrompe la sessione Music secondo il controller dell'App.

## 9. DMX OUT e priorità dei firmware

Arduino 3.3.70 aggiorna il trasporto Legacy, Matrix e Music con il wrapper IPv4 nativo `EffectUDP.h`: gruppo e interfaccia multicast espliciti, ripristino/retry Legacy e rivalidazione dell'endpoint Matrix. Il formato wire rimane invariato: JSON su UDP 8889, frame sACN RGBW completi da 638 byte su UDP 5568 e stessa mappatura di universi/pixel. Questa indicazione identifica i sorgenti correnti e non anticipa l'esito della compilazione Arduino o una verifica di comunicazione fisica sui fari.

Nei tre firmware allineati, Matrix e Music portano al DMX OUT **tutti i 512 canali dell'universo dell'effetto**, con start code zero. Il PixelID sceglie soltanto i quattro valori dei LED locali; non taglia l'universo destinato ai fari RGBW esterni.

| Percorso firmware | Universo e contenuto sul DMX OUT |
|---|---|
| Matrix Master, ID 0 | Universo base completo GUniverse |
| Matrix Slave | Universo completo `GUniverse + floor(PixelID / 128)` |
| Music, GLights=100 | Universo completo `GUniverse + floor(PixelID / 128)` ricevuto dall'App |
| Legacy Master/Slave | Colore uniforme RGBW nei canali 1–4; nessuna virtual strip. Master completa a zero i restanti canali, Slave conserva il frame ricevuto |

Il comando Music non riscrive il ruolo GState precedente e non usa una distribuzione Master/Slave: non esiste un distinto generatore Music Master nei fari. Ogni ricevitore Music inoltra il proprio universo completo, indipendentemente dal ruolo rimasto nella configurazione.

Un faro standard può usare gli indirizzi RGBW 1, 5, 9 e così via per replicare posizioni della strip di quell'universo. Una singola linea DMX fisica porta un universo. Il canale d'ingresso Primary e la modalità Primary 4/5 canali non rimappano l'universo degli effetti. La limitazione di corrente dei LED locali non scala i valori Music/Matrix inoltrati.

Music OFF, cambio sessione e timeout di 3000 ms senza frame valido invalidano il frame e azzerano l'uscita completa. Primary DMX valido mantiene precedenza su LED, DMX OUT ed effetti subordinati. Solo il corretto universo d'ingresso Primary stabilisce tale priorità; un frame effetto sulla stessa porta UDP 5568 non basta.

Durante Primary DMX, il firmware continua ad accettare l'ultimo stato App valido senza sostituire l'uscita attiva. Alla scadenza dell'ingresso ripristina automaticamente quello stato, compreso OFF: circa tre secondi su ESP-IDF; circa 3,3 secondi o più, secondo scheduling, su Arduino. Music attende i nuovi frame necessari. ESP-IDF può riportare Command=0 in Discovery durante la priorità Primary; Arduino conserva il Command App riportato. Queste differenze non devono diventare un'ipotesi di conferma visiva sull'App.

Le verifiche automatiche dei firmware comprendono inoltro del canale 512, PixelID 0/127/128/1023, timeout, OFF e passaggi di sorgente. L'uscita elettrica, i cablaggi e i fari esterni richiedono comunque prove fisiche.

## 10. Aggiornamenti firmware e pacchetti

Questa revisione non aggiunge un aggiornamento firmware nativo all'App Android. Resta la scelta di progetto già documentata per le App mobile. Il percorso OTA e la programmazione seriale sono descritti dai progetti firmware e dall'App PC.

Il combined 4 MB misura 4.194.304 byte; il combined 8 MB misura 8.388.608 byte. Sono immagini complete di programmazione seriale. Il **combined 8 MB è in chiaro**: non deve essere confuso con il pacchetto di aggiornamento OTA 8 MB UE, che è cifrato con il flusso e le chiavi esistenti. Non caricare un combined come aggiornamento OTA. L'OTA applicativa 4 MB usa invece l'immagine prevista dal relativo firmware in chiaro.

APK, documentazione e report Android restano nel progetto Android; i binari firmware restano nelle cartelle dei rispettivi firmware. Nessuna chiave privata, credenziale di firma o password viene inclusa nella documentazione o nei report pubblicabili.

## 11. About: versione App e modelli MobileD

La schermata About è aggiornata per la versione 2.0.11. La dicitura della versione proviene da `BuildConfig.VERSION_NAME`, applicata al contenuto HTML al caricamento: non è un numero fisso da aggiornare separatamente dalla build. Il testo è in inglese, coerente con la schermata esistente, e mantiene crediti e contatti del prodotto.

Sono presenti due schede distinte per l'hardware:

| Caratteristica | MobileD | MobileD XL |
|---|---|---|
| Sorgente LED | 20 W RGBW OSRAM OSTAR, LE RTDUW S2W | 40 W RGBW OSRAM OSTAR, LE RTDUW S2WN |
| Batterie | 2 pacchi Li-ion 2S1P, ciascuno 7,2 V / 3,5 Ah | 4 pacchi dello stesso tipo e capacità |
| Autonomia minima dichiarata | 10 ore | 10 ore |
| Tempo massimo di ricarica | 4 ore | 8 ore |
| Dimensioni, larghezza × altezza × profondità | 100 × 46,1 × 76 mm | 106 × 54 × 100 mm |
| Peso | 0,5 kg | 1,0 kg |

Le caratteristiche comuni comprendono lente Fresnel 60° da 40 mm e lente in vetro 120° inclusa, ottiche opzionali, IP20, Wi-Fi 2,4 GHz IEEE 802.11 b/g/n, Art-Net/sACN E1.31 e DMX512 a quattro/cinque canali, DMX OUT XLR femmina a tre poli e ricarica 12 V DC / 1 A con connettore 5,5 × 2,1 mm. About descrive anche i controlli App, Legacy/Matrix, Tap Sync e Music con cattura playback autorizzata su Android 10 o successivo. Le impostazioni e gli aggiornamenti del firmware del faro tramite portale web non equivalgono a una nuova funzione OTA nativa nell'App Android.

La fonte dei dati hardware è il manuale fornito `MobileD - User Manual ENG 2026.pdf`: pagine PDF 5–6, stampate 4–5, per sorgenti, batterie, ottiche, dimensioni, pesi e carica; pagina PDF 42, stampata 41, per le tabelle DMX. Per le dimensioni viene usata la tabella dettagliata che indica esplicitamente gli assi W × H × D; il riepilogo finale riordina le quote e arrotonda 46,1 a 46 mm. Il numero di pacchi non viene trasformato in una capacità Ah complessiva, perché il manuale non documenta il loro collegamento elettrico. Autonomia e ricarica sono dati dichiarati del prodotto, non misure eseguite durante questa build.

## 12. Build, consegna e verifiche

La configurazione Android corrente usa Android Gradle Plugin 8.13.2, wrapper Gradle 8.13, Kotlin 2.0.0, compileSdk/targetSdk 34 e minSdk 26. L'applicationId resta `com.mobiled.android.test`. Per l'ambiente locale viene usato JDK 21; Android Studio è facoltativo per modificare e compilare da terminale.

Il README contiene i comandi. Gli APK di lavoro Gradle si trovano in `app/build/outputs/apk`; la cartella di consegna ordinata è `dist` del progetto Android. L'APK non viene installato automaticamente dal solo comando di build. Le credenziali di firma e il keystore mantengono il percorso locale previsto dal progetto.

Questa revisione non incorpora come nuove prove le installazioni e i test registrati nelle note v1.7. Il log della verifica corrente è `build/reports/alignment/final-build.log`; il report consegnato è `dist/build-verification.json`. Un report non ancora presente non rappresenta una build riuscita. I test JVM non sostituiscono i test strumentali che richiedono un telefono o un emulatore.

Le prove fisiche da eseguire dopo la build sono:

1. All Devices e gruppi con inventario misto online/offline: IP aggiornati, invii e ritorno online senza spostamento dei pixel.
2. Matrix con Master ID 0, duplicati, ID oltre 255 e passaggio tra universi 127/128; controllo di PixelCount e preview.
3. Selezione Matrix con potenza mista ON/OFF, modifica parametri e riapplicazione dello stesso effetto dopo un comando esterno.
4. Music: consenso iniziale, rifiuto, revoca, audio ammesso/non ammesso dalla sorgente, cambi di sottotipo e gruppo, stop/riavvio e comportamento in background.
5. Palette BPM/Spectrum con nero, White puro e colori attenuati; verifica del frame trasmesso e dell'effetto visibile.
6. DMX OUT con fari esterni su più indirizzi dello stesso universo, incluso canale 512, su Matrix Master/Slave e ricevitori Music.
7. Ingresso Primary DMX durante Music/Matrix, comandi App durante l'ingresso, ripristino automatico dell'ultimo stato ON o OFF al timeout.
8. Persistenza di gruppo, universo e Master dopo riapertura e import/export; corrispondenza Tap Sync/flash sui fari.

La correzione dei sorgenti, una build riuscita e un test automatico sono evidenze diverse. Il report di consegna deve specificare quali sono state effettivamente ottenute; nessuna prova elettrica o su telefono viene dedotta dalla sola lettura di questo documento.
