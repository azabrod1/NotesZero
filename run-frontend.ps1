param(
  [ValidateSet("dev", "build", "typecheck")]
  [string]$Mode = "dev",
  [string]$BindHost = "127.0.0.1",
  [int]$Port = 3000
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontendDir = Join-Path $scriptDir "frontend"

function Get-NodeExecutable {
  $candidates = @(
    (Join-Path $env:LOCALAPPDATA "Microsoft\\WinGet\\Packages\\OpenJS.NodeJS.LTS_Microsoft.Winget.Source_8wekyb3d8bbwe\\node-v24.14.0-win-x64\\node.exe")
  )

  $onPath = Get-Command node -ErrorAction SilentlyContinue
  if ($onPath) {
    $candidates += $onPath.Source
  }

  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path $candidate)) {
      return $candidate
    }
  }

  throw "Node.js not found. Install Node.js LTS (20.19+)."
}

function Assert-NodeVersion([string]$nodeExe) {
  $raw = & $nodeExe -v
  $versionText = ($raw -replace "^v", "").Split("-")[0]
  $version = [Version]$versionText
  $minimum = [Version]"20.19.0"
  if ($version -lt $minimum) {
    throw "Node $versionText is too old. Required: 20.19.0 or newer."
  }
}

$nodeExe = Get-NodeExecutable
Assert-NodeVersion -nodeExe $nodeExe

$nodeDir = Split-Path -Parent $nodeExe
$npmCmd = Join-Path $nodeDir "npm.cmd"

Push-Location $frontendDir
try {
  if (-not (Test-Path "node_modules")) {
    if (-not (Test-Path $npmCmd)) {
      throw "npm.cmd was not found next to $nodeExe. Reinstall Node.js LTS."
    }
    & $npmCmd install
    if ($LASTEXITCODE -ne 0) {
      exit $LASTEXITCODE
    }
  }

  switch ($Mode) {
    "dev" {
      & $nodeExe ".\\node_modules\\vite\\bin\\vite.js" "--host" $BindHost "--port" "$Port"
    }
    "build" {
      & $nodeExe ".\\node_modules\\vite\\bin\\vite.js" "build"
    }
    "typecheck" {
      & $nodeExe ".\\node_modules\\typescript\\bin\\tsc" "--noEmit"
    }
  }

  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
} finally {
  Pop-Location
}
