-- Suspend anonymous v1 access; preserve rows, views, SELECT policies and non-FORCE RLS.
-- Execute as the same existing schema/table owner. Reinstall viewer.sql to restore access after review.
BEGIN;
SET LOCAL search_path = pg_catalog, pg_temp;
SET LOCAL lock_timeout = '10s';
DO $$ DECLARE r record; BEGIN
 FOR r IN SELECT c.* FROM pg_class c WHERE c.oid=ANY(ARRAY[
  'houselens."Properties"'::regclass,'houselens."Listings"'::regclass,'houselens."PriceHistoryEntries"'::regclass,
  'houselens."DistrictConfigs"'::regclass,'houselens."CrawlRuns"'::regclass,'houselens."SourceRunResults"'::regclass,
  'houselens.viewer_properties'::regclass,'houselens.viewer_top_properties'::regclass,
  'houselens.viewer_big_drops'::regclass,'houselens.viewer_district_stats'::regclass]) LOOP
  IF r.relowner<>(SELECT oid FROM pg_roles WHERE rolname=current_user)
   OR (r.relname LIKE 'viewer_%' AND (r.relkind<>'v' OR obj_description(r.oid,'pg_class') IS DISTINCT FROM 'houselens-viewer:anonymous-v1')) THEN
   RAISE EXCEPTION 'Unexpected revoke target: %',r.relname;
  END IF;
 END LOOP;
 IF (SELECT nspowner FROM pg_namespace WHERE nspname='houselens')<>(SELECT oid FROM pg_roles WHERE rolname=current_user) THEN
  RAISE EXCEPTION 'Revoke requires the existing schema owner';
 END IF;
END $$;
REVOKE SELECT ("Id","City","District","Address","AreaPing","Floor","AgeYears","HasParking","CurrentTotalPrice","CurrentUnitPrice","Status","Score","IsNew","FirstSeenAt","LastSeenAt") ON houselens."Properties" FROM anon;
REVOKE SELECT ("Id","PropertyId","SourceSite","Title","Url","ImageUrl","PostedDate") ON houselens."Listings" FROM anon;
REVOKE SELECT ("Id","PropertyId","CapturedAt","TotalPrice","UnitPrice","SourceSite","ChangeFlag","ChangePercent","IsBigDrop") ON houselens."PriceHistoryEntries" FROM anon;
REVOKE SELECT ("City","District","IsEnabled") ON houselens."DistrictConfigs" FROM anon;
REVOKE SELECT ("Id","StartedAt","FinishedAt","Status","NewCount","DelistedCount","BigDropCount") ON houselens."CrawlRuns" FROM anon;
REVOKE SELECT ("CrawlRunId","SourceSite","Success","FetchedCount") ON houselens."SourceRunResults" FROM anon;
REVOKE SELECT ON houselens.viewer_properties,houselens.viewer_top_properties,houselens.viewer_big_drops,houselens.viewer_district_stats FROM anon;
REVOKE USAGE ON SCHEMA houselens FROM anon;
-- Refuse a false success if an inherited/PUBLIC or unexpected grant still permits access.
DO $$ DECLARE r record; BEGIN
 IF has_schema_privilege('anon','houselens','USAGE,CREATE') THEN RAISE EXCEPTION 'Anonymous schema access remains after revoke'; END IF;
 FOR r IN SELECT * FROM pg_class WHERE relnamespace='houselens'::regnamespace LOOP
  IF r.relkind IN ('r','v','m','p','f') AND
   (has_table_privilege('anon',r.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
    OR has_any_column_privilege('anon',r.oid,'SELECT,INSERT,UPDATE,REFERENCES')) THEN
   RAISE EXCEPTION 'Anonymous relation access remains after revoke: %',r.relname;
  END IF;
  IF r.relkind='S' AND has_sequence_privilege('anon',r.oid,'USAGE,SELECT,UPDATE') THEN
   RAISE EXCEPTION 'Anonymous sequence access remains after revoke: %',r.relname;
  END IF;
 END LOOP;
END $$;
COMMIT;
