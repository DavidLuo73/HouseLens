param(
 [string]$Apk=(Join-Path $PSScriptRoot '../app/build/outputs/apk/release/app-release.apk'),
 [string]$Sdk=$env:ANDROID_HOME,
 [string]$BuildToolsVersion='35.0.0'
)
$ErrorActionPreference='Stop'
if(-not (Test-Path -LiteralPath $Apk -PathType Leaf)){throw 'Signed release APK is missing; build is not deliverable'}
$tools=Join-Path $Sdk "build-tools/$BuildToolsVersion"
$badging=& (Join-Path $tools 'aapt.exe') dump badging $Apk 2>&1
if($LASTEXITCODE -ne 0){throw 'APK metadata cannot be read'}
$metadata=$badging -join "`n"
if($metadata -notmatch "package: name='tw\.houselens\.viewer' versionCode='[1-9][0-9]*' versionName='[^']+'" -or $metadata -notmatch "sdkVersion:'26'" -or $metadata -notmatch "targetSdkVersion:'36'"){throw 'Unexpected package, version or SDK contract'}
if($metadata -match 'application-debuggable'){throw 'Release APK must be non-debuggable'}
$signature=& (Join-Path $tools 'apksigner.bat') verify --verbose --print-certs $Apk 2>&1
if($LASTEXITCODE -ne 0){throw 'APK release signature verification failed'}
if(($signature -join "`n") -match 'CN=Android Debug'){throw 'APK uses Android debug signing identity'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Apk))
try {if(@($zip.Entries | Where-Object {$_.FullName -match '(?i)(\.jks$|\.keystore$|\.pem$|\.p12$|signing\.local\.properties$|supabase\.local\.properties$)'}).Count){throw 'APK contains a private configuration or key file'}} finally {$zip.Dispose()}
$badging | Where-Object {$_ -match "^(package:|sdkVersion:|targetSdkVersion:)"}
$signature | Where-Object {$_ -match '^Signer #1 certificate (DN|SHA-256 digest):'}
Get-FileHash -LiteralPath $Apk -Algorithm SHA256 | Select-Object Algorithm,Hash,Path
Write-Output 'PASS release artifact metadata and signing checks; live acceptance remains separate'
