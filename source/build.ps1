param([string]$Repository = '', [switch]$Offline, [switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mavenCommand) {
    $mavenPath = $mavenCommand.Source
} else {
    $wrapperRoot = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
    $cached = Get-ChildItem -LiteralPath $wrapperRoot -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $cached) { throw 'Can Maven 3.6.3+ trong PATH (mvn), hoac Maven wrapper da duoc tai trong ~/.m2/wrapper/dists.' }
    $mavenPath = $cached.FullName
}
$temporary = Join-Path $PSScriptRoot 'target\test-temp'
New-Item -ItemType Directory -Force -Path $temporary | Out-Null
$mavenArgs = @('-f', (Join-Path $PSScriptRoot 'pom.xml'), 'package', "-DargLine=-Djava.io.tmpdir=`"$temporary`"")
if ($Repository) { $mavenArgs += "-Dmaven.repo.local=$Repository" }
if ($Offline) { $mavenArgs += '-o' }
if ($SkipTests) { $mavenArgs += '-DskipTests' }
& $mavenPath @mavenArgs
if ($LASTEXITCODE -ne 0) { throw "Maven build failed ($LASTEXITCODE)." }
