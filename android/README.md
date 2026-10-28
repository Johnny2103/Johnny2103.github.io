# Nova Galerie

Moderne, schlanke Galerie-App für Android (Kotlin · Jetpack Compose · Material 3), entstanden aus der
Analyse der Aves-APK (siehe [`docs/aves-analyse.md`](../docs/aves-analyse.md)).

## Download

Fertige APK: **https://github.com/Johnny2103/Johnny2103.github.io/releases/latest/download/NovaGalerie.apk**
(oder über die Seite https://johnny2103.github.io/galerie/).

Jeder Push nach `main`, der etwas unter `android/` ändert, baut die APK über GitHub Actions
(`.github/workflows/android-apk.yml`) und veröffentlicht sie als Release. Auf anderen Branches
wird die APK nur als Build-Artefakt angehängt; über *Actions → Android APK → Run workflow* lässt
sich ein Release auch manuell auslösen.

## Funktionen

- **Personen**: Gesichtserkennung komplett offline (ML Kit Face Detection + MobileFaceNet-Embeddings via
  TensorFlow Lite). Gesichter werden automatisch zu Personen gruppiert; Personen benennen, zusammenführen,
  ausblenden, Titelbild wählen, nach Namen suchen.
- **Orte**: Offline-Länderzuordnung aus den EXIF-/Video-GPS-Daten (Natural-Earth-Polygone), Kartenlink.
- **Zeitleiste** nach Tag, Monat oder Jahr gruppiert, Schnellscroll-Leiste mit Datumsblase,
  Spaltenzahl per Pinch-Geste.
- **Alben**, **Favoriten**, **Videos**, **Tags** (lokal), **Statistik** (Jahre, Alben, Länder, Personen).
- **Papierkorb** (Android 11+): Löschen verschiebt in den System-Papierkorb, Wiederherstellen möglich.
- **Viewer**: Wischen, Pinch-/Doppeltipp-Zoom, Video-Wiedergabe (Media3/ExoPlayer), Diashow,
  Details mit EXIF (Kamera, Objektiv, Belichtung, GPS), erkannte Personen, Land und Tags.
- Mehrfachauswahl: Teilen, Favorit, Tags, Papierkorb/Löschen; Hintergrundbild, Öffnen mit, Bearbeiten.
- Empfängt Bilder/Videos von anderen Apps (VIEW/SEND).
- **App-Sperre** mit Biometrie oder Gerätesperre.
- Material 3 mit dynamischen Farben, Hell/Dunkel, Edge-to-Edge, Splash-Screen. Deutsch und Englisch.
- Keine Netzwerkberechtigung, keine Analytics – die Erkennung läuft ausschliesslich auf dem Gerät.

## Datenschutz

- **Keine Netzwerkberechtigung.** Das Manifest entfernt `INTERNET`, `ACCESS_NETWORK_STATE` und
  `ACCESS_WIFI_STATE` ausdrücklich (`tools:node="remove"`), und der Build bricht ab, wenn die fertige
  APK eine dieser Berechtigungen enthält. Android verweigert einer App ohne `INTERNET` jede Verbindung.
- **Alles lokal.** Gesichtserkennung (ML Kit, gebündeltes Modell) und Embeddings (TensorFlow Lite) laufen
  auf dem Gerät. Gesichter, Personen, Orte und Tags liegen in der privaten App-Datenbank
  (`nova.db`) und im privaten Dateiordner (`files/faces/`); andere Apps kommen nicht heran.
  «Index neu aufbauen» oder Deinstallieren löscht alles.
- **Keine Cloud-Sicherung.** `allowBackup="false"`: Android sichert keine App-Daten bei Google.
- **Keine Analyse, kein Crash-Reporting, keine Konten.**
- **Daten verlassen das Gerät nur auf deinen Befehl:** Teilen, Öffnen mit, Bearbeiten, Hintergrundbild
  und «Auf Karte zeigen» übergeben die gewählte Datei bzw. Koordinate an die App, die du dafür auswählst.
- Berechtigungen: Fotos/Videos lesen (ab Android 13 getrennt, ab 14 auch Teilzugriff), Standort-EXIF
  lesen (`ACCESS_MEDIA_LOCATION`), Hintergrundbild setzen, Biometrie für die App-Sperre.

## Technik

| Bereich | Umsetzung |
| --- | --- |
| Sprache / UI | Kotlin 2.0, Jetpack Compose (BOM 2024.12), Material 3 |
| Medien | MediaStore (eine Query über die `files`-Tabelle), ContentObserver → Flow, Papierkorb-API |
| Bilder | Coil 3 (Video-Frames, GIF, SVG) |
| Video | Media3 ExoPlayer + PlayerView |
| Gesichter | ML Kit Face Detection (gebündelt, offline) + MobileFaceNet TFLite (112×112 → 192-dim), Online-Clustering per Kosinus-Ähnlichkeit |
| Orte | Natural Earth 1:50m TopoJSON (`assets/geo`), Point-in-Polygon |
| Metadaten | AndroidX ExifInterface, MediaMetadataRetriever |
| Persistenz | Room (Analyse, Gesichter, Personen, Tags), Preferences DataStore (Einstellungen, Favoriten) |
| Sicherheit | AndroidX Biometric |
| Min / Target SDK | 26 / 35 |

Das Modell `app/src/main/assets/models/mobilefacenet.tflite` stammt aus dem BSD-3-lizenzierten Projekt
*FaceRecognitionAuth* (Marcos Carlomagno); Lizenztext liegt daneben.

## Lokal bauen

```bash
cd android
./gradlew assembleDebug        # Debug-APK: app/build/outputs/apk/debug/
./gradlew assembleRelease      # Release (signiert mit KEYSTORE_* Umgebungsvariablen, sonst Debug-Key)
```

## Signatur

Ohne Secrets erzeugt der Workflow bei jedem Build einen neuen Schlüssel; Android verweigert dann
ein Update über eine bereits installierte Version (erst deinstallieren). Für stabile Updates einmal
einen Schlüssel erzeugen und als Repository-Secrets hinterlegen:

```bash
keytool -genkeypair -v -keystore nova.jks -alias nova -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 nova.jks   # → Secret ANDROID_KEYSTORE_BASE64
```

Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
