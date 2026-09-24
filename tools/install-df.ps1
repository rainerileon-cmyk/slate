# Install the built NeoForge jars into Leon's DF pack (CloudLauncher) and retire Chatterbox.
# Usage: powershell -File tools/install-df.ps1 [-Pack <game dir>] [-KeepChatterbox]
param(
    [string]$Pack = "$env:APPDATA\CloudLauncher\default\packs\df\game",
    [switch]$KeepChatterbox
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$mods = Join-Path $Pack "mods"
if (-not (Test-Path $mods)) { throw "mods folder not found: $mods" }

# Remove any previous Slate jars (enabled or disabled), then copy the fresh ones.
Get-ChildItem $mods -Filter "slate-*-neoforge-1.21.1-*.jar*" | ForEach-Object {
    Write-Output "removing old $($_.Name)"; Remove-Item $_.FullName -Force
}
foreach ($m in @("core", "menu", "multiplayer", "chat", "config", "building")) {
    $jar = Get-ChildItem (Join-Path $root "neoforge\$m\build\libs") -Filter "slate-$m-neoforge-1.21.1-*.jar" |
        Where-Object { $_.Name -notlike "*-sources*" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "no built jar for $m - run .\gradlew.bat build in neoforge first" }
    Copy-Item $jar.FullName (Join-Path $mods $jar.Name) -Force
    Write-Output "installed $($jar.Name)"
}

# Slate Chat supersedes Chatterbox; leaving both loaded would draw two chat overlays.
if (-not $KeepChatterbox) {
    Get-ChildItem $mods -Filter "chatterbox-*.jar" | ForEach-Object {
        Rename-Item $_.FullName ($_.Name + ".disabled") -Force
        Write-Output "disabled $($_.Name) (superseded by Slate Chat)"
    }
}
Write-Output "done -> $mods"
