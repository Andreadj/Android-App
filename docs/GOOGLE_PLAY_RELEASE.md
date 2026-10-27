# Google Play: MobileD 2.0.16

Preparazione locale del 23 ottobre 2026, stato aggiornato il 27 ottobre 2026: la Console mostrata dal proprietario conferma la 2.0.16 nei test interni e la 2.0.7 in produzione. Il proprietario conferma di aver testato le app. La dichiarazione del servizio Music e il video sono in preparazione; nessun invio in produzione e stato eseguito dall'assistente.

## Identita della release

- Scheda esistente: https://play.google.com/store/apps/details?id=com.mobiled.android
- Application ID release: `com.mobiled.android`; debug: `com.mobiled.android.test`.
- Versione candidata: 2.0.16, versionCode 29. La produzione mostrata dal proprietario e 20 (2.0.7); il canale di test interno contiene gia 29 (2.0.16).
- compileSdk e targetSdk 36; minSdk 26.
- Firma tramite la configurazione locale esistente: confrontare SHA-256 del certificato di caricamento con Play Console. Non includere chiavi o password nella consegna.

## Note di rilascio da incollare

<it-IT>
Migliorata la stabilita quando si apre una luce.
Gli effetti Legacy funzionano anche sui gruppi senza Master.
Dieci colori salvati, disposti su due righe da cinque.
Aggiornate le informazioni di aiuto e la compatibilita Android.
</it-IT>
<en-US>
Improved stability when opening a light.
Legacy effects also work with groups without a Master.
Ten saved colors arranged in two rows of five.
Updated help information and Android compatibility.
</en-US>

## Descrizione breve proposta

Controlla colori, gruppi ed effetti delle tue luci MobileD e MobileD XL.

## Descrizione completa proposta

MobileD ti permette di controllare le luci RGBW portatili MobileD e MobileD XL dal tuo dispositivo Android.

Scopri le luci, organizzale in gruppi e regola colori e luminosita. Salva fino a dieci colori per richiamarli con un tocco.

Crea atmosfere con gli effetti Legacy e Matrix. Usa Tap Sync per regolare il ritmo degli effetti compatibili oppure Music per creare effetti dall'audio riprodotto sul dispositivo.

Music richiede Android 10 o successivo, la tua autorizzazione e una sorgente audio che consenta la cattura. L'app richiede luci MobileD compatibili e una connessione Wi-Fi alle luci.

Manuali e assistenza: https://www.mobiledonline.eu/

## Controlli nella Console prima della distribuzione

1. Confermare versionCode 29 disponibile e certificato della chiave di caricamento corrispondente.
2. Caricare il bundle in Test interno; esaminare gli avvisi della Console e il report pre-lancio.
3. Provare aggiornamento dalla versione Play con conservazione dei dati, apertura luci, Color, gruppi Legacy/Matrix e autorizzazione/arresto Music. Verificare margini e navigazione su Android 15/16; una build riuscita non sostituisce queste prove.
4. Rivedere Sicurezza dei dati e informativa privacy rispetto al comportamento reale. Il file interno `privacy.html` contiene Terms and Conditions: non e una privacy policy. La scheda pubblica collega una policy esterna: verificarne contenuti e accessibilita.
5. Se richiesto, completare la dichiarazione del servizio in primo piano Music (mediaProjection), con spiegazione e video della funzione avviata dall'utente. Non dichiarare registrazione del microfono se la funzione usa soltanto audio di riproduzione.
6. Aggiornare descrizione e note; controllare gli screenshot rispetto all'interfaccia corrente.
7. Passare a Produzione e inviare in revisione solo dopo la verifica del candidato.

About usa BuildConfig.VERSION_NAME. About, Help e Terms sono ricontrollati: questa revisione non cambia le funzioni descritte e non introduce dettagli tecnici nei testi rivolti al cliente.

La nuova identita di produzione non importa i dati della copia locale `.test`; la conservazione dei dati va verificata aggiornando il pacchetto Play `com.mobiled.android` con firma compatibile.

## Verifiche locali della candidata

Build bundle firmato, 39 test JVM e lint release completati con successo. Nessun errore/fatal lint. Bundle validato con bundletool Google 1.18.3; firma JAR verificata; metadati e tre asset informativi controllati nel bundle. Nessuna libreria nativa presente. Prove su Android 15/16 e aggiornamento dalla release Play ancora da eseguire.

Certificato di caricamento locale SHA-256:

`D5:5B:B0:9F:07:30:53:E2:B5:4B:21:1F:C4:A3:1A:4A:95:6B:E5:75:F8:BC:82:41:64:18:C0:83:F5:8B:23:90`

Il confronto con Play Console resta da completare.
