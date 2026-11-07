\set ON_ERROR_STOP on
BEGIN;
SET LOCAL timezone = 'Pacific/Honolulu';
CREATE FUNCTION pg_temp.assert_true(ok boolean, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF ok IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %', label; END IF;
RAISE NOTICE 'PASS: %', label; END $$;
-- A missing implementation must fail here, after a successful connection/bootstrap.
SELECT pg_temp.assert_true(to_regclass('houselens.viewer_properties') IS NOT NULL, 'viewer_properties exists');
INSERT INTO auth.users VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'), ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
SET LOCAL ROLE s1_worker;
INSERT INTO houselens."ViewerMembers" VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
INSERT INTO houselens."Properties" ("Id","City","District","Address","AreaPing","HasParking","CurrentTotalPrice","CurrentUnitPrice","Status","Score","IsNew","FirstSeenAt","LastSeenAt")
SELECT ('00000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid, 'City', 'Tie', 'Address '||i, 10, false, 100, NULL, 0, .9, true, '2026-01-01Z', '2026-02-01Z' FROM generate_series(1,6) i;
INSERT INTO houselens."Properties" ("Id","City","District","Address","AreaPing","HasParking","CurrentTotalPrice","CurrentUnitPrice","Status","Score","IsNew","FirstSeenAt","LastSeenAt")
SELECT ('00000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid, 'City', 'Boundary', NULL, 10, false, (i-10)*10+100.5,
 CASE WHEN i=10 THEN 0 WHEN i=11 THEN 20 ELSE NULL END, 0, NULL, false, '2026-01-01Z', '2026-02-01Z' FROM generate_series(10,15) i;
INSERT INTO houselens."Properties" ("Id","City","District","Address","AreaPing","HasParking","CurrentTotalPrice","CurrentUnitPrice","Status","Score","IsNew","FirstSeenAt","LastSeenAt") VALUES
 ('00000000-0000-0000-0000-000000000020','City','Single',NULL,0,false,500,NULL,0,NULL,false,'2026-01-01Z','2026-02-01Z'),
 ('00000000-0000-0000-0000-000000000021','Other','Tie',NULL,10,false,999,99,1,1,false,'2026-01-01Z','2026-02-01Z'),
 ('00000000-0000-0000-0000-000000000022','City','NoCurrent',NULL,10,false,0,NULL,0,NULL,false,'2026-01-01Z','2026-02-01Z'),
 ('00000000-0000-0000-0000-000000000023','City','Merged',NULL,10,false,100,NULL,2,1,false,'2026-01-01Z','2026-02-01Z');
INSERT INTO houselens."DistrictConfigs" ("City","District","MaxTotalPrice","IsEnabled","MaxAgeYears","ParkingCodes") VALUES
 ('City','Empty',1000,false,20,''),('City','Tie',1000,true,20,''),('Other','Tie',2000,true,30,'');
INSERT INTO houselens."Listings" VALUES
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001',2,'a','Primary title','https://example.test/a',NULL,100,true,NULL),
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001',0,'b','Secondary title','https://example.test/b',NULL,100,true,'https://example.test/img'),
 ('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001',0,'c','Third title','https://example.test/c',NULL,100,true,'https://example.test/later');
INSERT INTO houselens."CrawlRuns" VALUES
 ('20000000-0000-0000-0000-000000000001','2026-01-01Z',NULL,1,1,0,1),
 ('20000000-0000-0000-0000-000000000002','2026-02-01Z',NULL,1,1,0,1);
INSERT INTO houselens."SourceRunResults" VALUES ('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002',0,false,0,'PRIVATE ERROR');
INSERT INTO houselens."PriceHistoryEntries" VALUES
 ('30000000-0000-0000-0000-000000000000','00000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','2026-02-01T00:30:00Z',80,14.5,1,2,-.2,true),
 ('30000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','2026-01-01T23:30:00Z',80,NULL,0,2,-.2,true),
 ('30000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','2026-02-01T00:30:00Z',90,0,0,2,-.1,true),
 ('30000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','2026-02-01T00:30:00Z',95,20,2,1,.05,false),
 ('30000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001','2026-01-01Z',1.64,NULL,0,2,-.2,true),
 ('30000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000001','2026-01-01Z',1.72,NULL,0,2,-.2,true),
 ('30000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000020','20000000-0000-0000-0000-000000000001','2026-01-01Z',5,NULL,0,2,NULL,true),
 ('30000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000021','20000000-0000-0000-0000-000000000001','2026-01-01Z',5,20,0,2,-1,true),
 ('30000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000022','20000000-0000-0000-0000-000000000001','2026-01-01T00:30:00Z',100,NULL,0,0,NULL,false);
RESET ROLE;
SET LOCAL ROLE anon;
DO $$ DECLARE t text; stmt text; BEGIN
 FOREACH t IN ARRAY ARRAY['ViewerMembers','Properties','Listings','PriceHistoryEntries','DistrictConfigs','CrawlRuns','SourceRunResults','viewer_properties','viewer_top_properties','viewer_big_drops','viewer_district_stats'] LOOP
  BEGIN EXECUTE format('SELECT * FROM houselens.%I', t); RAISE EXCEPTION 'anon read succeeded: %',t;
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 END LOOP;
 FOREACH t IN ARRAY ARRAY['ViewerMembers','Properties','Listings','PriceHistoryEntries','DistrictConfigs','CrawlRuns','SourceRunResults','viewer_properties','viewer_top_properties','viewer_big_drops','viewer_district_stats'] LOOP
  FOREACH stmt IN ARRAY ARRAY[format('INSERT INTO houselens.%I DEFAULT VALUES',t),format('UPDATE houselens.%I SET %I=%I',t,CASE WHEN t='ViewerMembers' THEN 'UserId' WHEN t LIKE 'viewer_%' THEN 'district' ELSE 'Id' END,CASE WHEN t='ViewerMembers' THEN 'UserId' WHEN t LIKE 'viewer_%' THEN 'district' ELSE 'Id' END),format('DELETE FROM houselens.%I',t)] LOOP
   BEGIN EXECUTE stmt; RAISE EXCEPTION 'anon write succeeded: %',stmt;
   EXCEPTION WHEN insufficient_privilege THEN NULL; END;
  END LOOP;
 END LOOP;
 RAISE NOTICE 'PASS: anon reads and writes denied';
END $$;
RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',true);
DO $$ DECLARE t text; n bigint; BEGIN
 FOREACH t IN ARRAY ARRAY['ViewerMembers','viewer_properties','viewer_top_properties','viewer_big_drops','viewer_district_stats'] LOOP
  EXECUTE format('SELECT count(*) FROM houselens.%I',t) INTO n;
  PERFORM pg_temp.assert_true(n=0, 'unapproved sees no '||t);
 END LOOP;
END $$;
SELECT set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',true);
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM houselens."ViewerMembers"), 'own membership only');
DO $$ DECLARE n bigint; q text; BEGIN
 FOREACH q IN ARRAY ARRAY['SELECT "Id" FROM houselens."Properties"','SELECT "Id" FROM houselens."Listings"','SELECT "Id" FROM houselens."PriceHistoryEntries"','SELECT "City" FROM houselens."DistrictConfigs"','SELECT "Id" FROM houselens."CrawlRuns"','SELECT "SourceSite" FROM houselens."SourceRunResults"'] LOOP
  PERFORM set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',true);
  EXECUTE 'SELECT count(*) FROM ('||q||') x' INTO n;
  PERFORM pg_temp.assert_true(n=0,'direct table denied to unapproved member: '||q);
 END LOOP;
 PERFORM set_config('request.jwt.claim.sub','',true);
 SELECT count(*) INTO n FROM houselens.viewer_properties;
 PERFORM pg_temp.assert_true(n=0,'missing JWT identity sees zero rows');
 PERFORM set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',true);
END $$;
SELECT pg_temp.assert_true((SELECT count(*)=16 FROM houselens.viewer_properties), 'approved reads expected property count');
SELECT pg_temp.assert_true((SELECT to_jsonb(p) ?& ARRAY['id','title','city','district','address','areaPing','floor','ageYears','hasParking','currentTotalPrice','currentUnitPrice','status','score','isNew','firstSeenAt','lastSeenAt','imageUrl','listingUrl','sourceSites','latestChangeFlag','latestChangePercent','latestIsBigDrop','hasPriceDrop'] FROM houselens.viewer_properties p WHERE id='00000000-0000-0000-0000-000000000001'), 'property JSON has exact consumer camelCase fields');
SELECT pg_temp.assert_true((SELECT title='Primary title'  AND "imageUrl"='https://example.test/img' AND "listingUrl"='https://example.test/a' AND "sourceSites"=ARRAY[0,2] AND "latestChangeFlag"=1 AND "latestChangePercent"=.05 AND NOT "latestIsBigDrop" AND "hasPriceDrop" FROM houselens.viewer_properties WHERE id='00000000-0000-0000-0000-000000000001'), 'listing/history tie breaks and ever-drop');
SELECT pg_temp.assert_true((SELECT title='Address 2' AND "sourceSites"=ARRAY[]::integer[] FROM houselens.viewer_properties WHERE id='00000000-0000-0000-0000-000000000002'), 'address and empty sources fallback');
SELECT pg_temp.assert_true((SELECT title='未提供' FROM houselens.viewer_properties WHERE id='00000000-0000-0000-0000-000000000020'), 'missing title fallback');
DO $$ DECLARE ids uuid[] := ARRAY[]::uuid[]; batch uuid[]; i integer; BEGIN
 FOR i IN 0..2 LOOP
  SELECT array_agg(id ORDER BY id) INTO batch FROM (SELECT id FROM houselens.viewer_properties WHERE district='Tie' AND status=0 ORDER BY score DESC NULLS LAST,"currentTotalPrice",id LIMIT 2 OFFSET i*2) p;
  ids := ids || batch;
 END LOOP;
 PERFORM pg_temp.assert_true(ids=ARRAY['00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000006']::uuid[], 'six ties across three pages');
END $$;
SELECT pg_temp.assert_true((SELECT count(*)=5 AND max(rank)=5 AND max(id::text)='00000000-0000-0000-0000-000000000005' FROM houselens.viewer_top_properties), 'top five active scored ties');
SELECT pg_temp.assert_true((SELECT "newPrice"=90 AND "originalPrice"=100 AND "dropAmount"=10 AND "dropPercent"=.1 AND "capturedAt"='2026-02-01T00:30:00Z' FROM houselens.viewer_big_drops WHERE id='00000000-0000-0000-0000-000000000001'), 'latest big-drop event, not current price or latest history');
SELECT pg_temp.assert_true((SELECT "originalPrice"=2.0 AND "dropAmount"=.4 FROM houselens.viewer_big_drops WHERE id='00000000-0000-0000-0000-000000000002'), 'even midpoint rounds down independently');
SELECT pg_temp.assert_true((SELECT "originalPrice"=2.2 AND "dropAmount"=.4 FROM houselens.viewer_big_drops WHERE id='00000000-0000-0000-0000-000000000003'), 'odd midpoint rounds up independently');
SELECT pg_temp.assert_true((SELECT "dropPercent" IS NULL AND "originalPrice" IS NULL AND "dropAmount" IS NULL FROM houselens.viewer_big_drops WHERE id='00000000-0000-0000-0000-000000000020'), 'unknown drop remains null');
SELECT pg_temp.assert_true((SELECT status=1 AND "dropPercent"=1 AND "originalPrice" IS NULL AND "dropAmount" IS NULL FROM houselens.viewer_big_drops WHERE id='00000000-0000-0000-0000-000000000021'), 'zero divisor and delisted event');
SELECT pg_temp.assert_true((SELECT "propertyCount"=0 AND "avgUnitPrice" IS NULL AND "minTotalPrice" IS NULL AND "maxTotalPrice" IS NULL AND "priceBuckets"='[]'::jsonb AND trend='[]'::jsonb AND "insufficientData" FROM houselens.viewer_district_stats WHERE district='Empty'), 'empty disabled config district');
SELECT pg_temp.assert_true((SELECT "propertyCount"=1 AND "avgUnitPrice" IS NULL AND "minTotalPrice"=500 AND "priceBuckets"='[{"range":"500-500","count":1}]'::jsonb AND "insufficientData" FROM houselens.viewer_district_stats WHERE district='Single'), 'one invalid-unit sample');
SELECT pg_temp.assert_true((SELECT "propertyCount"=6 AND "avgUnitPrice"=10 AND "priceBuckets"='[{"range":"100-100","count":6}]'::jsonb AND NOT "insufficientData" FROM houselens.viewer_district_stats WHERE district='Tie'), 'same-name grouping, equal-price bucket, unit fallback');
SELECT pg_temp.assert_true((SELECT "propertyCount"=6 AND abs("avgUnitPrice"-14.0416666666666667)<.000000001 AND "priceBuckets"='[{"range":"100-110","count":1},{"range":"110-120","count":1},{"range":"120-130","count":1},{"range":"130-140","count":1},{"range":"140-150","count":2}]'::jsonb FROM houselens.viewer_district_stats WHERE district='Boundary'), 'exact numeric boundaries and midpoint-even labels');
SELECT pg_temp.assert_true((SELECT trend->1='{"date":"2026-02-01","avgUnitPrice":14.5}'::jsonb FROM houselens.viewer_district_stats WHERE district='Tie'), 'UTC trend includes simultaneous sources');
SELECT pg_temp.assert_true((SELECT "propertyCount"=0 AND trend='[{"date":"2026-01-01","avgUnitPrice":10}]'::jsonb FROM houselens.viewer_district_stats WHERE district='NoCurrent'), 'trend retains active history without positive current price');
SELECT pg_temp.assert_true((SELECT "SourceSite"=0 AND NOT "Success" AND "FetchedCount"=0 FROM houselens."SourceRunResults" WHERE "CrawlRunId"='20000000-0000-0000-0000-000000000002'), 'safe source projection and predicate');
DO $$ DECLARE t text; stmt text; BEGIN
 FOREACH t IN ARRAY ARRAY['ViewerMembers','Properties','Listings','PriceHistoryEntries','DistrictConfigs','CrawlRuns','SourceRunResults','viewer_properties','viewer_top_properties','viewer_big_drops','viewer_district_stats'] LOOP
  FOREACH stmt IN ARRAY ARRAY[format('INSERT INTO houselens.%I DEFAULT VALUES',t),format('UPDATE houselens.%I SET %I=%I',t,CASE WHEN t='ViewerMembers' THEN 'UserId' WHEN t LIKE 'viewer_%' THEN 'district' ELSE 'Id' END,CASE WHEN t='ViewerMembers' THEN 'UserId' WHEN t LIKE 'viewer_%' THEN 'district' ELSE 'Id' END),format('DELETE FROM houselens.%I',t)] LOOP
   BEGIN EXECUTE stmt; RAISE EXCEPTION 'write succeeded: %',stmt;
   EXCEPTION WHEN insufficient_privilege THEN NULL; WHEN object_not_in_prerequisite_state THEN
    IF t NOT LIKE 'viewer_%' THEN RAISE; END IF;
   END;
  END LOOP;
 END LOOP;
 FOREACH stmt IN ARRAY ARRAY['SELECT "ErrorMessage" FROM houselens."SourceRunResults"','SELECT "MaxTotalPrice" FROM houselens."DistrictConfigs"','SELECT "MissingCount" FROM houselens."Properties"','SELECT * FROM houselens."NotificationLogs"','SELECT * FROM houselens."TrackingCriteria"','SELECT * FROM houselens."ScoringConfigs"','SELECT * FROM houselens."PlatformFilterConfigs"','TRUNCATE houselens."Properties" CASCADE','CREATE TABLE houselens.unwanted(id int)'] LOOP
  BEGIN EXECUTE stmt; RAISE EXCEPTION 'private/write access succeeded: %',stmt;
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 END LOOP;
 RAISE NOTICE 'PASS: all API writes/private columns/settings denied';
END $$;
RESET ROLE;
SET LOCAL ROLE s1_worker;
DELETE FROM houselens."Properties" WHERE "District"='Boundary' AND "CurrentTotalPrice" NOT IN (100.5,150.5);
SET LOCAL ROLE authenticated;
SELECT pg_temp.assert_true((SELECT "priceBuckets"='[{"range":"100-110","count":1},{"range":"110-120","count":0},{"range":"120-130","count":0},{"range":"130-140","count":0},{"range":"140-150","count":1}]'::jsonb FROM houselens.viewer_district_stats WHERE district='Boundary'), 'empty middle buckets retained');
RESET ROLE;
SET LOCAL ROLE s1_worker;
INSERT INTO houselens."ViewerMembers" VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
SET LOCAL ROLE authenticated;
SELECT pg_temp.assert_true((SELECT count(*)=1 AND min("UserId"::text)='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' FROM houselens."ViewerMembers"), 'cannot enumerate other approved member');
RESET ROLE;
SET LOCAL ROLE s1_worker;
DELETE FROM houselens."ViewerMembers" WHERE "UserId"='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
UPDATE houselens."Properties" SET "CurrentTotalPrice"=101 WHERE "Id"='00000000-0000-0000-0000-000000000001';
SELECT pg_temp.assert_true((SELECT "CurrentTotalPrice"=101 FROM houselens."Properties" WHERE "Id"='00000000-0000-0000-0000-000000000001'), 'non-super owner still writes');
SET LOCAL ROLE s1_bypass;
UPDATE houselens."Properties" SET "CurrentTotalPrice"=102 WHERE "Id"='00000000-0000-0000-0000-000000000001';
SELECT pg_temp.assert_true((SELECT "CurrentTotalPrice"=102 FROM houselens."Properties" WHERE "Id"='00000000-0000-0000-0000-000000000001'), 'existing BYPASSRLS writer still writes');
SET LOCAL ROLE authenticated;
DO $$ DECLARE t text; n bigint; BEGIN
 FOREACH t IN ARRAY ARRAY['ViewerMembers','viewer_properties','viewer_top_properties','viewer_big_drops','viewer_district_stats'] LOOP
  EXECUTE format('SELECT count(*) FROM houselens.%I',t) INTO n;
  PERFORM pg_temp.assert_true(n=0, 'revoked member sees no '||t);
 END LOOP;
END $$;
RESET ROLE;
SET LOCAL ROLE s1_worker;
REVOKE SELECT ("UserId") ON houselens."ViewerMembers" FROM authenticated;
REVOKE SELECT ON houselens.viewer_properties, houselens.viewer_top_properties,
 houselens.viewer_big_drops, houselens.viewer_district_stats FROM authenticated;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',true);
DO $$ DECLARE q text; BEGIN
 FOREACH q IN ARRAY ARRAY['SELECT "UserId" FROM houselens."ViewerMembers"','SELECT "Id" FROM houselens."Properties"','SELECT "Id" FROM houselens."Listings"','SELECT "Id" FROM houselens."PriceHistoryEntries"','SELECT "City" FROM houselens."DistrictConfigs"','SELECT "Id" FROM houselens."CrawlRuns"','SELECT "SourceSite" FROM houselens."SourceRunResults"','SELECT * FROM houselens.viewer_properties','SELECT * FROM houselens.viewer_top_properties','SELECT * FROM houselens.viewer_big_drops','SELECT * FROM houselens.viewer_district_stats'] LOOP
  BEGIN EXECUTE q; RAISE EXCEPTION 'Access stop allowed read: %',q;
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 END LOOP;
 RAISE NOTICE 'PASS: documented access stop denies every API relation even to approved member';
END $$;
SET LOCAL ROLE s1_worker;
UPDATE houselens."Properties" SET "CurrentTotalPrice"=103 WHERE "Id"='00000000-0000-0000-0000-000000000001';
SELECT pg_temp.assert_true((SELECT "CurrentTotalPrice"=103 FROM houselens."Properties" WHERE "Id"='00000000-0000-0000-0000-000000000001'), 'access stop preserves owner writes');
RESET ROLE;
ROLLBACK;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM houselens."Properties") OR EXISTS (SELECT 1 FROM auth.users) THEN RAISE EXCEPTION 'Fixture rollback failed'; END IF;
 RAISE NOTICE 'PASS: fixture transaction rolled back';
END $$;
