# Boot-test the DF pack with the CmlLib harness (exactly how CloudLauncher launches it), then analyse the log.
# Usage: powershell -File tools/boot-df.ps1 [-Seconds 240]
# Exit 100 from the harness = the game was still running at the timeout (it got past mod loading).
param(
    [string]$Pack = "$env:APPDATA\CloudLauncher\default\packs\df\game",
    [string]$Harness = "C:\Users\leonr\AppData\Local\Temp\claude\C--Users-leonr-Coding\e94ada8d-ad03-48ed-9d40-617065b642fa\scratchpad\boot\bin\Release\net10.0\boot.exe",
    [string]$Analyzer = "C:\Users\leonr\AppData\Local\Temp\claude\C--Users-leonr-Coding\e94ada8d-ad03-48ed-9d40-617065b642fa\scratchpad\analyze_log.py",
    [int]$Seconds = 240,
    [string]$Version = "neoforge-21.1.250"
)
$ErrorActionPreference = "Stop"
$mods = Join-Path $Pack "mods"
# Drippy Loading Screen's early window breaks the harness (CustomLoadingOverlay ClassNotFound); park it for the run.
$drippy = Get-ChildItem $mods -Filter "drippyloadingscreen-earlywindow_*.jar" | Select-Object -First 1
$parked = $null
if ($drippy) { $parked = "$($drippy.FullName).bootparked"; Move-Item $drippy.FullName $parked; Write-Output "parked $($drippy.Name)" }
try {
    $log = Join-Path $env:TEMP "slate-df-boot.log"
    # PowerShell 5.1 turns a native command's stderr into a terminating error under 2>&1; let cmd do the redirect.
    $ErrorActionPreference = "Continue"
    cmd /c "`"$Harness`" `"$Pack`" $Version $Seconds 8192 > `"$log`" 2>&1"
    $code = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    Write-Output "harness exit $code (100 = still running at timeout = boot survived)"
} finally {
    if ($parked) { Move-Item $parked $drippy.FullName; Write-Output "restored $($drippy.Name)" }
}
$latest = Join-Path $Pack "logs\latest.log"
if (Test-Path $Analyzer) { python $Analyzer $latest } else { Write-Output "analyzer missing; grep the log yourself: $latest" }
Write-Output "--- Slate lines ---"
Select-String -Path $latest -Pattern "Slate|slate_" | Select-Object -First 40 | ForEach-Object { $_.Line }
