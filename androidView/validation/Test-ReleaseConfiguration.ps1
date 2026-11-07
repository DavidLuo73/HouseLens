param([string]$EvidenceDirectory)
$ErrorActionPreference='Stop'
$viewer=Split-Path $PSScriptRoot -Parent
if (-not $EvidenceDirectory) { $EvidenceDirectory=Join-Path $viewer 'app/build/release-guard-tests' }
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$names=@('SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY','VIEWER_SIGNING_STORE_FILE','VIEWER_SIGNING_STORE_PASSWORD','VIEWER_SIGNING_KEY_ALIAS','VIEWER_SIGNING_KEY_PASSWORD')
$saved=@{}
foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$base=@{
 SUPABASE_URL='https://release-fixture.supabase.co'; SUPABASE_PUBLISHABLE_KEY='sb_publishable_release_fixture'
 VIEWER_SIGNING_STORE_FILE=' '; VIEWER_SIGNING_STORE_PASSWORD=' '; VIEWER_SIGNING_KEY_ALIAS=' '; VIEWER_SIGNING_KEY_PASSWORD=' '
}
$cases=@(
 @{Name='missing-url'; Values=@{SUPABASE_URL=' '}; Error='Missing SUPABASE_URL'},
 @{Name='missing-public-key'; Values=@{SUPABASE_PUBLISHABLE_KEY=' '}; Error='Missing SUPABASE_PUBLISHABLE_KEY'},
 @{Name='invalid-origin'; Values=@{SUPABASE_URL='http://localhost:54321'}; Error='HTTPS project origin'},
 @{Name='secret-key'; Values=@{SUPABASE_PUBLISHABLE_KEY='sb_secret_forbidden'}; Error='publishable key'},
 @{Name='missing-signing'; Values=@{}; Error='Release requires all VIEWER_SIGNING_'},
 @{Name='debug-alias'; Values=@{VIEWER_SIGNING_STORE_FILE='absent.jks';VIEWER_SIGNING_STORE_PASSWORD='fixture-password';VIEWER_SIGNING_KEY_ALIAS='androiddebugkey';VIEWER_SIGNING_KEY_PASSWORD='fixture-password'};Error='must not use the Android debug identity'},
 @{Name='missing-keystore'; Values=@{VIEWER_SIGNING_STORE_FILE='absent.jks';VIEWER_SIGNING_STORE_PASSWORD='fixture-password';VIEWER_SIGNING_KEY_ALIAS='viewer';VIEWER_SIGNING_KEY_PASSWORD='fixture-password'};Error='keystore does not exist'},
 @{Name='invalid-keystore'; Values=@{VIEWER_SIGNING_STORE_FILE=(Join-Path $viewer 'README.md');VIEWER_SIGNING_STORE_PASSWORD='fixture-password';VIEWER_SIGNING_KEY_ALIAS='viewer';VIEWER_SIGNING_KEY_PASSWORD='fixture-password'};Error='cannot be loaded'}
)
try {
 foreach($case in $cases){
  foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$base[$name],'Process')}
  foreach($name in $case.Values.Keys){[Environment]::SetEnvironmentVariable($name,$case.Values[$name],'Process')}
  $output=& (Join-Path $viewer 'gradlew.bat') -p $viewer :app:assembleRelease --max-workers=2 2>&1
  $code=$LASTEXITCODE
  $output | Set-Content -Encoding utf8 (Join-Path $EvidenceDirectory "$($case.Name).log")
  if($code -eq 0 -or ($output -join "`n") -notmatch [regex]::Escape($case.Error)){throw "Release guard case failed: $($case.Name) (exit $code)"}
  Write-Output "PASS $($case.Name): release refused"
 }
} finally {foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
