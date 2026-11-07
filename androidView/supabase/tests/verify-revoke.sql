\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE anon;
DO $$ DECLARE q text; BEGIN
 FOREACH q IN ARRAY ARRAY['SELECT "UserId" FROM houselens."ViewerMembers"','SELECT "Id" FROM houselens."Properties"','SELECT "Id" FROM houselens."Listings"','SELECT "Id" FROM houselens."PriceHistoryEntries"','SELECT "City" FROM houselens."DistrictConfigs"','SELECT "Id" FROM houselens."CrawlRuns"','SELECT "SourceSite" FROM houselens."SourceRunResults"','SELECT * FROM houselens.viewer_properties','SELECT * FROM houselens.viewer_top_properties','SELECT * FROM houselens.viewer_big_drops','SELECT * FROM houselens.viewer_district_stats'] LOOP
  BEGIN EXECUTE q; RAISE EXCEPTION 'Revoke allowed read: %',q;
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 END LOOP;
 RAISE NOTICE 'PASS: revoke denies every public view/table/member read';
END $$;
RESET ROLE;
SET LOCAL ROLE s1_worker;
INSERT INTO houselens."Properties" ("Id","City","District","AreaPing","HasParking","CurrentTotalPrice","Status","IsNew","FirstSeenAt","LastSeenAt")
VALUES ('00000000-0000-0000-0000-000000000099','City','Revoke',1,false,100,0,false,now(),now());
SET LOCAL ROLE s1_bypass;
UPDATE houselens."Properties" SET "CurrentTotalPrice"=101 WHERE "District"='Revoke';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM houselens."Properties" WHERE "District"='Revoke' AND "CurrentTotalPrice"=101) THEN RAISE EXCEPTION 'Revoke blocked trusted writers'; END IF;
 RAISE NOTICE 'PASS: revoke preserves owner insert and BYPASSRLS update';
END $$;
RESET ROLE;
ROLLBACK;
