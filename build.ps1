# Convenience build script: resolves a JDK 21, pins JAVA_HOME, then runs Maven
# with whatever goals you pass.
#
#   ./build.ps1 clean verify
#   ./build.ps1 spring-boot:run
#
# JDK resolution order:
#   1. $env:JAVA_HOME if it already points at a JDK 21 (has bin/javac.exe)
#   2. A standalone Temurin/Adoptium JDK 21 under common install locations
#   3. The bundled ./.tools JDK (legacy fallback, if present)
$ErrorActionPreference = "Stop"

function Test-Jdk21($path) {
    if (-not $path) { return $false }
    $javac = Join-Path $path "bin\javac.exe"
    if (-not (Test-Path $javac)) { return $false }
    try { return ((& $javac -version 2>&1) -match "21\.") } catch { return $false }
}

$candidates = @(
    $env:JAVA_HOME,
    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-21.0.9.10-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot",
    (Join-Path $PSScriptRoot ".tools\jdk-21.0.12.1+1")
)

# Also glob for any Adoptium JDK 21 in the standard user/machine locations.
$globRoots = @(
    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
    "C:\Program Files\Eclipse Adoptium"
)
foreach ($root in $globRoots) {
    if (Test-Path $root) {
        Get-ChildItem $root -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
            ForEach-Object { $candidates += $_.FullName }
    }
}

$jdk = $candidates | Where-Object { Test-Jdk21 $_ } | Select-Object -First 1
if (-not $jdk) {
    Write-Error "No JDK 21 found. Install Temurin 21 or set JAVA_HOME. See README."
    exit 1
}

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
mvn @args
