param()
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$testWorldPath = Join-Path $projectPath 'work\gametest-run\world'
$clientWorldPath = Join-Path $projectPath 'work\client-run\saves\WindweavePhysicsTest'
if (-not (Test-Path -LiteralPath (Join-Path $testWorldPath 'level.dat'))) {
    throw 'Run .\gradlew.bat runGameTestServer successfully before preparing the client test world.'
}
if (-not (Test-Path -LiteralPath (Join-Path $clientWorldPath 'level.dat'))) {
    New-Item -ItemType Directory -Force -Path $clientWorldPath | Out-Null
    Get-ChildItem -LiteralPath $testWorldPath | Where-Object Name -ne 'session.lock' | Copy-Item -Destination $clientWorldPath -Recurse
}
$optionsPath = Join-Path $projectPath 'work\client-run\options.txt'
if (-not (Test-Path -LiteralPath $optionsPath)) {
    "lang:zh_cn`nrenderDistance:8`nsimulationDistance:5`nmaxFps:60`nguiScale:2`npauseOnLostFocus:false`n" | Set-Content -LiteralPath $optionsPath -Encoding utf8
}
Write-Output 'Client test world ready. Run .\gradlew.bat runPhysicsClientTest.'
