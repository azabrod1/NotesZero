param(
  [ValidateSet("test", "clean-test", "run", "package")]
  [string]$Mode = "test",
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$MavenArgs = @()
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Resolve-JavaHome {
  $candidates = @(
    (Join-Path $env:USERPROFILE ".jdks\\openjdk-25.0.2"),
    $env:JAVA_HOME
  ) | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\\java.exe")) }

  if (-not $candidates) {
    throw "JDK 25 not found. Install JDK 25 and set JAVA_HOME."
  }

  return $candidates[0]
}

function Assert-Java25 {
  $versionLine = (& java -version 2>&1 | Select-Object -First 1)
  if ($versionLine -notmatch '\"25(\.|$)') {
    throw "Expected Java 25, got: $versionLine"
  }
}

$javaHome = Resolve-JavaHome
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\\bin;$env:Path"
Assert-Java25

Push-Location $scriptDir
try {
  $baseCommand = switch ($Mode) {
    "test" { @("test") }
    "clean-test" { @("clean", "test") }
    "run" { @("spring-boot:run") }
    "package" { @("clean", "package") }
  }

  & mvn @baseCommand @MavenArgs
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
} finally {
  Pop-Location
}
