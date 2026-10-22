$adb = "$HOME\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) {
    Write-Host "APK not found. Building first..." -ForegroundColor Yellow
    & .\build.ps1
}

if (-not (Test-Path $adb)) {
    Write-Host "adb.exe not found at $adb" -ForegroundColor Red
    exit 1
}

Write-Host "==> Checking connected devices..." -ForegroundColor Cyan
& $adb devices

Write-Host "`n==> Installing KeyMouseLauncher to device..." -ForegroundColor Cyan
& $adb install -r $apk

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n==> Launching KeyMouse Launcher..." -ForegroundColor Green
    & $adb shell am start -n com.keymouse.launcher/.MainActivity
}
