# Convenience build script: pins JAVA_HOME to the bundled Temurin 21 JDK,
# then runs Maven with whatever goals you pass.
#
#   ./build.ps1 clean verify
#   ./build.ps1 spring-boot:run
#
$ErrorActionPreference = "Stop"
$jdk = Join-Path $PSScriptRoot ".tools\jdk-21.0.12.1+1"
if (-not (Test-Path $jdk)) {
    Write-Error "Bundled JDK not found at $jdk. See README for setup."
    exit 1
}
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
mvn @args
