# Generate web/ dari sumber tunggal app/src/main/assets — hindari 2 salinan HTML yang drift.
# Update: Rab 01/07/2026 - web v1.0.2 (fix favicon 404)
# Jalankan: powershell -ExecutionPolicy Bypass -File web\build-web.ps1
$ErrorActionPreference = 'Stop'
$root   = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root 'app\src\main\assets'
$web    = $PSScriptRoot

$html = Get-Content (Join-Path $assets 'index.html') -Raw -Encoding UTF8

# Suntik mqtt.js (CDN) + shim SEBELUM </head> supaya window.MqttAndroid siap sebelum skrip body.
# favicon data-URI kosong -> browser stop minta /favicon.ico (WebView app tak butuh ini).
$inject = @'
    <link rel="icon" href="data:,">
    <script src="https://unpkg.com/mqtt/dist/mqtt.min.js"></script>
    <script src="mqtt-web.js"></script>
</head>
'@
$html = $html -replace '</head>', $inject

Set-Content -Path (Join-Path $web 'index.html') -Value $html -Encoding UTF8
Copy-Item (Join-Path $assets 'config.js') (Join-Path $web 'config.js') -Force
Write-Host "OK -> web/index.html (+ config.js). Serve via HTTPS/localhost (WSS butuh secure context)."
