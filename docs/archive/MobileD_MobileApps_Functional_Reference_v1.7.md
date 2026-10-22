# MobileD — Mobile Apps Functional Reference

**Revisione v1.7 — allineamento PC v8.4 e correzioni Android All Devices, Breath/Heartbeat e Tap Sync**  
Data: 19/10/2026

## Autorità e limiti

- Specifica di protocollo: *MobileD PC App Functional Reference FINAL v8.4*, PC App 0.20.44, checkpoint dichiarato `dce50802ebc8abb0299e7a98176cb8131caf3587`.
- Implementazione Android esaminata: repository `Andreadj/Android-App`, branch `master`, HEAD `83e1488`; i file Kotlin e Gradle modificati localmente non fanno parte di quel commit.
- Questa revisione sostituisce le formulazioni funzionali in conflitto degli appunti mobile v1.1–v1.6. Mantiene separate le regole della specifica PC e le evidenze statiche Android.
- L’ispezione del sorgente non certifica il comportamento su hardware. Non è stato eseguito in questa revisione un nuovo test fisico. La presenza di una regola in questo documento non equivale a una prova runtime.

> [!IMPORTANT]
> Discovery è la fonte autorevole dello stato reale del dispositivo. I comandi locali non devono diventare una sostituzione dello stato Discovery.

## 1. Protocollo

| Elemento | Regola |
|---|---|
| Discovery UDP | 8232 |
| Comandi/configurazione App → MobileD | Unicast UDP 8889 |
| Frame sACN / E1.31 | UDP 5568; universo nel frame e indirizzo multicast identificano il flusso |
| Art-Net | UDP 6454 |
| `Command` | 0 = OFF, 1 = ON, 2 = FLASH |
| `GLights` | 0 = Color; 1–99 = Legacy; 100 = Music; 101–199 = Matrix |
| `GState` | X = diretto/locale; M = Master; S = Slave |
| Identificatore di gruppo | `GPort` è un valore scalare incluso nei payload di comando/configurazione; non è una porta UDP di destinazione |
| GPort All Devices | `8890`, identificatore del gruppo di sistema |
| Universo All Devices | `GUniverse=32000`; base del blocco riservato |
| Affidabilità comando | Invio immediato; dopo 50 ms senza un comando più recente, 3 ritrasmissioni a intervalli di 15 ms |
| ON/OFF | Preservare payload configurato/ricevuto; cambiare solo `Command` |

Color resta sul proprio percorso e usa `GLights=0`. Music usa `GLights=100`; Matrix usa i codici 101–199. La correzione delle funzioni Matrix/Music non deve cambiare il protocollo Color o Legacy.

## 2. Identificatori di gruppo e universi

| Contesto | GPort (identificatore) | GUniverse (destinazione frame) |
|---|---|---|
| All Devices | `8890` | Base fissa 32000; blocco riservato 32000–32009 |
| Gruppo utente | Identificatore assegnato casualmente (policy allocator esistente: 10000–65535) | Base casuale tra 32010 e 35000 inclusi; prenota 10 universi da base a base+9 |

`GPort` identifica il gruppo nei payload di configurazione/comando; non seleziona né modifica il socket di trasmissione. Non interpretare il suo valore numerico come porta di destinazione e non introdurre un’esclusione speciale solo perché coincide con la porta standard sACN 5568. Restano valide le policy esistenti dell’allocator per la scelta degli identificatori.

`GUniverse` è invece la base del gruppo per i frame distribuiti. Per sACN/E1.31 Android invia a UDP 5568 e usa l’universo nel frame per scegliere il multicast standard `239.255.<universo-high>.<universo-low>`. Discovery resta su UDP 8232 e tutti i comandi/configurazioni App → MobileD restano unicast UDP 8889, indipendentemente da GPort e GUniverse.

Prima di assegnare un blocco di universi a un gruppo utente, controllare tutte le prenotazioni per evitare sovrapposizioni. Salvare la base assegnata e riutilizzarla dopo riavvio e import/export. Eliminare un gruppo libera il suo blocco; non rinumerare gli altri gruppi. Il valore 35000 è il limite della **base assegnabile**; il blocco riservato può arrivare a base+9.

Per Music e per i frame distribuiti, la mappatura RGBW è:

- universo: `GUniverse + floor(PixelID / 128)`;
- canale iniziale: `(PixelID mod 128) × 4 + 1`;
- 128 PixelID per universo, 512 canali DMX.

Android assegna e persiste la base nel modello/database del gruppo, ripara basi assenti o sovrapposte e forza All Devices a 32000. L’import/export conserva la base valida o assegna un blocco libero quando quella importata è assente/non valida. **Verifica statica del codice; persistenza dopo riavvio/import-export non verificata su dispositivo in questa revisione.**

## 3. Selezione dei gruppi e PixelID

Quando si apre un controller, Android cattura lo snapshot dei membri Discovery-online con IP valido. La selezione Matrix usa il prefisso operativo della virtual strip: ID distinti da 0 fino al primo ID mancante; ogni membro online con lo stesso PixelID resta incluso nella posizione corrispondente.

Esempio: gli ID `0,1,2,2,4,5,8,1,0` producono un prefisso operativo `0,1,2` e `PixelCount=3`. Gli ID duplicati sono consentiti e non aumentano PixelCount. Un dispositivo online aggiunto dopo la cattura non entra nello snapshot; i dispositivi offline non partecipano alla selezione corrente e non devono bloccare il gruppo solo perché restano nell’inventario.

Music usa uno snapshot separato di tutti i membri online del gruppo. Gli ID devono essere nel range Android 0–1023 e i valori distinti devono essere consecutivi a partire da 0; duplicati sono ammessi. `PixelCount` è il numero di PixelID distinti. Music non usa Master/Slave.

**Discrepanza da risolvere prima del rilascio:** il controller Android filtra la strip Matrix a PixelID 0–255; il PDF PC v8.4 non specifica un limite Matrix equivalente. Non estendere o documentare quel limite come requisito PC senza una decisione funzionale verificata.

## 4. Matrix

- Matrix richiede esattamente un Master; il Master deve avere PixelID 0. Gli altri membri operativi sono Slave.
- Android usa la strip consecutiva online; gli ID duplicati rappresentano più dispositivi sulla stessa posizione virtuale.
- Una selezione esplicita di effetto configura tutti i membri online interessati con il payload completo e l’indirizzamento del gruppo.
- Una modifica di parametri dello stesso effetto può essere inviata al solo Master quando Discovery conferma, per ogni membro online, effetto, ruolo, porta, universo, PixelID e PixelCount attesi. Se la conferma manca, Android riconfigura il gruppo online.
- Ogni payload Matrix conserva il `Command` letto dal Discovery del destinatario. La selezione dell’effetto non forza ON; OFF rimane OFF. L’azione ON/OFF è l’unica che cambia intenzionalmente il campo Command.
- Il payload comprende i campi previsti dall’effetto: GLights, Speed, Brightness, GState, GPort, GUniverse, PixelID, PixelCount, ColorCount, Random, Custom1/2/3, OnOff1/2 e Colors secondo applicabilità.
- Per i comandi Legacy di gruppo con ruoli Master/Slave, Android include GPort come identificatore del gruppo e GUniverse come base di indirizzamento; i comandi continuano a viaggiare su UDP 8889.
- Il rendering distribuito è compito del firmware; Android configura il gruppo e non genera una copia dell’effetto per ciascuno Slave.
- I controlli Tap Sync Matrix sono presenti per gli effetti 101 e 105; la corrispondenza temporale resta da verificare su hardware.
- Tap Sync è disponibile soltanto in Matrix (101 e 105). Rimosso dagli effetti Legacy Breath, Strobe e Heartbeat; questi mantengono la regolazione manuale Speed.

## 5. Music

- Music è un effetto guidato dall’App, usa `GLights=100` e non usa Master/Slave.
- La selezione iniziale della sessione invia ai destinatari il payload di routing completo: `Command`, `GLights`, `GPort`, `GUniverse`, `PixelID`, `PixelCount`.
- `GPort` è 8890 per All Devices o la porta assegnata al gruppo; `GUniverse` è la base persistita del gruppo.
- Android conserva per ogni destinatario il `Command` presente nel suo Discovery. Non deduce ON/OFF dall’effetto selezionato. La discrepanza tra questa regola per-destinazione e una formulazione storica che calcola un singolo Command per il gruppo è risolta qui in favore dello stato Discovery per-device.
- Il cambio di sottotipo Music nella stessa sessione aggiorna l’elaborazione dell’App e non invia un nuovo comando UDP di routing. Il cambio di gruppo/sessione richiede una nuova selezione e un nuovo routing.
- Il frame realtime è separato dal comando di selezione e viene trasmesso via sACN multicast, con limite di circa 30 fps (un frame ogni ~33 ms).
- Ogni frame Music sACN usa sempre UDP 5568; GPort non è passato come porta di destinazione. GUniverse determina l’indirizzo multicast e resta nel payload di routing/configurazione unicast inviato su UDP 8889.
- La priorità sACN (Priority) è un campo del frame associato al singolo universo, non un’impostazione globale del gruppo e non un sostituto di GPort/GUniverse. I dispositivi confrontano la priorità dei flussi che ricevono per ciascun universo; gli universi del blocco sono flussi distinti. Android imposta Priority a 100 nei frame Music.
- La cattura Android usa la cattura di playback disponibile sulla piattaforma Android; non è la cattura system-loopback desktop della PC App. Permessi, foreground service e disponibilità della sorgente audio devono essere verificati sul dispositivo.
- Android implementa Rainbow, Random, Rainbow Chase, BPM, Spectrum e VU Meter. Le differenze di piattaforma audio non autorizzano gain o normalizzazioni non presenti nel contratto.

## 6. All Devices e Master

All Devices è il gruppo di sistema: contiene tutti i MobileD rilevati, non si rinomina e non consente di aggiungere o rimuovere membri dall’editor. In Edit Group sono modificabili i PixelID; il Master si assegna dalla schermata Master/Slave. Selezionare/toccare il campo PixelID di un dispositivo online con IP valido invia un singolo {"Command":2} a UDP 8889 per identificarlo; non cambia lo stato Discovery.

Nella schermata Master/Slave Android mostra i membri online al momento della selezione; il Master scelto viene impostato a PixelID 0 e gli altri membri online diventano Slave. Il modello e il percorso di import/export conservano l’indicazione Master. La persistenza runtime del Master All Devices dopo riapertura e import/export richiede ancora una prova funzionale su dispositivo.

## 7. Verifica Android registrata

### Evidenze statiche

- Allocatore e persistenza GUniverse: `GroupTableDAO.kt`, `AppSettingsManager.kt`, modello `HardwareGroup.kt`.
- Snapshot, validazione Matrix/Music e invio comandi: `ControllerActivity.kt`, `MatrixFragment.kt`, `MusicFragment.kt`.
- Matrix preserva il Command Discovery per destinatario e non promuove automaticamente OFF a ON.
- Music invia il routing completo alla selezione iniziale e conserva il Command Discovery di ciascun destinatario.
- Breath e Heartbeat includono Speed e mantengono invariati i campi colore correnti quando cambia solo Speed.
- All Devices blocca modifica nome/appartenenza; la selezione PixelID invia Command=2 solo al dispositivo online con IP valido.
- Import/export rappresenta il Master salvato; la persistenza runtime resta da provare su hardware.

### Build e limiti

L’ultima verifica automatica registrata ha avuto esito positivo per `:app:testDebugUnitTest` e `:app:assembleDebug`. In questa correzione `:app:assembleDebug` è riuscito e la versione è stata installata/avviata sul dispositivo Xiaomi M2101K9AG. Non è stato ancora eseguito un test funzionale dei flash/effetti sui MobileD.

Configurazione locale esaminata: Android Gradle Plugin 8.13.2, Gradle wrapper 8.13, compileSdk/targetSdk 34, minSdk 26, versione app 2.0.8. I file Gradle hanno modifiche locali: non sono incluse in `83e1488`.

### Prove fisiche ancora necessarie

1. All Devices con solo una parte dei dispositivi online: invio e avvio Matrix/Music.
2. Gruppo Matrix M/S: selezione effetto, parametri, cambio gruppo, Discovery OFF/ON e indirizzamento dei duplicati PixelID.
3. Music: routing completo, cambio sottotipo senza nuovo comando UDP, sACN e permesso/cattura audio.
4. Master All Devices: salvataggio e persistenza dopo chiusura/riapertura e import/export.
5. Tap Sync/flash: conteggio tap confrontato con i flash firmware.
6. Effetti e stati dispositivi online/offline, inclusi Discovery incompleti/non validi.

## 8. Stato dei file e consegna

Questa revisione v1.7 è il sorgente Markdown modificabile della specifica mobile. Il PDF v1.7 è stato rigenerato da questo documento e accompagna la revisione nel repository. Il PDF v1.4 risulta cancellato nel working tree.

Nessun commit, push o modifica alla PC App o al firmware è stato eseguito.
