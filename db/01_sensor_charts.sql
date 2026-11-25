-- =============================================================================
-- Sensor Charts – Datenbank-Objekte für die App
--
-- Voraussetzungen / Annahmen:
--   * Tabelle  event("timestamp" timestamptz, source uuid, payload jsonb)
--   * payload ist ein Objekt der Form {"name": "...", "value": <Zahl>}
--   * PostgreSQL >= 12 (date_trunc mit Zeitzone)
--   * PostgREST exponiert das Schema, in dem dieses Skript ausgeführt wird
--     (Standard: public). Bei Bedarf vorher  SET search_path = <schema>;
--
-- Ausführen (Rolle anpassen, mit der PostgREST die Requests ausführt):
--   psql -v api_role=web_user -f db/01_sensor_charts.sql
--
-- Das Skript ist idempotent und kann gefahrlos mehrfach ausgeführt werden.
-- =============================================================================

\if :{?api_role}
\else
  \set api_role web_user
\endif

-- -----------------------------------------------------------------------------
-- 1. Index für Rohdaten-Abfragen (Tagesansicht) und für die Aggregation
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS event_source_name_ts_idx
    ON event (source, (payload ->> 'name'), "timestamp");

-- -----------------------------------------------------------------------------
-- 2. Rohwerte als flache View (wird von der App für die Tagesansicht genutzt)
--    GET /event_value?source=eq.<uuid>&name=eq.<name>&ts=gte.<von>&ts=lt.<bis>
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW event_value AS
SELECT e."timestamp"                              AS ts,
       e.source                                   AS source,
       e.payload ->> 'name'                       AS name,
       (e.payload ->> 'value')::double precision  AS value
FROM event e
WHERE jsonb_typeof(e.payload -> 'value') = 'number';

-- -----------------------------------------------------------------------------
-- 3. Stundenaggregat (Wochen- und Monatsansicht)
--    Als Tabelle statt Materialized View, damit inkrementell aktualisiert
--    werden kann (nur die letzten Stunden statt zwei Jahre Rohdaten).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS event_hourly (
    source       uuid             NOT NULL,
    name         text             NOT NULL,
    bucket       timestamptz      NOT NULL,
    avg_value    double precision NOT NULL,
    min_value    double precision NOT NULL,
    max_value    double precision NOT NULL,
    sample_count integer          NOT NULL,
    PRIMARY KEY (source, name, bucket)
);

-- -----------------------------------------------------------------------------
-- 4. Tagesaggregat (Jahresansicht), Tagesgrenzen in der Zeitzone day_tz
--    (siehe refresh_event_aggregates, Standard Europe/Berlin)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS event_daily (
    source       uuid             NOT NULL,
    name         text             NOT NULL,
    bucket       timestamptz      NOT NULL,
    avg_value    double precision NOT NULL,
    min_value    double precision NOT NULL,
    max_value    double precision NOT NULL,
    sample_count integer          NOT NULL,
    PRIMARY KEY (source, name, bucket)
);

-- -----------------------------------------------------------------------------
-- 5. Katalog der verfügbaren Datenreihen (Auswahl in der Chart-Konfiguration)
--    GET /event_series?order=source,name
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW event_series AS
SELECT source,
       name,
       min(bucket)       AS first_ts,
       max(bucket)       AS last_ts,
       sum(sample_count) AS sample_count
FROM event_daily
GROUP BY source, name;

-- -----------------------------------------------------------------------------
-- 6. Aktualisierung der Aggregate
--    refresh_event_aggregates()               -> letzte 2 Tage neu berechnen
--    refresh_event_aggregates('-infinity')    -> kompletter Neuaufbau
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION refresh_event_aggregates(
    since timestamptz DEFAULT now() - interval '2 days'
) RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    -- Zeitzone, in der Tage geschnitten werden; muss zur App passen.
    day_tz constant text := 'Europe/Berlin';
    hour_from timestamptz := date_trunc('hour', since);
    day_from  timestamptz := date_trunc('day', since, day_tz);
BEGIN
    -- Stunden-Buckets ab hour_from neu berechnen
    DELETE FROM event_hourly WHERE bucket >= hour_from;
    INSERT INTO event_hourly (source, name, bucket, avg_value, min_value, max_value, sample_count)
    SELECT source, name, date_trunc('hour', ts),
           avg(value), min(value), max(value), count(*)
    FROM event_value
    WHERE ts >= hour_from
    GROUP BY 1, 2, 3;

    -- Tages-Buckets ab day_from aus den Stunden-Buckets ableiten
    -- (gewichteter Mittelwert, damit er dem Mittel über die Rohwerte entspricht)
    DELETE FROM event_daily WHERE bucket >= day_from;
    INSERT INTO event_daily (source, name, bucket, avg_value, min_value, max_value, sample_count)
    SELECT source, name, date_trunc('day', bucket, day_tz),
           sum(avg_value * sample_count) / sum(sample_count),
           min(min_value), max(max_value), sum(sample_count)
    FROM event_hourly
    WHERE bucket >= day_from
    GROUP BY 1, 2, 3;
END;
$$;

-- -----------------------------------------------------------------------------
-- 7. Rechte für die PostgREST-Rolle (nur lesend)
-- -----------------------------------------------------------------------------
GRANT SELECT ON event_value, event_hourly, event_daily, event_series TO :"api_role";
REVOKE EXECUTE ON FUNCTION refresh_event_aggregates(timestamptz) FROM PUBLIC;

-- PostgREST das neue Schema neu einlesen lassen
NOTIFY pgrst, 'reload schema';

-- -----------------------------------------------------------------------------
-- 8. Initialer Aufbau + regelmäßige Aktualisierung
--
--   Einmalig (dauert bei zwei Jahren Daten ggf. einige Minuten):
--     SELECT refresh_event_aggregates('-infinity');
--
--   Danach alle 10 Minuten, z.B. mit pg_cron:
--     CREATE EXTENSION IF NOT EXISTS pg_cron;
--     SELECT cron.schedule('refresh-event-aggregates', '*/10 * * * *',
--                          $$SELECT refresh_event_aggregates()$$);
--
--   Ohne pg_cron alternativ per System-Cron:
--     */10 * * * *  psql -c "SELECT refresh_event_aggregates()"
--
--   Werden Daten nachträglich für weiter zurückliegende Zeiträume importiert,
--   refresh_event_aggregates('<ab Zeitpunkt>') einmal manuell aufrufen.
-- -----------------------------------------------------------------------------
