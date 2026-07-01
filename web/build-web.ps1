# Generate web/ dari sumber tunggal app/src/main/assets — hindari 2 salinan HTML yang drift.
# Update: Rab 01/07/2026 - web v1.0.3 (favicon = icon app yang sama)
# Jalankan: powershell -ExecutionPolicy Bypass -File web\build-web.ps1
$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$assets  = Join-Path $root 'app\src\main\assets'
$appIcon = Join-Path $root 'app\src\main\res\drawable\ic_icon_sanyo.png'
$web     = $PSScriptRoot

$html = Get-Content (Join-Path $assets 'index.html') -Raw -Encoding UTF8

# Suntik mqtt.js (CDN) + shim SEBELUM </head> supaya window.MqttAndroid siap sebelum skrip body.
# favicon = icon app yang sama (ic_icon_sanyo.png), disalin ke web/ tiap build.
$inject = @'
    <link rel="icon" type="image/png" href="favicon.png">
    <script src="https://unpkg.com/mqtt/dist/mqtt.min.js"></script>
    <script src="mqtt-web.js"></script>
</head>
'@
$html = $html -replace '</head>', $inject

Set-Content -Path (Join-Path $web 'index.html') -Value $html -Encoding UTF8
Copy-Item (Join-Path $assets 'config.js') (Join-Path $web 'config.js') -Force
Copy-Item $appIcon (Join-Path $web 'favicon.png') -Force
Write-Host "OK -> web/index.html (+ config.js, favicon.png). Serve via HTTPS/localhost (WSS butuh secure context)."
