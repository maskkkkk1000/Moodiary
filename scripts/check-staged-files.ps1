# Scan the exact staged snapshot without printing matched credentials or user content.
$ErrorActionPreference = 'Stop'
$paths = @(git -c core.quotepath=false diff --cached --name-only --diff-filter=ACMR)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the Git index.' }
$blockedPath = '(^|/)(local\.properties|secrets\.properties|signing\.properties|keystore\.properties|\.env(?:\..*)?|credentials[^/]*\.json|client_secret[^/]*|oauth[^/]*\.json|google-services\.json|token[s]?\.json)$|(^|/)(\.tooling|\.gradle|build|\.idea|\.vscode|verification|photos|backups|exports|user-data|device-data)/|\.(jks|keystore|p12|pfx|pem|key|db|sqlite3?|apk|aab|zip|backup)$'
$secret = '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\bgh[pousr]_[A-Za-z0-9]{30,}|\bgithub_pat_[A-Za-z0-9_]{30,}|\bAIza[A-Za-z0-9_-]{30,}|\bAKIA[A-Z0-9]{16}\b|\bya29\.[A-Za-z0-9_-]{20,}'
$failures = @()
foreach ($path in $paths) {
    if ($path -match $blockedPath) { $failures += "Excluded file type: $path"; continue }
    if ($path -match '\.(kt|kts|xml|json|md|txt|properties|ps1|py|ya?ml|toml)$' -or $path -in @('.gitignore', '.gitattributes')) {
        $content = (git show ":$path") -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Cannot inspect staged file: $path" }
        if ($content -match $secret) { $failures += "Possible credential: $path" }
    }
}
if ($failures.Count) { $failures | Write-Output; throw 'Staged file scan failed; do not commit.' }
Write-Output "Staged scan passed: $($paths.Count) files. Review the full diff for private data and unrecognized secrets as well."
