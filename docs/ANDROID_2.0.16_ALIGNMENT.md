# Allineamento Android 2.0.16 - 27 ottobre 2026

La release corrente e 2.0.16, versionCode 29, applicationId `com.mobiled.android`; il debug usa `.test`. compileSdk e targetSdk sono 36. La Console mostrata dal proprietario conferma la 2.0.16 nei test interni e la 2.0.7 in produzione. L'assistente non ha inviato una nuova release in revisione.

## Contenuti e comportamento

Sono allineati la configurazione della candidata Play, gli accorgimenti per le barre di sistema su Android 15/16, le informazioni About/Help/Terms e i documenti di consegna. Il codice di controllo luci e Music non viene modificato in questo allineamento. La selezione di un colore dalla ruota Color arresta Music e il relativo servizio; cambiare soltanto scheda o portare l'app in background lo mantiene attivo. Non viene aggiunto un messaggio di arresto nella versione gia caricata.

## Verifiche eseguite

- Rieseguiti i 39 test JVM: 39 superati, zero errori/fallimenti.
- `lintRelease` completato con esito positivo: zero errori/fatal; gli avvisi esistenti restano nel report lint.
- Versione, applicationId, SDK e firma dell'APK verificati.
- I tre asset About, Help e Terms corrispondono byte per byte a sorgenti, bundle Play e APK derivato. I testi non espongono i dettagli interni riservati.
- About sostituisce `{{APP_VERSION}}` con `BuildConfig.VERSION_NAME` tramite `AboutRepository`.
- Il bundle Play originale conserva il suo SHA-256. L'APK corrente viene ricavato da quel bundle, senza ricompilare o sostituire la candidata gia caricata.
- Nessuna nuova prova hardware sul telefono o sulle luci e stata eseguita dall'assistente. Il proprietario conferma i test dell'app; l'installazione ADB del pacchetto locale non ha sostituito la copia Play per incompatibilita della firma.

## Pacchetti locali

- `dist/MobileD_Android_2.0.16.apk`: APK locale della 2.0.16.
- `dist/google-play-2.0.16/MobileD-2.0.16-29-play.aab`: bundle originale Play.
- `dist/archive/2.0.15`: consegna precedente, separata dalla release corrente.
- `dist/build-verification.json`: metadati, hash e risultati correnti.

Una copia firmata da Google Play deve essere aggiornata dal canale di test Play; l'APK locale e firmato con la chiave di caricamento e puo avere un certificato differente. Nessuna disinstallazione o cancellazione dati e stata eseguita.

## Configurazione di firma

`gradle.properties` e le chiavi restano sul computer. Il ramo corrente rimuove il file di proprieta contenente credenziali e aggiunge `gradle.properties.example`, senza valori privati. Il file era gia presente nella cronologia pubblica: questa operazione non riscrive i vecchi commit. Nessun file di chiave privata e incluso nella nuova consegna o nel nuovo commit.

Dettagli verificabili: `ANDROID_2.0.16_ALIGNMENT.json`.
