# Analyse der hochgeladenen APK («Aves.apk»)

## Identität

| Merkmal | Wert |
| --- | --- |
| Paketname | `deckers.thibault.aves` |
| Version | 1.15.8 (versionCode 182) |
| App | **Aves Gallery** von Thibault Deckers, Open Source (BSD-3-Clause), https://github.com/deckerst/aves |
| Flavor | *play* (Firebase Crashlytics, Google Maps / Play Services) |
| Min / Target SDK | 32 / 36 |
| Build | Android Gradle Plugin 9.4.1, Kotlin, R8-minifiziert |
| Grösse | 6,4 MB, 357 Dateien |

Wichtig: Die Datei ist nur der **Basis-Split eines App-Bundles**. Es fehlt das `lib/`-Verzeichnis mit
`libflutter.so` (Flutter-Engine) und `libapp.so` (kompilierter Dart-Code). Der eigentliche App-Code
(die ganze Oberfläche und Logik) steckt in genau diesen fehlenden Bibliotheken. Die APK ist deshalb
allein weder installierbar noch lauffähig; vollständig wäre sie rund 30 MB gross.

## Architektur

Aves ist eine **Flutter-App** mit einem Kotlin-«Host». Die Arbeitsteilung:

```
┌────────────────────────── Dart / Flutter (libapp.so – fehlt) ──────────────────────────┐
│ Oberfläche, Navigation, Sammlung/Alben/Tags/Länder, Suche, Filter, Slideshow, Settings  │
└───────────────┬───────────────────────────────────────────────────────┬────────────────┘
                │ MethodChannels / EventStreams                         │
┌───────────────▼───────────────────── Kotlin-Host (classes.dex) ───────▼────────────────┐
│ deckers.thibault/aves/…  app · device · storage · analysis · geocoding · intent ·       │
│                          security · wallpaper · window · embedded · debug              │
│ MainActivity (Flutter-Einbettung, Intents)   AnalysisWorker (WorkManager, Katalog)     │
│ MediaPlaybackService (Vordergrund-Dienst)    ScreenSaverService (Daydream)             │
│ WallpaperActivity (ATTACH_DATA)              HomeWidgetProvider (+ Settings-Activity)  │
│ SearchSuggestionsProvider                    AvesAppGlideModule (Thumbnails via Glide) │
│ Bibliotheken: metadata-extractor, Adobe XMP, ExifInterfaceFork, mp4parser,             │
│               TiffBitmapFactory, Glide, Room, WorkManager, DataStore, Biometric        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Was im Kotlin-Teil steckt

- **Kanäle** (`deckers.thibault/aves/<name>`): Die Dart-Seite ruft über diese Kanäle Android-Funktionen
  auf. Erkennbar sind Kanäle für App-Infos, Geräteeigenschaften, Speicherzugriff (SAF, Kopieren/Löschen
  mit `ActivityResultStreamHandler`), Metadaten-Analyse, Geocoding, Intents, Sicherheit
  (Biometrie/Krypto), Hintergrundbild, Fenster und eingebettete Daten (Motion Photos).
- **AnalysisWorker**: Ein WorkManager-Job, der die Mediathek katalogisiert (EXIF/XMP lesen, Adressen
  auflösen) und Ergebnisse in einer Room-Datenbank ablegt.
- **Metadaten**: `metadata-extractor` + Adobe XMP-Core lesen EXIF, IPTC, XMP; `ExifInterfaceFork` ist
  ein Fork der AndroidX-Klasse für das Schreiben von Orientierung/Datum; `mp4parser` bearbeitet
  Video-Container (z. B. Rotation).
- **Dienste**: Vordergrund-Dienst für Medienwiedergabe (Notification mit MediaSession), Bildschirmschoner
  (`screen_saver.xml`), Home-Screen-Widget (`app_widget_info.xml`), Suchvorschläge für die
  Android-Systemsuche (`searchable.xml`).
- **Intent-Filter**: Launcher, `APP_GALLERY`, `VIEW/EDIT/SEND/PICK/GET_CONTENT` für Bilder und Videos,
  `PICK_IMAGES`, `REVIEW`, `SET_WALLPAPER`, `ATTACH_DATA`, `PROCESS_TEXT`, `SEARCH`,
  `APPLICATION_PREFERENCES`, Picture-in-Picture, Leanback (Android TV) und Freeform-Fenster.

### Was in den Flutter-Assets steckt

- Kartenkacheln als Vorschaubilder (Google, OSM, OpenTopoMap, Stamen), `countries-50m.json` für die
  Länder-Zuordnung, Material-Symbols-Schriften, `media_kit` (hls.js) und `wakelock_plus`
  (Web-Reste), `flex_color_picker`, Nutzungsbedingungen (`terms.md`).
- `NativeAssetsManifest.json` verweist auf `libicu4x.so` (Unicode-Bibliothek), ebenfalls im fehlenden
  Teil.

### Berechtigungen

Lesen von Bildern/Videos (inkl. Teilzugriff ab Android 14), `ACCESS_MEDIA_LOCATION`, `MANAGE_MEDIA`,
Vordergrund-Dienste (Medien, Datensync), Benachrichtigungen, Biometrie, Wake-Lock, Boot-Receiver,
Shortcuts, Hintergrundbild, Internet/Netzwerkstatus (Karten, Geocoding, Crashlytics).

## Bewertung

| Stärke | Schwäche |
| --- | --- |
| Sehr umfangreiche Funktionen (Tags, Länder, Karten, XMP, Motion Photos, TV, Widgets) | Flutter-Engine + Dart-Code machen die App gross (≈ 30 MB) und der Start ist spürbar langsamer als nativ |
| Reife Metadaten-Pipeline | Doppelte Welten: jede Funktion braucht Dart- und Kotlin-Code plus Kanal |
| Quelloffen, aktiv gepflegt | Play-Flavor mit Firebase/Play Services (Telemetrie, Google-Abhängigkeit) |
| | Mindest-Android 12L, kein Material-3-Look mit dynamischen Farben |

## Die neue App: Nova Galerie

Statt Flutter + Host wird die Galerie **vollständig nativ** umgesetzt (Kotlin, Jetpack Compose,
Material 3). Das streicht die zweite Laufzeitumgebung, die Kanäle und die Google-Dienste; die APK
bleibt bei wenigen MB, startet schneller und sieht auf Android 12+ aus wie eine System-App.

| Aves | Nova Galerie |
| --- | --- |
| Flutter-UI + Kotlin-Host über MethodChannels | Eine Codebasis in Kotlin / Compose |
| Glide (Thumbnails) + eigene Decoder | Coil 3 mit Video-, GIF- und SVG-Decodern |
| media_kit / eigener Playback-Service | Media3 ExoPlayer |
| metadata-extractor, XMP, Fork von ExifInterface | AndroidX ExifInterface + MediaMetadataRetriever |
| Room-Katalog, WorkManager-Analyse | Direkt aus dem MediaStore, live per ContentObserver |
| Firebase Crashlytics, Play Services, Internet | Keine Netzwerkberechtigung |
| minSdk 32 | minSdk 26 |

Bewusst (noch) nicht übernommen: Tags/XMP-Schreiben, Länder- und Kartenansicht, Motion Photos,
Bildschirmschoner, Widgets, TV-Oberfläche. Diese Funktionen lassen sich bei Bedarf gezielt ergänzen.

Quellcode: [`android/`](../android/), Build/Release: [`.github/workflows/android-apk.yml`](../.github/workflows/android-apk.yml).
