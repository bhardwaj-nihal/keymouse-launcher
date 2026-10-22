$ErrorActionPreference = "Stop"
$jbrJava = "C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
if (Test-Path $jbrJava) {
    $javaExe = $jbrJava
} else {
    $javaExe = (Get-Command java.exe).Source
}

Write-Host "==> Compiling KeyMouseLauncher using Gradle..." -ForegroundColor Cyan
& $javaExe "-Dfile.encoding=UTF-8" -cp "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[SUCCESS] Debug APK built successfully!" -ForegroundColor Green
    $apk = Get-ChildItem -Recurse "app\build\outputs\apk\debug" -Filter "*.apk" | Select-Object -First 1
    if ($apk) {
        Write-Host "APK File: $($apk.FullName) ($([math]::Round($apk.Length / 1MB, 2)) MB)" -ForegroundColor Yellow
    }
} else {
    Write-Host "`n[ERROR] Build failed." -ForegroundColor Red
}
