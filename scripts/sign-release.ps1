param(
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.moodiary-signing'),
    [string]$UnsignedApk = (Join-Path $PSScriptRoot '../app/build/outputs/apk/release/app-release-unsigned.apk'),
    [string]$OutputApk = (Join-Path $PSScriptRoot '../app/build/outputs/apk/release/Moodiary-v0.2.0.apk')
)
$ErrorActionPreference = 'Stop'
# Build first. Never generate or replace a signing key implicitly.
if (-not $env:ANDROID_HOME) { throw 'Set ANDROID_HOME and JAVA_HOME before signing.' }
$signer = Join-Path $env:ANDROID_HOME 'build-tools/35.0.0/apksigner.bat'
$aligner = Join-Path $env:ANDROID_HOME 'build-tools/35.0.0/zipalign.exe'
$key = Join-Path $SigningDirectory 'moodiary-release.jks'
$passwordFile = Join-Path $SigningDirectory 'release-password.dpapi.xml'
foreach ($path in @($signer, $aligner, $key, $passwordFile, $UnsignedApk)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file missing: $path" }
}
if ([IO.Path]::GetFullPath($UnsignedApk) -eq [IO.Path]::GetFullPath($OutputApk)) {
    throw 'Output must differ from unsigned input.'
}
if (Test-Path -LiteralPath $OutputApk) { throw 'Output already exists; choose a new path to preserve the previous artifact.' }
$credential = Import-Clixml -LiteralPath $passwordFile
if ($credential -isnot [pscredential]) { throw 'Invalid signing credential file.' }
$priorPassword = $env:MOODIARY_RELEASE_STORE_PASS
try {
    $env:MOODIARY_RELEASE_STORE_PASS = $credential.GetNetworkCredential().Password
    & $signer sign --ks $key --ks-key-alias moodiary-release --ks-pass env:MOODIARY_RELEASE_STORE_PASS --key-pass env:MOODIARY_RELEASE_STORE_PASS --out $OutputApk $UnsignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $signer verify --verbose --print-certs $OutputApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    & $aligner -c -P 16 4 $OutputApk
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed.' }
    Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256
} finally {
    $env:MOODIARY_RELEASE_STORE_PASS = $priorPassword
    $credential = $null
}
