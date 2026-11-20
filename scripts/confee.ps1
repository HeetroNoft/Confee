<#
.SYNOPSIS
    Runs Confee in development, without remembering Gradle flags.

.DESCRIPTION
    Picks a JDK 25 for Gradle (Minecraft 26.1+ needs Gradle itself on Java 25), then starts the requested
    task.

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

# Local secrets (publishing tokens) from .env, see .env.example. A variable already set in the shell wins.
$dotEnv = Join-Path $root '.env'
if (Test-Path $dotEnv) {
    foreach ($line in Get-Content $dotEnv) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $name = $Matches[1]
            $value = $Matches[2] -replace '^"(.*)"$', '$1' -replace "^'(.*)'$", '$1'
            if (-not [Environment]::GetEnvironmentVariable($name)) {
                [Environment]::SetEnvironmentVariable($name, $value)
            }
        }
    }
}

$tasks = [ordered]@{
    'fabric'        = @(':fabric:runTestmodClient', 'Minecraft client on Fabric with Mod Menu and the test mods')
    'forge'         = @(':forge:runTestmodClient', 'Minecraft client on Forge with the test mods')
    'quilt'         = @('quilt:runTestmodClient', 'Minecraft client on Quilt (Quilted Fabric API) with the Fabric release jar')
    'neoforge'      = @(':neoforge:runTestmodClient', 'Minecraft client on NeoForge with the test mods')
    'fabric-server' = @(':fabric:runTestmodServer', 'Fabric dedicated server (checks nothing client-only is loaded)')
    'forge-server'  = @(':forge:runTestmodServer', 'Forge dedicated server (needs eula=true in forge/run-server/eula.txt)')
    'quilt-server'  = @('quilt:runTestmodServer', 'Quilt dedicated server with the Fabric release jar')
    'neoforge-server' = @(':neoforge:runTestmodServer', 'NeoForge dedicated server')
    'shots-fabric'  = @(':fabric:runTestmodScreenshots', 'Automatic screenshots on Fabric -> fabric/run-shots/screenshots')
    'shots-forge'   = @(':forge:runTestmodScreenshots', 'Automatic screenshots on Forge -> forge/run-shots/screenshots')
    'shots-quilt'   = @('quilt:runTestmodScreenshots', 'Automatic screenshots on Quilt -> testenv/quilt/run-shots/screenshots')
    'shots-neoforge' = @(':neoforge:runTestmodScreenshots', 'Automatic screenshots on NeoForge -> neoforge/run-shots/screenshots')
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

# A JDK 25 for Gradle (Minecraft 26.1+ needs Gradle itself on Java 25): one Gradle downloaded, an installed
# Adoptium JDK, else JAVA_HOME
$jdk = $null
$candidates = @((Join-Path $env:USERPROFILE '.gradle\jdks'), (Join-Path $env:ProgramFiles 'Eclipse Adoptium')) | Where-Object { Test-Path $_ }
foreach ($dir in $candidates) {
    $jdk = Get-ChildItem $dir -Directory | Where-Object { $_.Name -match '(^|[-_])25' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } |
        Select-Object -First 1 -ExpandProperty FullName
    if ($jdk) { break }
}
if (-not $jdk -and $env:JAVA_HOME) {
    $jdk = $env:JAVA_HOME
}
if (-not $jdk) {
    Write-Error 'No JDK 25 found. Install one (Adoptium) or set JAVA_HOME to a JDK 25.'
}

$javaHome = "-Dorg.gradle.java.home=$($jdk -replace '\\', '/')"

# Runs one task. "quilt:<task>" is a separate build in testenv/quilt (its Gradle plugin cannot share the main build);
# it first needs the Fabric release jar from the main build.
function Invoke-ConfeeTask([string] $gradleTask) {
    if ($gradleTask -like 'quilt:*') {
        & "$root\gradlew.bat" $javaHome ':fabric:jar' | Out-Host
        if ($LASTEXITCODE -ne 0) { return $LASTEXITCODE }
        & "$root\gradlew.bat" $javaHome '-p' "$root\testenv\quilt" ($gradleTask -replace '^quilt:', '') | Out-Host
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
        foreach ($t in @(':fabric:runTestmodScreenshots', 'quilt:runTestmodScreenshots', ':forge:runTestmodScreenshots', ':neoforge:runTestmodScreenshots')) {
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

if ($Task -eq 'publish-dry' -and $code -eq 0 -and $env:CONFEE_PUBLISH -ne 'true') {
    Write-Host "`nDry run only. To really publish (both sites, both jars): put the tokens in .env (see .env.example)," -ForegroundColor Yellow
    Write-Host '  then run: $env:CONFEE_PUBLISH="true"; .\scripts\confee.ps1 publish-dry'
}

if ($Task -eq 'jars' -and $code -eq 0) {
    $mcVersion = ((Get-Content "$root\gradle.properties") -match '^minecraft_versions=' -replace '^minecraft_versions=', '') -replace ',', ', '
    Write-Host "`nJars to copy into the 'mods' folder of a Minecraft $mcVersion instance:" -ForegroundColor Green
    Get-ChildItem "$root\fabric\build\libs", "$root\neoforge\build\libs", "$root\forge\build\libs" -Filter '*.jar' |
        Where-Object { $_.Name -notmatch 'sources' } |
        ForEach-Object { Write-Host "  $($_.FullName)" }
    Write-Host "`n  Fabric/Quilt: also needs Fabric API (and Mod Menu to see the Config buttons)."
    Write-Host '  NeoForge, Forge: nothing else needed.'
}
exit $code
