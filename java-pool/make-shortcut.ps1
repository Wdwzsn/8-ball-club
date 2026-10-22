# 在桌面创建 "8-BALL CLUB" 快捷方式（图标 assets\8ball.ico）
# 用法： powershell -ExecutionPolicy Bypass -File make-shortcut.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $root "dist\8-ball-club.jar"
if (-not (Test-Path $jar)) {
    Write-Host "dist\8-ball-club.jar not found. Run build.ps1 first." -ForegroundColor Red
    exit 1
}

$jdk = $null
$marker = Join-Path $root ".jdk-home"
if (Test-Path $marker) {
    $jdk = (Get-Content $marker -TotalCount 1).Trim()
}
if (-not $jdk -or -not (Test-Path (Join-Path $jdk "bin\javaw.exe"))) {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javaw.exe"))) {
        $jdk = $env:JAVA_HOME
    }
}
if (-not $jdk) {
    Write-Host "Java runtime not found. Run build.ps1 once to record the JDK path." -ForegroundColor Red
    exit 1
}
$javaw = Join-Path $jdk "bin\javaw.exe"
$desktop = [Environment]::GetFolderPath('Desktop')
$lnkPath = Join-Path $desktop "8-BALL CLUB.lnk"

$ws = New-Object -ComObject WScript.Shell
$lnk = $ws.CreateShortcut($lnkPath)
$lnk.TargetPath = $javaw
$lnk.Arguments = '-jar "' + $jar + '"'
$lnk.WorkingDirectory = $root
$ico = Join-Path $root "assets\8ball.ico"
if (Test-Path $ico) { $lnk.IconLocation = $ico + ",0" }
$lnk.Description = "8-BALL CLUB · 标准八球"
$lnk.WindowStyle = 1
$lnk.Hotkey = "CTRL+ALT+8"
$lnk.Save()

Write-Host "Shortcut created: $lnkPath" -ForegroundColor Green
Write-Host "Target: $javaw -jar `"$jar`""
