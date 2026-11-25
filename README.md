# Sensor Charts

Android-App, die Sensordaten (Temperaturen, Leistungen usw.) aus einer PostgreSQL-Datenbank
als Liniendiagramme anzeigt. Die Daten werden über eine [PostgREST](https://postgrest.org)-API gelesen.

- Beliebig viele Charts, in der App konfigurierbar (Titel, Datenreihen, Farbe je Reihe)
- Mehrere Datenreihen pro Chart, ausgewählt über `source` (UUID) und `name`
- Paging **tageweise, wochenweise, monatsweise, jahresweise**, Sprung zu beliebigem Datum
- Pinch-Zoom und Scrollen im Chart, Tippen zeigt die Werte an einem Punkt
- API-Key wird bei der Ersteinrichtung abgefragt und mit dem Android Keystore verschlüsselt gespeichert

## Technologie

| Bereich | Wahl |
|---|---|
| Sprache / UI | Kotlin, Jetpack Compose, Material 3 |
| Charts | [Vico](https://github.com/patrykandpatrick/vico) 3.3 |
| HTTP / JSON | OkHttp 5, kotlinx.serialization |
| Speicherung | DataStore (Konfiguration), Android Keystore (API-Key) |
| Build | Android Gradle Plugin 9.5, Gradle 9.6, compileSdk 37, targetSdk 36 (Android 16), minSdk 26 |

## Datenbank

Grundlage ist die Tabelle `event` (`"timestamp"`, `source uuid`, `payload jsonb` mit
`name` und `value`). Einmalig ausführen:

```bash
psql -v api_role=<PostgREST-Rolle> -f db/01_sensor_charts.sql
psql -c "SELECT refresh_event_aggregates('-infinity')"   # initialer Aufbau
```

Das Skript legt an:

| Objekt | Zweck | Verwendet für |
|---|---|---|
| `event_value` (View) | Rohwerte als Spalten `ts, source, name, value` | Tagesansicht (ca. 288 Punkte je Reihe) |
| `event_hourly` (Tabelle) | Stundenmittel, -minimum, -maximum, Anzahl | Wochen- und Monatsansicht |
| `event_daily` (Tabelle) | Tageswerte (Europe/Berlin), gewichtet aus den Stundenwerten | Jahresansicht |
| `event_series` (View) | Katalog aller Kombinationen aus source und name | Auswahllisten im Chart-Editor |
| `refresh_event_aggregates()` | Berechnet die letzten 2 Tage inkrementell neu | pg_cron alle 10 Minuten |
| Index `event_source_name_ts_idx` | Schnelles Filtern nach source, name und Zeit | Rohdaten und Aggregation |

Die Aggregate sind normale Tabellen statt Materialized Views. Dadurch muss ein Lauf nur die
letzten zwei Tage neu berechnen und nicht jedes Mal zwei Jahre Rohdaten. Die
`pg_cron`-Anweisung für die regelmäßige Aktualisierung steht am Ende des Skripts.

Die App sendet den API-Key als `Authorization: Bearer <key>`, so wie PostgREST ein JWT erwartet.

## Bauen

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest
```

Dafür wird ein Android SDK mit Plattform 37 benötigt. Android Studio installiert es automatisch.

## Aufbau

```
app/src/main/java/de/sensorcharts/
├── data/        PostgREST-Client, Einstellungen und Chart-Konfiguration, Verschlüsselung des Keys
├── domain/      Zeiträume (Tag/Woche/Monat/Jahr) und ihre Datenauflösung
└── ui/
    ├── setup/      Ersteinrichtung (URL + API-Key)
    ├── dashboard/  Chart-Übersicht, Paging, Vico-Chart
    └── editor/     Chart-Editor (Datenreihen, Farben)
db/01_sensor_charts.sql
```
