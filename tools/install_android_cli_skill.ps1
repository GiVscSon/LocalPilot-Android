# Install/update the official Google Android CLI skill for coding agents
# Only configures agent skills. It does not build an APK or change an attached device.
[CmdletBinding()]
param(
    [ValidateSet("codex", "gemini", "claude")]
    [string[]] $Agents = @("codex", "gemini")
)
$ErrorActionPreference = "Stop"
$androidCommand = Get-Command "android" -ErrorAction SilentlyContinue
if (-not $androidCommand) {
    throw "Android CLI not found on PATH. Install from https://developer.android.com/tools/agents/android-cli and rerun."
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$agentList = $Agents -join ","
Write-Host "Android CLI: project=$repositoryRoot; agents=$agentList"

& $androidCommand.Source --version
if ($LASTEXITCODE -ne 0) { throw "android --version failed" }

& $androidCommand.Source skills add android-cli "--agent=$agentList" "--project=$repositoryRoot"
if ($LASTEXITCODE -ne 0) { throw "android skills add failed" }

& $androidCommand.Source skills list --long
if ($LASTEXITCODE -ne 0) { throw "android skills list failed" }

Write-Host "Android CLI skill installation complete for: $agentList"
Write-Host "Skill availability is not proof that the SDK, ADB, emulator, or physical phone is connected."
