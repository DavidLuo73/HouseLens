-- Run as the existing houselens table owner. No Auth identities or backend data are changed.
BEGIN;
SET LOCAL search_path = pg_catalog, pg_temp;
SET LOCAL lock_timeout = '10s';
CREATE TEMP TABLE viewer_columns (table_name text PRIMARY KEY, columns text[]) ON COMMIT DROP;
INSERT INTO viewer_columns VALUES
 ('Properties', ARRAY['Id','City','District','Address','AreaPing','Floor','AgeYears','HasParking','CurrentTotalPrice','CurrentUnitPrice','Status','Score','IsNew','FirstSeenAt','LastSeenAt']),
 ('Listings', ARRAY['Id','PropertyId','SourceSite','Title','Url','ImageUrl','PostedDate']),
 ('PriceHistoryEntries', ARRAY['Id','PropertyId','CapturedAt','TotalPrice','UnitPrice','SourceSite','ChangeFlag','ChangePercent','IsBigDrop']),
 ('DistrictConfigs', ARRAY['City','District','IsEnabled']),
 ('CrawlRuns', ARRAY['Id','StartedAt','FinishedAt','Status','NewCount','DelistedCount','BigDropCount']),
 ('SourceRunResults', ARRAY['CrawlRunId','SourceSite','Success','FetchedCount']),
 ('ViewerMembers', ARRAY['UserId']);
DO $$
DECLARE r record; api text; col record; managed boolean;
BEGIN
 IF current_setting('server_version_num')::int < 150000 THEN RAISE EXCEPTION 'PostgreSQL 15+ required for security_invoker views'; END IF;
 IF to_regnamespace('houselens') IS NULL OR to_regclass('auth.users') IS NULL OR to_regprocedure('auth.uid()') IS NULL THEN
  RAISE EXCEPTION 'Existing houselens schema and Supabase Auth required';
 END IF;
 FOREACH api IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=api AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole) THEN
   RAISE EXCEPTION 'Unsafe or missing API role: %', api;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles g WHERE g.rolname<>api AND pg_has_role(api,g.oid,'MEMBER')
   AND (g.rolsuper OR g.rolbypassrls OR g.oid=(SELECT nspowner FROM pg_namespace WHERE nspname='houselens')
    OR EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace='houselens'::regnamespace AND c.relowner=g.oid))) THEN
   RAISE EXCEPTION 'Unexpected API role membership for %',api;
  END IF;
  IF has_schema_privilege(api,'houselens','CREATE') OR (api='anon' AND has_schema_privilege(api,'houselens','USAGE')) THEN
   RAISE EXCEPTION 'Unexpected schema grant for %',api;
  END IF;
 END LOOP;
 -- Defaults can silently grant writes to newly created members/views; refuse rather than revoke them.
 IF EXISTS (SELECT 1 FROM pg_default_acl d CROSS JOIN LATERAL aclexplode(d.defaclacl) a
  LEFT JOIN pg_roles g ON g.oid=a.grantee
  WHERE d.defaclrole=(SELECT oid FROM pg_roles WHERE rolname=current_user)
   AND d.defaclnamespace IN (0,to_regnamespace('houselens')) AND d.defaclobjtype='r'
   AND a.grantee<>d.defaclrole AND NOT coalesce(g.rolsuper OR g.rolbypassrls,false)) THEN
  RAISE EXCEPTION 'Unexpected table default grants; review before installing';
 END IF;
 FOR r IN SELECT * FROM viewer_columns WHERE table_name<>'ViewerMembers' LOOP
  IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid=to_regclass(format('houselens.%I',r.table_name)) AND relkind='r'
   AND relowner=(SELECT oid FROM pg_roles WHERE rolname=current_user) AND NOT relforcerowsecurity) THEN
   RAISE EXCEPTION 'Expected an existing ordinary table owned by current role, without FORCE RLS: %',r.table_name;
  END IF;
  EXECUTE format('LOCK TABLE houselens.%I IN ACCESS EXCLUSIVE MODE',r.table_name);
 END LOOP;
 FOR r IN SELECT c.*, obj_description(c.oid,'pg_class') AS marker FROM pg_class c WHERE c.relnamespace='houselens'::regnamespace LOOP
  managed := r.relname IN ('ViewerMembers','viewer_properties','viewer_top_properties','viewer_big_drops','viewer_district_stats');
  IF managed AND (r.marker IS DISTINCT FROM 'houselens-viewer:v1' OR r.relowner<>(SELECT oid FROM pg_roles WHERE rolname=current_user)
    OR (r.relname='ViewerMembers' AND (r.relkind<>'r' OR r.relforcerowsecurity))
    OR (r.relname<>'ViewerMembers' AND (r.relkind<>'v' OR NOT coalesce(r.reloptions @> ARRAY['security_invoker=true'],false)))) THEN
   RAISE EXCEPTION 'Unmanaged or unsafe object collision: %',r.relname;
  END IF;
  IF r.relkind NOT IN ('r','v','m','p','f') THEN CONTINUE; END IF;
  -- Inspect direct grants, including unrelated roles, and inherited effective API access.
  IF EXISTS (SELECT 1 FROM aclexplode(r.relacl) a LEFT JOIN pg_roles g ON g.oid=a.grantee
    WHERE a.grantee<>r.relowner AND NOT coalesce(g.rolsuper OR g.rolbypassrls,false)
     AND NOT (g.rolname='authenticated' AND managed AND r.relkind='v' AND a.privilege_type='SELECT' AND NOT a.is_grantable)) THEN
   RAISE EXCEPTION 'Unexpected table grant on %',r.relname;
  END IF;
  FOREACH api IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF has_table_privilege(api,r.oid,'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
    OR (has_table_privilege(api,r.oid,'SELECT') AND NOT (api='authenticated' AND managed AND r.relkind='v')) THEN
    RAISE EXCEPTION 'Unexpected effective table access for % on %',api,r.relname;
   END IF;
  END LOOP;
  FOR col IN SELECT * FROM pg_attribute WHERE attrelid=r.oid AND attnum>0 AND NOT attisdropped LOOP
   IF EXISTS (SELECT 1 FROM aclexplode(col.attacl) a LEFT JOIN pg_roles g ON g.oid=a.grantee
     WHERE a.grantee<>r.relowner AND NOT coalesce(g.rolsuper OR g.rolbypassrls,false)
      AND NOT (g.rolname='authenticated' AND a.privilege_type='SELECT' AND NOT a.is_grantable
       AND EXISTS (SELECT 1 FROM viewer_columns v WHERE v.table_name=r.relname AND col.attname=ANY(v.columns)))) THEN
    RAISE EXCEPTION 'Unexpected column grant on %.%',r.relname,col.attname;
   END IF;
   IF has_column_privilege('anon',r.oid,col.attnum,'SELECT,INSERT,UPDATE,REFERENCES')
    OR has_column_privilege('authenticated',r.oid,col.attnum,'INSERT,UPDATE,REFERENCES')
    OR (has_column_privilege('authenticated',r.oid,col.attnum,'SELECT') AND NOT
      ((managed AND r.relkind='v') OR EXISTS (SELECT 1 FROM viewer_columns v WHERE v.table_name=r.relname AND col.attname=ANY(v.columns)))) THEN
    RAISE EXCEPTION 'Unexpected effective column access on %.%',r.relname,col.attname;
   END IF;
  END LOOP;
 END LOOP;
END $$;
CREATE TABLE IF NOT EXISTS houselens."ViewerMembers" ("UserId" uuid PRIMARY KEY REFERENCES auth.users(id));
COMMENT ON TABLE houselens."ViewerMembers" IS 'houselens-viewer:v1';
DO $$ BEGIN
 IF (SELECT count(*) FROM pg_attribute WHERE attrelid='houselens."ViewerMembers"'::regclass AND attnum>0 AND NOT attisdropped)<>1
  OR NOT EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid='houselens."ViewerMembers"'::regclass AND attname='UserId' AND atttypid='uuid'::regtype AND attnotnull)
  OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='houselens."ViewerMembers"'::regclass AND contype='p' AND conkey=ARRAY[1]::smallint[])
  OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='houselens."ViewerMembers"'::regclass AND contype='f' AND conkey=ARRAY[1]::smallint[]
   AND confrelid='auth.users'::regclass AND confkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid='auth.users'::regclass AND attname='id')]::smallint[]) THEN
  RAISE EXCEPTION 'Unexpected ViewerMembers structure';
 END IF;
END $$;
-- Parse canonical expected policies with PostgreSQL itself; do not trust a marker alone.
CREATE TEMP TABLE viewer_policy_expected ("UserId" uuid) ON COMMIT DROP;
CREATE POLICY viewer_own_row ON viewer_policy_expected FOR SELECT TO authenticated USING ("UserId"=(SELECT auth.uid()));
CREATE POLICY viewer_member ON viewer_policy_expected FOR SELECT TO authenticated USING
 (EXISTS (SELECT 1 FROM houselens."ViewerMembers" m WHERE m."UserId"=(SELECT auth.uid())));
DO $$ DECLARE r record; p record; expected record; policy_name text; column_list text; BEGIN
 FOR r IN SELECT v.*, c.oid, c.relrowsecurity FROM viewer_columns v JOIN pg_class c ON c.oid=to_regclass(format('houselens.%I',v.table_name)) LOOP
  policy_name := CASE WHEN r.table_name='ViewerMembers' THEN 'viewer_own_row' ELSE 'viewer_member' END;
  SELECT * INTO expected FROM pg_policy WHERE polrelid='pg_temp.viewer_policy_expected'::regclass AND polname=policy_name;
  IF r.relrowsecurity AND NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid=r.oid AND polname=policy_name) THEN
   RAISE EXCEPTION 'Unexpected preexisting RLS state on %',r.table_name;
  END IF;
  FOR p IN SELECT * FROM pg_policy WHERE polrelid=r.oid LOOP
   IF p.polname<>policy_name OR obj_description(p.oid,'pg_policy') IS DISTINCT FROM 'houselens-viewer:v1'
    OR p.polcmd<>expected.polcmd OR p.polroles<>expected.polroles OR p.polpermissive<>expected.polpermissive
    OR pg_get_expr(p.polqual,p.polrelid) IS DISTINCT FROM pg_get_expr(expected.polqual,expected.polrelid)
    OR p.polwithcheck IS NOT NULL THEN
    RAISE EXCEPTION 'Unexpected policy drift on %: %',r.table_name,p.polname;
   END IF;
  END LOOP;
  IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid=r.oid AND polname=policy_name) THEN
   EXECUTE format('CREATE POLICY %I ON houselens.%I FOR SELECT TO authenticated USING (%s)', policy_name,r.table_name,pg_get_expr(expected.polqual,expected.polrelid));
   EXECUTE format('COMMENT ON POLICY %I ON houselens.%I IS %L',policy_name,r.table_name,'houselens-viewer:v1');
  END IF;
  EXECUTE format('ALTER TABLE houselens.%I ENABLE ROW LEVEL SECURITY',r.table_name);
  SELECT string_agg(quote_ident(c),',') INTO column_list FROM unnest(r.columns) c;
  EXECUTE format('GRANT SELECT (%s) ON houselens.%I TO authenticated',column_list,r.table_name);
 END LOOP;
END $$;
GRANT USAGE ON SCHEMA houselens TO authenticated;

CREATE OR REPLACE VIEW houselens.viewer_properties WITH (security_invoker=true) AS
SELECT p."Id" AS id, coalesce(l."Title",p."Address",'未提供') AS title,
 p."City" AS city, p."District" AS district, p."Address" AS address, p."AreaPing" AS "areaPing",
 p."Floor" AS floor, p."AgeYears" AS "ageYears", p."HasParking" AS "hasParking",
 p."CurrentTotalPrice" AS "currentTotalPrice", p."CurrentUnitPrice" AS "currentUnitPrice",
 p."Status" AS status, p."Score" AS score, p."IsNew" AS "isNew", p."FirstSeenAt" AS "firstSeenAt", p."LastSeenAt" AS "lastSeenAt",
 (SELECT i."ImageUrl" FROM houselens."Listings" i WHERE i."PropertyId"=p."Id" AND i."ImageUrl" IS NOT NULL ORDER BY i."Id" LIMIT 1) AS "imageUrl",
 l."Url" AS "listingUrl",
 ARRAY(SELECT DISTINCT s."SourceSite" FROM houselens."Listings" s WHERE s."PropertyId"=p."Id" ORDER BY s."SourceSite") AS "sourceSites",
 h."ChangeFlag" AS "latestChangeFlag", h."ChangePercent" AS "latestChangePercent", coalesce(h."IsBigDrop",false) AS "latestIsBigDrop",
 EXISTS (SELECT 1 FROM houselens."PriceHistoryEntries" d WHERE d."PropertyId"=p."Id" AND d."ChangeFlag"=2) AS "hasPriceDrop"
FROM houselens."Properties" p
LEFT JOIN LATERAL (SELECT "Title","Url" FROM houselens."Listings" WHERE "PropertyId"=p."Id" ORDER BY "Id" LIMIT 1) l ON true
LEFT JOIN LATERAL (SELECT "ChangeFlag","ChangePercent","IsBigDrop" FROM houselens."PriceHistoryEntries" WHERE "PropertyId"=p."Id" ORDER BY "CapturedAt" DESC,"Id" DESC LIMIT 1) h ON true;
COMMENT ON VIEW houselens.viewer_properties IS 'houselens-viewer:v1';

CREATE OR REPLACE VIEW houselens.viewer_top_properties WITH (security_invoker=true) AS
SELECT district, rank, id, title, "currentTotalPrice" AS "totalPrice", "currentUnitPrice" AS "unitPrice", "ageYears", "hasParking", score
FROM (SELECT p.*, row_number() OVER (PARTITION BY district ORDER BY score DESC,id) AS rank
 FROM houselens.viewer_properties p WHERE status=0 AND score IS NOT NULL) ranked WHERE rank<=5;
COMMENT ON VIEW houselens.viewer_top_properties IS 'houselens-viewer:v1';

CREATE OR REPLACE VIEW houselens.viewer_big_drops WITH (security_invoker=true) AS
SELECT p.id, p.title, p.district, p.address,
 -- PostgreSQL numeric round uses ties-away; retain .NET midpoint-to-even independently for each amount.
 CASE WHEN abs(raw.original*10-trunc(raw.original*10))=.5
  THEN (trunc(raw.original*10)+sign(raw.original)*mod(abs(trunc(raw.original*10)),2))/10 ELSE round(raw.original,1) END AS "originalPrice",
 h."TotalPrice" AS "newPrice",
 CASE WHEN abs(raw.amount*10-trunc(raw.amount*10))=.5
  THEN (trunc(raw.amount*10)+sign(raw.amount)*mod(abs(trunc(raw.amount*10)),2))/10 ELSE round(raw.amount,1) END AS "dropAmount",
 -h."ChangePercent" AS "dropPercent", h."CapturedAt" AS "capturedAt", p.status
FROM houselens.viewer_properties p
JOIN LATERAL (SELECT "TotalPrice","ChangePercent","CapturedAt" FROM houselens."PriceHistoryEntries"
 WHERE "PropertyId"=p.id AND "IsBigDrop" ORDER BY "CapturedAt" DESC,"Id" DESC LIMIT 1) h ON true
CROSS JOIN LATERAL (SELECT h."TotalPrice"/nullif(1+h."ChangePercent",0) AS original,
 h."TotalPrice"/nullif(1+h."ChangePercent",0)-h."TotalPrice" AS amount) raw;
COMMENT ON VIEW houselens.viewer_big_drops IS 'houselens-viewer:v1';

CREATE OR REPLACE VIEW houselens.viewer_district_stats WITH (security_invoker=true) AS
WITH districts AS (SELECT "District" AS district FROM houselens."DistrictConfigs" UNION SELECT "District" FROM houselens."Properties"),
 active AS (SELECT "Id", "District" AS district, "AreaPing", "CurrentTotalPrice" AS price, "CurrentUnitPrice" FROM houselens."Properties" WHERE "Status"=0),
 sample AS (SELECT *, CASE WHEN "CurrentUnitPrice">0 THEN "CurrentUnitPrice" WHEN "AreaPing">0 THEN price/"AreaPing" END AS unit FROM active WHERE price>0),
 stats AS (SELECT district,count(*)::integer AS n,avg(unit) AS unit,min(price) AS lo,max(price) AS hi FROM sample GROUP BY district),
 bounds AS (SELECT s.district, i, s.lo+(s.hi-s.lo)*i/5 AS lo,
 s.lo+(s.hi-s.lo)*(i+1)/5 AS hi, s.lo=s.hi AS equal_price FROM stats s CROSS JOIN LATERAL generate_series(0,CASE WHEN s.lo=s.hi THEN 0 ELSE 4 END) i),
 buckets AS (SELECT b.district, b.i, jsonb_build_object('range',
  (CASE WHEN abs(b.lo-trunc(b.lo))=.5 THEN trunc(b.lo)+sign(b.lo)*mod(abs(trunc(b.lo)),2) ELSE round(b.lo) END)::text || '-' ||
  (CASE WHEN abs(b.hi-trunc(b.hi))=.5 THEN trunc(b.hi)+sign(b.hi)*mod(abs(trunc(b.hi)),2) ELSE round(b.hi) END)::text,
  'count',(SELECT count(*) FROM sample s WHERE s.district=b.district AND s.price>=b.lo AND (s.price<b.hi OR ((b.i=4 OR b.equal_price) AND s.price<=b.hi)))) AS bucket FROM bounds b),
 observations AS (SELECT a.district, (h."CapturedAt" AT TIME ZONE 'UTC')::date AS date,
 CASE WHEN h."UnitPrice">0 THEN h."UnitPrice" WHEN h."TotalPrice">0 AND a."AreaPing">0 THEN h."TotalPrice"/a."AreaPing" END AS unit
 FROM active a JOIN houselens."PriceHistoryEntries" h ON h."PropertyId"=a."Id"),
 daily AS (SELECT district,date,avg(unit) AS unit FROM observations WHERE unit>0 GROUP BY district,date)
SELECT d.district, coalesce(s.n,0) AS "propertyCount", s.unit AS "avgUnitPrice", s.lo AS "minTotalPrice", s.hi AS "maxTotalPrice",
 coalesce((SELECT jsonb_agg(b.bucket ORDER BY b.i) FROM buckets b WHERE b.district=d.district),'[]'::jsonb) AS "priceBuckets",
 coalesce((SELECT jsonb_agg(jsonb_build_object('date',t.date,'avgUnitPrice',t.unit) ORDER BY t.date) FROM daily t WHERE t.district=d.district),'[]'::jsonb) AS trend,
 coalesce(s.n,0)<2 AS "insufficientData"
FROM districts d LEFT JOIN stats s ON s.district=d.district;
COMMENT ON VIEW houselens.viewer_district_stats IS 'houselens-viewer:v1';
GRANT SELECT ON houselens.viewer_properties,houselens.viewer_top_properties,houselens.viewer_big_drops,houselens.viewer_district_stats TO authenticated;
COMMIT;
