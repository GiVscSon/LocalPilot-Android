#requires -Version 7
<#
LocalPilot release signing provisioning. RUN ONLY ON THE KEY OWNER'S TRUSTED WINDOWS PC.
This script NEVER uploads a private key to a repository; only GitHub Actions
Secrets receive a Base64-encoded PKCS12 through gh CLI's standard input.
A password and the backup PKCS12 must be kept by the owner.
The existing android-signing GitHub Environment must be protected first.
No repository-scoped signing secrets are created.

Examples:
  pwsh ./setup_triad_signing.ps1 -CreateNew -Environment android-signing
  pwsh ./setup_triad_signing.ps1 -KeystorePath 'D:\Secure\existing-release.p12' -Alias existingAlias
#>
[CmdletBinding()]
param(
  [string]$Repo = 'GiVscSon/LocalPilot-Android',
  [string]$KeystorePath = "$HOME\LocalPilot-PRIVATE-RELEASE.p12",
  [string]$Alias = 'localpilot-triad',
  [string]$Environment = 'android-signing',
  [switch]$CreateNew
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($Repo -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Invalid owner/repository' }
if ($Environment -notmatch '^[A-Za-z0-9._-]{1,80}$') { throw 'Invalid signing environment' }
if ($Alias -notmatch '^[A-Za-z0-9._-]{1,80}$') { throw 'Invalid signing alias' }

$gh = (Get-Command gh -ErrorAction Stop).Source
$keytool = (Get-Command keytool -ErrorAction Stop).Source
& $gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI is not authenticated. Run gh auth login locally.' }
& $gh repo view $Repo --json name --jq .name *> $null
if ($LASTEXITCODE -ne 0) { throw 'No authenticated access to target GitHub repository.' }

# A protected environment must exist before uploading any private key bytes.
# Never fall back to unscoped repository secrets.
& $gh api "repos/$Repo/environments/$Environment" --jq '.name' *> $null
if ($LASTEXITCODE -ne 0) {
  throw "Create and protect GitHub Settings > Environments > $Environment before configuring secrets."
}

$absolute = [System.IO.Path]::GetFullPath($KeystorePath)
if (!(Test-Path -LiteralPath $absolute)) {
  if (!$CreateNew) {
    throw 'The keystore does not exist. Refusing to silently create a new identity. Supply -CreateNew only for a genuinely new signing identity.'
  }
  $folder = Split-Path -Parent $absolute
  if (!(Test-Path -LiteralPath $folder)) { New-Item -ItemType Directory -Force -Path $folder | Out-Null }
  Write-Host 'Generating a NEW release identity. Use your OLD key instead if any installed app uses that package ID.'
  & $keytool -genkeypair -keystore $absolute -storetype PKCS12 -alias $Alias `
    -keyalg RSA -keysize 3072 -validity 11000 -dname 'CN=LocalPilot Release, O=LocalPilot'
  if ($LASTEXITCODE -ne 0) { throw 'Key generation failed' }
}

# Keytool prompts for the keystore password; do not place it on the command line.
$certificate = (& $keytool -list -v -storetype PKCS12 -keystore $absolute -alias $Alias 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read PKCS12 certificate' }
$match = [regex]::Match($certificate, 'SHA256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})')
if (!$match.Success) { throw 'Cannot extract 64-hex certificate SHA-256 from keytool' }
$pin = $match.Groups[1].Value.Replace(':', '').ToLowerInvariant()

function Set-SecretFromMemory([string]$SecretName, [string]$SecretValue) {
  $settings = [System.Diagnostics.ProcessStartInfo]::new()
  $settings.FileName = $gh
  foreach ($arg in @('secret','set',$SecretName,'--repo',$Repo,'--env',$Environment)) { [void]$settings.ArgumentList.Add($arg) }
  $settings.UseShellExecute = $false
  $settings.RedirectStandardInput = $true
  $settings.RedirectStandardError = $true
  $settings.RedirectStandardOutput = $true
  $proc = [System.Diagnostics.Process]::Start($settings)
  try {
    $proc.StandardInput.Write($SecretValue)
    $proc.StandardInput.Close()
    $errorText = $proc.StandardError.ReadToEnd()
    $null = $proc.StandardOutput.ReadToEnd()
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) {
      throw "GitHub could not set secret $SecretName (code $($proc.ExitCode)): $errorText"
    }
  } finally { $proc.Dispose() }
}

$secureStore = Read-Host 'Enter the SAME keystore password (hidden; never logged)' -AsSecureString
$secureKey = Read-Host 'Enter key alias password (normally same; hidden)' -AsSecureString
$store = [System.Net.NetworkCredential]::new('', $secureStore).Password
$key = [System.Net.NetworkCredential]::new('', $secureKey).Password
try {
  if ($store.Length -lt 6 -or $key.Length -lt 6) { throw 'Blank or unexpectedly short signing password' }
  $blob = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($absolute))
  Set-SecretFromMemory 'LOCALPILOT_RELEASE_KEYSTORE_B64' $blob
  Set-SecretFromMemory 'LOCALPILOT_RELEASE_STORE_PASS' $store
  Set-SecretFromMemory 'LOCALPILOT_RELEASE_KEY_PASS' $key
  Set-SecretFromMemory 'LOCALPILOT_RELEASE_KEY_ALIAS' $Alias
} finally {
  $store = $null; $key = $null; $blob = $null
  $secureStore.Dispose(); $secureKey.Dispose()
}
# This digest is PUBLIC; it is safe to store as an Actions variable.
& $gh variable set LOCALPILOT_RELEASE_CERT_SHA256 --repo $Repo --env $Environment --body $pin
if ($LASTEXITCODE -ne 0) { throw 'Could not pin the public signing certificate' }
Write-Host 'Public signing certificate SHA256 pinned:' $pin
Write-Host "Signing secrets are scoped to the protected GitHub environment: $Environment"
Write-Host 'Do not place release signing secrets at repository scope. If old repository-scoped copies exist, remove them in GitHub Settings.'
Write-Host 'Signing secrets are configured. Keep the PKCS12+password backup OFFLINE.'
Write-Host 'Do NOT share the keystore or passwords in chat, Issues or commits.'
Write-Host 'The signed-candidate workflow still requires explicit manual dispatch and physical Android tests.'