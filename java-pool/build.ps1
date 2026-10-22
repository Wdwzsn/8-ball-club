# 8-BALL CLUB build script
# Usage:  powershell -ExecutionPolicy Bypass -File build.ps1
# Optional: -Jdk "C:\path\to\jdk"   -Test
param(
    [string]$Jdk = "",
    [switch]$Test
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Find-Javac {
    param([string]$hint)
    $cands = New-Object System.Collections.Generic.List[string]
    if ($hint) {
        $cands.Add((Join-Path $hint "bin\javac.exe"))
        $cands.Add((Join-Path $hint "javac.exe"))
    }
    if ($env:JAVA_HOME) { $cands.Add((Join-Path $env:JAVA_HOME "bin\javac.exe")) }
    $onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($onPath) { $cands.Add($onPath.Source) }
    $patterns = @(
        "C:\Program Files\Java\*\bin\javac.exe",
        "C:\Program Files\Eclipse Adoptium\*\bin\javac.exe",
        "C:\Program Files\Microsoft\jdk*\bin\javac.exe",
        "C:\Program Files\Zulu\*\bin\javac.exe",
        "C:\Program Files\BellSoft\*\bin\javac.exe",
        "C:\Program Files\Amazon Corretto\*\bin\javac.exe",
        "C:\Program Files\JetBrains\*\jbr\bin\javac.exe",
        "$env:LOCALAPPDATA\Programs\*\jbr\bin\javac.exe",
        "D:\*\jbr\bin\javac.exe",
        "D:\*\*\jbr\bin\javac.exe",
        "$env:USERPROFILE\.jdks\*\bin\javac.exe",
        "$env:USERPROFILE\scoop\apps\openjdk\current\bin\javac.exe"
    )
    foreach ($p in $patterns) {
        Get-ChildItem $p -ErrorAction SilentlyContinue | ForEach-Object { $cands.Add($_.FullName) }
    }
    foreach ($c in $cands) {
        if ($c -and (Test-Path $c)) { return (Resolve-Path $c).Path }
    }
    return $null
}

$javac = Find-Javac $Jdk
if (-not $javac) {
    Write-Host "[ERROR] No javac.exe found. Install JDK 17+ (https://adoptium.net) or pass -Jdk <path>." -ForegroundColor Red
    exit 1
}
$jdkHome = Split-Path (Split-Path $javac -Parent) -Parent
$java = Join-Path $jdkHome "bin\java.exe"
$jar = Join-Path $jdkHome "bin\jar.exe"
Write-Host "JDK : $jdkHome"

$src = Join-Path $root "src\main\java"
$outDir = Join-Path $root "out\classes"
$dist = Join-Path $root "dist"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force $outDir | Out-Null
New-Item -ItemType Directory -Force $dist | Out-Null

$files = Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "Compiling $($files.Count) source files ..."
& $javac -encoding UTF-8 --release 17 -d $outDir $files
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

# 开发/测试入口不打包进发行 jar（保留在 out/classes 以便运行冒烟测试）
$stage = Join-Path $root "out\jar"
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
Copy-Item -Recurse $outDir $stage
$dev = @("DebugMain", "BallSheet", "UiSmokeTest", "NetSmokeTest", "InputSmokeTest")
foreach ($d in $dev) {
    Get-ChildItem -Path $stage -Recurse -Filter "$d*.class" -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

$jarPath = Join-Path $dist "8-ball-club.jar"
if (Test-Path $jarPath) { Remove-Item -Force $jarPath }
$jarTool = Join-Path $jdkHome "bin\jar.exe"
if (Test-Path $jarTool) {
    & $jarTool --create --file $jarPath --main-class com.pool8.Main -C $stage .
} else {
    & $java -cp $stage com.pool8.tools.JarBuilder $jarPath $stage com.pool8.Main
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] jar packaging failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
Set-Content -Path (Join-Path $root ".jdk-home") -Value $jdkHome -Encoding ASCII
Write-Host ("Built: {0} ({1:N1} KB)" -f $jarPath, ((Get-Item $jarPath).Length / 1KB)) -ForegroundColor Green

if ($Test) {
    Write-Host "Running self tests ..."
    $env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
    & $java -cp $jarPath com.pool8.SelfTest
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    exit $LASTEXITCODE
}
Write-Host "Done. Double-click run.bat to play (or: java -jar dist\8-ball-club.jar)"
