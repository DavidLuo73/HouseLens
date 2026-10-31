$ErrorActionPreference = 'Stop'
$project = Join-Path $PSScriptRoot 'reset-data.csproj'
dotnet build $project --nologo
if ($LASTEXITCODE -ne 0) { throw 'reset-data build failed' }
$executable = Join-Path $PSScriptRoot 'bin/Debug/net10.0/reset-data.dll'
$savedConnection = $env:ConnectionStrings__Default
try {
    $env:ConnectionStrings__Default = 'Host=127.0.0.1;Port=1;Timeout=1'
    foreach ($mode in @('reset', 'fix-yungching-images')) {
        $output = (& dotnet $executable $mode 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 2 -or $output -notmatch '--confirm') {
            throw "$mode must require confirmation before connecting"
        }
    }
    $output = (& dotnet $executable 'invalid-mode' 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 2 -or $output -notmatch '用法') { throw 'Invalid mode accepted' }
    $env:ConnectionStrings__Default = $null
    $output = (& dotnet $executable 'query' 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 2 -or $output -notmatch 'ConnectionStrings__Default') {
        throw 'Missing connection configuration was not rejected'
    }
    Write-Output 'CLI guard checks passed (no database connection).'
} finally {
    $env:ConnectionStrings__Default = $savedConnection
}
