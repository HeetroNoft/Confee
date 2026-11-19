<#
.SYNOPSIS
    Runs Confee in development, without remembering Gradle flags.

.DESCRIPTION
    Picks a JDK 17 for Gradle (your global ~/.gradle/gradle.properties points to a JDK 25 that Gradle 8.8
    cannot run), then starts the requested task.

.EXAMPLE
    .\scripts\confee.ps1 fabric        # Minecraft client, Fabric + Mod Menu + test mods
    .\scripts\confee.ps1 forge         # Minecraft client, Forge + test mods
    .\scripts\confee.ps1 jars          # builds the jars to drop into your own mods folder
    .\scripts\confee.ps1 shots-all     # screenshots on Fabric, Quilt, Forge and NeoForge
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('fabric', 'forge', 'quilt', 'neoforge', 'fabric-server', 'forge-server', 'quilt-server', 'neoforge-server',
            'shots-fabric', 'shots-forge', 'shots-quilt', 'shots-neoforge', 'shots-all', 'test', 'build', 'jars', 'publish-dry', 'help')]
    [string] $Task = 'help'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$tasks = [ordered]@{
    'fabric'        = @(':fabric:runTestmodClient', 'Minecraft client on Fabric with Mod Menu and the test mods')
    'forge'         = @(':forge:runTestmodClient', 'Minecraft client on Forge with the test mods')
    'quilt'         = @('quilt:runTestmodClient', 'Minecraft client on Quilt (Quilted Fabric API) with the Fabric release jar')
    'neoforge'      = @('neoforge:runTestmodClient', 'Minecraft client on NeoForge 1.20.1 with the test mods')
    'fabric-server' = @(':fabric:runTestmodServer', 'Fabric dedicated server (checks nothing client-only is loaded)')
    'forge-server'  = @(':forge:runTestmodServer', 'Forge dedicated server (needs eula=true in forge/run-server/eula.txt)')
    'quilt-server'  = @('quilt:runTestmodServer', 'Quilt dedicated server with the Fabric release jar')
    'neoforge-server' = @('neoforge:runTestmodServer', 'NeoForge dedicated server')
    'shots-fabric'  = @(':fabric:runTestmodScreenshots', 'Automatic screenshots on Fabric -> fabric/run-shots/screenshots')
    'shots-forge'   = @(':forge:runTestmodScreenshots', 'Automatic screenshots on Forge -> forge/run-shots/screenshots')
    'shots-quilt'   = @('quilt:runTestmodScreenshots', 'Automatic screenshots on Quilt -> testenv/quilt/run-shots/screenshots')
    'shots-neoforge' = @('neoforge:runTestmodScreenshots', 'Automatic screenshots on NeoForge -> testenv/neoforge/run-shots/screenshots')
    'shots-all'     = @('all', 'Automatic screenshots on the four loaders, one after the other')
    'test'          = @(':common:test', 'Unit tests')
    'build'         = @('build', 'Full build with unit tests')
    'jars'          = @('build', 'Builds the jars for your own Minecraft instance')
    'publish-dry'   = @('publishMods', 'Shows what would be published to Modrinth/CurseForge, publishes nothing')
}

if ($Task -eq 'help') {
    Write-Host "Usage: .\scripts\confee.ps1 <task>`n"
    foreach ($name in $tasks.Keys) {
        Write-Host ("  {0,-16} {1}" -f $name, $tasks[$name][1])
    }
    exit 0
}

# A JDK 17 for Gradle: the one Gradle downloaded itself, else JAVA_HOME
$jdk = $null
$jdks = Join-Path $env:USERPROFILE '.gradle\jdks'
if (Test-Path $jdks) {
    $jdk = Get-ChildItem $jdks -Directory | Where-Object { $_.Name -match '17' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk -and $env:JAVA_HOME) {
    $jdk = $env:JAVA_HOME
}
if (-not $jdk) {
    Write-Error 'No JDK 17 found. Run any Gradle task once so the toolchain downloads it, or set JAVA_HOME to a JDK 17 or 21.'
}

$javaHome = "-Dorg.gradle.java.home=$($jdk -replace '\\', '/')"

# Runs one task. "quilt:<task>" and "neoforge:<task>" are separate builds in testenv/ (their Gradle plugins cannot
# share the main build); Quilt first needs the Fabric release jar from the main build.
function Invoke-ConfeeTask([string] $gradleTask) {
    if ($gradleTask -like 'quilt:*') {
        & "$root\gradlew.bat" $javaHome ':fabric:remapJar' | Out-Host
        if ($LASTEXITCODE -ne 0) { return $LASTEXITCODE }
        & "$root\gradlew.bat" $javaHome '-p' "$root\testenv\quilt" ($gradleTask -replace '^quilt:', '') | Out-Host
        return $LASTEXITCODE
    }
    if ($gradleTask -like 'neoforge:*') {
        # own wrapper: NeoGradle 6 needs Gradle 8.1
        & "$root\testenv\neoforge\gradlew.bat" $javaHome '-p' "$root\testenv\neoforge" ($gradleTask -replace '^neoforge:', '') | Out-Host
        return $LASTEXITCODE
    }
    & "$root\gradlew.bat" $javaHome $gradleTask | Out-Host
    return $LASTEXITCODE
}

$gradleTask = $tasks[$Task][0]
Write-Host "Confee: $($tasks[$Task][1])" -ForegroundColor Green
Write-Host "        $gradleTask (JDK: $jdk)`n"

Push-Location $root
try {
    if ($gradleTask -eq 'all') {
        $code = 0
        foreach ($t in @(':fabric:runTestmodScreenshots', 'quilt:runTestmodScreenshots', ':forge:runTestmodScreenshots', 'neoforge:runTestmodScreenshots')) {
            Write-Host "`n== $t" -ForegroundColor Green
            $result = Invoke-ConfeeTask $t
            if ($result -ne 0) {
                Write-Host "   failed ($result)" -ForegroundColor Red
                $code = $result
            }
        }
    } else {
        $code = Invoke-ConfeeTask $gradleTask
    }
} finally {
    Pop-Location
}

if ($Task -eq 'publish-dry' -and $code -eq 0) {
    Write-Host "`nDry run only. To really publish (both sites, both jars):" -ForegroundColor Yellow
    Write-Host '  $env:MODRINTH_TOKEN="..."'
    Write-Host '  $env:CURSEFORGE_TOKEN="..."'
    Write-Host '  $env:CONFEE_PUBLISH="true"; .\scripts\confee.ps1 publish-dry'
}

if ($Task -eq 'jars' -and $code -eq 0) {
    Write-Host "`nJars to copy into the 'mods' folder of a Minecraft 1.20.1 instance:" -ForegroundColor Green
    Get-ChildItem "$root\fabric\build\libs", "$root\forge\build\libs" -Filter '*.jar' |
        Where-Object { $_.Name -notmatch 'sources' } |
        ForEach-Object { Write-Host "  $($_.FullName)" }
    Write-Host "`n  Fabric/Quilt: also needs Fabric API (and Mod Menu to see the Config buttons)."
    Write-Host '  Forge/NeoForge 1.20.1: nothing else needed.'
}
exit $code
