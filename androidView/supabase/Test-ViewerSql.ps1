param(
    [string]$DatabaseHost = '127.0.0.1',
    [int]$Port = 55439,
    [switch]$SelfTest
)
$ErrorActionPreference = 'Stop'
function Assert-Target([string]$TargetHost, [int]$TargetPort, [string]$Database) {
    if ($TargetHost -cne '127.0.0.1' -or $TargetPort -lt 1024 -or $TargetPort -gt 65535 -or $Database -cnotmatch '^s1_test_[0-9a-f]{12}$') {
        throw 'Only a new S1-owned database on literal 127.0.0.1 and an unprivileged port is allowed.'
    }
}
if ($SelfTest) {
    foreach ($bad in @('db.example.com','localhost','0.0.0.0','127.0.0.1 evil','::1')) {
        $rejected = $false
        try { Assert-Target $bad 55439 's1_test_0123456789ab' } catch { $rejected = $true }
        if (!$rejected) { throw "Accepted unsafe host: $bad" }
    }
    foreach ($bad in @('postgres','s1_test_x','s1_test_0123456789ab;DROP DATABASE postgres')) {
        $rejected = $false
        try { Assert-Target '127.0.0.1' 55439 $bad } catch { $rejected = $true }
        if (!$rejected) { throw 'Accepted unsafe database identifier' }
    }
    foreach ($bad in @(0,1023,65536)) {
        $rejected = $false
        try { Assert-Target '127.0.0.1' $bad 's1_test_0123456789ab' } catch { $rejected = $true }
        if (!$rejected) { throw 'Accepted unsafe port' }
    }
    Assert-Target '127.0.0.1' 55439 's1_test_0123456789ab'
    Write-Output 'PASS: unsafe hosts/database identifiers rejected before invoking PostgreSQL'
    exit 0
}
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$bin = Join-Path $repo '.superpowers/sdd/01-api-plan/pgsql/bin'
$id = [guid]::NewGuid().ToString('N').Substring(0,12)
$database = "s1_test_$id"
Assert-Target $DatabaseHost $Port $database
$run = Join-Path $repo ".superpowers/sdd/01-supabase-plan/runs/$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ'))-$id"
$data = Join-Path $run 'data'
# Refuse an occupied port before starting any process; verify data_directory again before SQL.
$probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$Port)
try { $probe.Start() } finally { $probe.Stop() }
New-Item -ItemType Directory -Path $run | Out-Null
Start-Transcript -Path (Join-Path $run 'runner.log') | Out-Null
$savedPg = @{}
Get-ChildItem Env:PG* | ForEach-Object { $savedPg[$_.Name]=$_.Value; Remove-Item -LiteralPath "Env:$($_.Name)" }
$env:PGPASSFILE = Join-Path $run 'no-password-file'
$env:PGSSLMODE = 'disable'
$psql = Join-Path $bin 'psql.exe'
function Invoke-Sql([string]$Label, [string[]]$SqlArgs, [string]$Db = $database) {
    & $psql -X -w -v ON_ERROR_STOP=1 -h $DatabaseHost -p $Port -U s1_admin -d $Db @SqlArgs 2>&1 | Tee-Object -FilePath (Join-Path $run "$Label.log") | Write-Output
    if ($LASTEXITCODE -ne 0) { throw "$Label failed (psql exit $LASTEXITCODE); see $run" }
}
$started = $false
try {
    & (Join-Path $bin 'initdb.exe') -D $data -U s1_admin -A trust --encoding=UTF8 --locale=C 2>&1 | Tee-Object -FilePath (Join-Path $run 'initdb.log') | Write-Output
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed' }
    $process = Start-Process -FilePath (Join-Path $bin 'pg_ctl.exe') -ArgumentList @('-D',('"'+$data+'"'),'-l',('"'+(Join-Path $run 'postgres.log')+'"'),'-o',('"-h 127.0.0.1 -p '+$Port+'"'),'-w','start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'start.log') -RedirectStandardError (Join-Path $run 'start-error.log')
    $process.WaitForExit()
    $started = Test-Path (Join-Path $data 'postmaster.pid')
    if ($process.ExitCode -ne 0) { throw 'Owned PostgreSQL startup failed' }
    $actual = & $psql -X -w -At -h $DatabaseHost -p $Port -U s1_admin -d postgres -c 'SHOW data_directory'
    if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($actual) -ne [IO.Path]::GetFullPath($data)) { throw 'Connected server is not this run owned cluster' }
    Invoke-Sql 'create-database' @('-c',"CREATE DATABASE $database") 'postgres'
    Invoke-Sql 'bootstrap' @('-f',(Join-Path $PSScriptRoot 'tests/bootstrap.sql'))
    $install = Join-Path $PSScriptRoot 'viewer.sql'
    if (Test-Path $install) {
        Invoke-Sql 'install' @('-c','SET ROLE s1_worker','-f',$install)
        Invoke-Sql 'reinstall' @('-c','SET ROLE s1_worker','-f',$install)
    }
    Invoke-Sql 'anon-no-login-read' @('-c','SET ROLE anon; SELECT count(*) FROM houselens.viewer_properties')
    Invoke-Sql 'verify' @('-f',(Join-Path $PSScriptRoot 'tests/verify.sql'))
    # Each probe changes this owned fixture only, then requires the installer to fail and preserve that state.
    $probes = @(
        @('write-grant','GRANT INSERT ON houselens."Properties" TO anon','REVOKE INSERT ON houselens."Properties" FROM anon','Unexpected table grant'),
        @('private-column','GRANT SELECT ("ErrorMessage") ON houselens."SourceRunResults" TO anon','REVOKE SELECT ("ErrorMessage") ON houselens."SourceRunResults" FROM anon','Unexpected column grant'),
        @('public-grant','GRANT SELECT ("ErrorMessage") ON houselens."SourceRunResults" TO PUBLIC','REVOKE SELECT ("ErrorMessage") ON houselens."SourceRunResults" FROM PUBLIC','Unexpected column grant'),
        @('unknown-policy','CREATE POLICY unexpected ON houselens."Properties" FOR SELECT TO anon USING (true)','DROP POLICY unexpected ON houselens."Properties"','Unexpected policy drift'),
        @('altered-policy','ALTER POLICY viewer_public_read ON houselens."Properties" USING (false)','ALTER POLICY viewer_public_read ON houselens."Properties" USING (true)','Unexpected policy drift'),
        @('force-rls','ALTER TABLE houselens."Properties" FORCE ROW LEVEL SECURITY','ALTER TABLE houselens."Properties" NO FORCE ROW LEVEL SECURITY','without FORCE RLS'),
        @('object-collision','COMMENT ON VIEW houselens.viewer_properties IS ''unmanaged''','COMMENT ON VIEW houselens.viewer_properties IS ''houselens-viewer:anonymous-v1''','Unmanaged or unsafe object collision'),
        @('definer-view','ALTER VIEW houselens.viewer_properties SET (security_invoker=false)','ALTER VIEW houselens.viewer_properties SET (security_invoker=true)','Unmanaged or unsafe object collision'),
        @('default-grant','ALTER DEFAULT PRIVILEGES FOR ROLE s1_worker IN SCHEMA houselens GRANT SELECT ON TABLES TO anon','ALTER DEFAULT PRIVILEGES FOR ROLE s1_worker IN SCHEMA houselens REVOKE SELECT ON TABLES FROM anon','Unexpected table default grants'),
        @('set-role-owner','GRANT s1_worker TO anon WITH INHERIT FALSE','REVOKE s1_worker FROM anon','Unexpected API role membership'),
        @('inherited-owner','GRANT s1_worker TO anon','REVOKE s1_worker FROM anon','Unexpected API role membership'),
        @('member-grant','GRANT SELECT ("UserId") ON houselens."ViewerMembers" TO anon','REVOKE SELECT ("UserId") ON houselens."ViewerMembers" FROM anon','Unexpected column grant'),
        @('admin-grant','GRANT SELECT ON houselens."NotificationLogs" TO anon','REVOKE SELECT ON houselens."NotificationLogs" FROM anon','Unexpected table grant'),
        @('column-write','GRANT UPDATE ("Address") ON houselens."Properties" TO anon','REVOKE UPDATE ("Address") ON houselens."Properties" FROM anon','Unexpected column grant'),
        @('grant-option','GRANT SELECT ("Address") ON houselens."Properties" TO anon WITH GRANT OPTION','REVOKE GRANT OPTION FOR SELECT ("Address") ON houselens."Properties" FROM anon','Unexpected column grant'),
        @('sequence-write','GRANT USAGE ON SEQUENCE houselens."DistrictConfigs_Id_seq" TO anon','REVOKE USAGE ON SEQUENCE houselens."DistrictConfigs_Id_seq" FROM anon','Unexpected API sequence access'),
        @('legacy-installer','COMMENT ON VIEW houselens.viewer_properties IS ''houselens-viewer:v1''','COMMENT ON VIEW houselens.viewer_properties IS ''houselens-viewer:anonymous-v1''','Legacy authenticated installer detected'),
        @('authenticated-grant','GRANT SELECT ON houselens.viewer_properties TO authenticated','REVOKE SELECT ON houselens.viewer_properties FROM authenticated','Unexpected table grant'),
        @('schema-owner','ALTER SCHEMA houselens OWNER TO s1_admin','ALTER SCHEMA houselens OWNER TO s1_worker','Expected current role to own houselens schema')
    )
    foreach ($probe in $probes) {
        $label = $probe[0]
        Invoke-Sql "drift-$label-setup" @('-c',$probe[1])
        try {
            $output = & $psql -X -w -v ON_ERROR_STOP=1 -h $DatabaseHost -p $Port -U s1_admin -d $database -c 'SET ROLE s1_worker' -f $install 2>&1
            $code = $LASTEXITCODE
            $output | Set-Content -Encoding utf8 (Join-Path $run "drift-$label-rejection.log")
            if ($code -ne 3 -or ($output -join "`n") -notmatch [regex]::Escape($probe[3])) { throw "Installer did not reject $label as expected" }
            Write-Output "PASS: installer rejected $label (transaction disconnected/rolled back)"
        } finally {
            Invoke-Sql "drift-$label-cleanup" @('-c',$probe[2])
        }
    }
    # Force a late failure after an earlier table was changed, and prove the earlier change rolls back.
    Invoke-Sql 'rollback-setup' @('-c','ALTER TABLE houselens."Properties" DISABLE ROW LEVEL SECURITY; DROP POLICY viewer_public_read ON houselens."Properties"; ALTER POLICY viewer_public_read ON houselens."SourceRunResults" USING (false)')
    $output = & $psql -X -w -v ON_ERROR_STOP=1 -h $DatabaseHost -p $Port -U s1_admin -d $database -c 'SET ROLE s1_worker' -f $install 2>&1
    $code = $LASTEXITCODE
    $output | Set-Content -Encoding utf8 (Join-Path $run 'rollback-rejection.log')
    if ($code -ne 3 -or ($output -join "`n") -notmatch 'Unexpected policy drift on SourceRunResults') { throw 'Late rollback probe did not fail at expected table' }
    Invoke-Sql 'rollback-verify' @('-c','DO $$ BEGIN IF (SELECT relrowsecurity FROM pg_class WHERE oid=''houselens."Properties"''::regclass) OR EXISTS (SELECT 1 FROM pg_policy WHERE polrelid=''houselens."Properties"''::regclass) THEN RAISE EXCEPTION ''Partial installation survived rollback''; END IF; RAISE NOTICE ''PASS: earlier policy and RLS changes rolled back after later failure''; END $$')
    Invoke-Sql 'rollback-cleanup' @('-c','ALTER POLICY viewer_public_read ON houselens."SourceRunResults" USING (true)')
    Invoke-Sql 'final-install' @('-c','SET ROLE s1_worker','-f',$install)
    Invoke-Sql 'final-verify' @('-f',(Join-Path $PSScriptRoot 'tests/verify.sql'))
    $revoke = Join-Path $PSScriptRoot 'revoke.sql'
    Invoke-Sql 'revoke' @('-c','SET ROLE s1_worker','-f',$revoke)
    Invoke-Sql 'revoke-repeat' @('-c','SET ROLE s1_worker','-f',$revoke)
    Invoke-Sql 'revoke-verify' @('-f',(Join-Path $PSScriptRoot 'tests/verify-revoke.sql'))
    Invoke-Sql 'restore-after-revoke' @('-c','SET ROLE s1_worker','-f',$install)
    Invoke-Sql 'restore-verify' @('-f',(Join-Path $PSScriptRoot 'tests/verify.sql'))
    Write-Output 'PASS: anonymous PostgreSQL contract/denials, reinstall, drift rejection, atomic rollback, revoke and restore'

} finally {
    $stopFailed = $false
    if ($started) {
        & (Join-Path $bin 'pg_ctl.exe') -D $data -m fast -w stop 2>&1 | Tee-Object -FilePath (Join-Path $run 'stop.log') | Write-Output
        if ($LASTEXITCODE -ne 0) { $stopFailed = $true; Write-Warning "Owned cluster did not stop; inspect $data" }
    }
    Get-ChildItem Env:PG* | ForEach-Object { Remove-Item -LiteralPath "Env:$($_.Name)" }
    foreach ($entry in $savedPg.GetEnumerator()) { Set-Item -LiteralPath "Env:$($entry.Key)" -Value $entry.Value }
    Write-Output "Evidence preserved: $run (cluster stop failed: $stopFailed; no files/databases deleted)"
    Stop-Transcript | Out-Null
    if ($stopFailed) { throw 'Owned cluster cleanup failed; inspect stop.log' }
}
