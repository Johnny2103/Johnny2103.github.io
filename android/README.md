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

- Zeitleiste aller Fotos und Videos, nach Tagen gruppiert, Rasterbreite einstellbar
- Alben (MediaStore-Ordner) mit Cover und Anzahl
- Favoriten (lokal gespeichert, DataStore)
- Suche nach Name, Album oder Typ mit Foto/Video-Filter
- Vollbild-Viewer: Wischen, Pinch-Zoom, Doppeltipp-Zoom, Video-Wiedergabe (Media3/ExoPlayer)
- Details: EXIF (Kamera, Objektiv, Belichtung, GPS mit Kartenlink), Auflösung, Grösse, Pfad
- Mehrfachauswahl: Teilen, Favorit, Löschen (über den System-Dialog, scoped storage)
- Als Hintergrund festlegen, Öffnen mit, Bearbeiten in anderer App
- Empfängt Bilder/Videos von anderen Apps (VIEW/SEND)
- Material 3 mit dynamischen Farben, Hell/Dunkel, Edge-to-Edge, Splash-Screen
- Keine Netzwerkberechtigung, keine Analytics

## Technik

| Bereich | Umsetzung |
| --- | --- |
| Sprache / UI | Kotlin 2.0, Jetpack Compose (BOM 2024.12), Material 3 |
| Medien | MediaStore (eine Query über die `files`-Tabelle), ContentObserver → Flow |
| Bilder | Coil 3 (Video-Frames, GIF, SVG) |
| Video | Media3 ExoPlayer + PlayerView |
| Metadaten | AndroidX ExifInterface, MediaMetadataRetriever |
| Persistenz | Preferences DataStore |
| Min / Target SDK | 26 / 35 |

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
