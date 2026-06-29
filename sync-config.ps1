<#
.SYNOPSIS
  Sinkronisasi GAS URL dari config.js ke semua file config.h ESP8266.
.DESCRIPTION
  Edit URL di app/src/main/assets/config.js lalu jalankan script ini.
  GAS URL akan otomatis disalin ke semua config.h project PlatformIO.
.EXAMPLE
  .\sync-config.ps1
#>

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

# ============================================================
# LANGKAH 1: Baca GAS URL dari config.js (sumber tunggal)
# ============================================================
$configJsPath = Join-Path $scriptDir "app\src\main\assets\config.js"

if (-not (Test-Path $configJsPath)) {
    Write-Host "ERROR: File config.js tidak ditemukan di: $configJsPath" -ForegroundColor Red
    exit 1
}

$configJs = Get-Content $configJsPath -Raw
$match = [regex]::Match($configJs, "gasUrl:\s*'([^']+)'")
if (-not $match.Success) {
    Write-Host "ERROR: gasUrl tidak ditemukan dalam config.js" -ForegroundColor Red
    exit 1
}
$gasUrl = $match.Groups[1].Value

Write-Host ""
Write-Host "GAS URL ditemukan:" -ForegroundColor Cyan
Write-Host "  $gasUrl" -ForegroundColor White
Write-Host ""

# Pecah URL menjadi 2 baris (prefix + suffix setelah /s/)
$urlMatch = [regex]::Match($gasUrl, "(https://script\.google\.com/macros/s/)([^/]+)(/.+)")
if ($urlMatch.Success) {
    $prefix  = $urlMatch.Groups[1].Value
    $scriptId = $urlMatch.Groups[2].Value
    $suffix  = $urlMatch.Groups[3].Value
    $defineBlock = "#define GAS_URL \`\`n  `"$prefix`" \`\`n  `"$scriptId`" \`\`n  `"$suffix`""
} else {
    $defineBlock = "#define GAS_URL `"$gasUrl`""
}

# Pattern regex untuk menemukan & mengganti blok #define GAS_URL
$gasPattern = '(?s)#define GAS_URL\s+\\?\s*"[^"]+"\s*\\?\s*"[^"]+"\s*\\?\s*"[^"]+"|#define GAS_URL\s+"[^"]+"'

# ============================================================
# LANGKAH 2: Target file config.h yang perlu diperbarui
# ============================================================
$targets = @(
    # Repo ini (SmartSanyo_ESP8266)
    (Join-Path $scriptDir "SmartSanyo_ESP8266\include\config.h"),
    # Project PlatformIO aktif (OneDrive)
    "e:\OneDrive\Documents\PlatformIO\Projects\Smart-Sanyo\include\config.h"
)

$updatedCount = 0

foreach ($target in $targets) {
    if (-not (Test-Path $target)) {
        Write-Host "SKIP: $target (tidak ditemukan)" -ForegroundColor Yellow
        continue
    }

    $content = Get-Content $target -Raw

    # Bangun blok #define baru (multi-line dengan backslash)
    $newDefine = "#define GAS_URL \`n  `"$prefix`" \`n  `"$scriptId`" \`n  `"$suffix`""

    $newContent = [regex]::Replace($content, $gasPattern, $newDefine)

    if ($newContent -ne $content) {
        Set-Content $target $newContent -Encoding utf8 -NoNewline
        Write-Host "UPDATED: $target" -ForegroundColor Green
        $updatedCount++
    } else {
        Write-Host "SAMA   : $target (tidak ada perubahan)" -ForegroundColor Gray
    }
}

Write-Host ""
if ($updatedCount -gt 0) {
    Write-Host "Selesai! $updatedCount file diperbarui." -ForegroundColor Green
    Write-Host "Langkah berikutnya:" -ForegroundColor Cyan
    Write-Host "  1. Upload firmware ESP8266 (PlatformIO)" -ForegroundColor White
    Write-Host "  2. Build & Install Android App (.\run.ps1)" -ForegroundColor White
} else {
    Write-Host "Tidak ada file yang diperbarui." -ForegroundColor Yellow
}
Write-Host ""
