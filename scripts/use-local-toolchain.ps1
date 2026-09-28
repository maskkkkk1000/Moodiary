# Dot-source from PowerShell: . .\scripts\use-local-toolchain.ps1
# Process-local settings only; this script never changes Windows environment settings.
$moodiaryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$moodiaryTooling = Join-Path $moodiaryRoot '.tooling'
$moodiaryJava = Join-Path $moodiaryTooling 'jdk17\jdk-17.0.20.1+1'
$moodiarySdk = Join-Path $moodiaryTooling 'android-sdk'
$moodiaryGradle = Join-Path $moodiaryTooling 'gradle-8.13\bin'
if (-not (Test-Path -LiteralPath (Join-Path $moodiaryJava 'bin\java.exe'))) {
    throw 'Portable JDK is absent. Set up the toolchain described in docs/TOOLCHAIN.md.'
}
$env:JAVA_HOME = $moodiaryJava
$env:ANDROID_HOME = $moodiarySdk
$env:ANDROID_SDK_ROOT = $moodiarySdk
$env:GRADLE_USER_HOME = Join-Path $moodiaryTooling 'gradle-home'
$env:Path = "$moodiaryJava\bin;$moodiaryGradle;$moodiarySdk\platform-tools;$moodiarySdk\cmdline-tools\latest\bin;$env:Path"
Write-Host "Moodiary toolchain configured for this PowerShell process. JAVA_HOME=$env:JAVA_HOME"
