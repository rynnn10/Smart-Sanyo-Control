<#
.SYNOPSIS
  Build, install and launch Smart-Sanyo-Control app on Android device.
.DESCRIPTION
  Script ini akan:
  1. Mengecek IP WiFi PC/laptop dan mencari IP HP di jaringan yang sama (port 5555)
  2. Jika tidak ada -DeviceId, auto-scan network untuk menemukan HP
  3. Konfirmasi di setiap langkah eksekusi
  4. Build, install, dan jalankan aplikasi di HP
.PARAMETER DeviceId
  IP:Port HP (contoh: 192.168.1.7:5555). Jika kosong, script akan auto-scan.
.PARAMETER PackageName
  Package name aplikasi (default: com.aistudio.smartsanyocontrol.jhfxqa)
.PARAMETER Gradle
  Path ke gradlew.bat
.EXAMPLE
  .\run.ps1                           # Auto-scan & install
  .\run.ps1 -DeviceId 192.168.1.7:5555  # Manual IP
#>

param(
    [string]$DeviceId = "",
    [string]$PackageName = "com.aistudio.smartsanyocontrol.jhfxqa",
    [string]$Gradle = ".\\gradlew.bat"
)

# ============================================================
# FUNGSI: Mendapatkan IP WiFi lokal PC
# ============================================================
function Get-LocalWiFiIP {
    try {
        $wifi = Get-NetAdapter | Where-Object { $_.Status -eq "Up" -and $_.Name -like "*Wi-Fi*" -or $_.Name -like "*Wireless*" -or $_.Name -like "*WLAN*" }
        if (-not $wifi) {
            $wifi = Get-NetAdapter | Where-Object { $_.Status -eq "Up" }
        }
        if ($wifi) {
            $ip = Get-NetIPAddress -InterfaceIndex $wifi[0].ifIndex -AddressFamily IPv4 | Select-Object -First 1
            if ($ip) { return $ip.IPAddress }
        }
    } catch {
        $ipconfig = ipconfig | Select-String "IPv4" | Select-String "192\.168" | Select-Object -First 1
        if ($ipconfig) {
            return ($ipconfig -split ":")[1].Trim()
        }
    }
    return $null
}

# ============================================================
# FUNGSI: Cek apakah port 5555 terbuka di IP tertentu
# ============================================================
function Test-Port5555 {
    param([string]$IpAddress)
    try {
        $socket = New-Object System.Net.Sockets.TcpClient
        $asyncResult = $socket.BeginConnect($IpAddress, 5555, $null, $null)
        $wait = $asyncResult.AsyncWaitHandle.WaitOne(300, $false)
        if ($wait -and $socket.Connected) {
            $socket.EndConnect($asyncResult)
            $socket.Close()
            return $true
        }
        $socket.Close()
    } catch {}
    return $false
}

# ============================================================
# FUNGSI: Scan jaringan lokal dengan ARP + port scan cepat
# ============================================================
function Find-AndroidDevicesOnNetwork {
    param([string]$Subnet)
    
    Write-Host ""
    Write-Host ("Memindai jaringan " + $Subnet + ".0/24 untuk perangkat Android...") -ForegroundColor Cyan
    Write-Host "   (Menggunakan ARP cache untuk deteksi cepat)" -ForegroundColor Yellow
    Write-Host ""
    
    # Metode 1: Ambil daftar IP dari ARP cache (instan)
    Write-Host "   Membaca ARP cache..." -ForegroundColor Cyan
    $arpOutput = arp -a | Select-String "^\s+\d+\.\d+\.\d+\.\d+" | ForEach-Object {
        $line = $_ -split '\s+'
        if ($line[1] -match "^\d+\.\d+\.\d+\.\d+$" -and $line[1] -ne "224.0.0.0" -and $line[1] -ne "239.255.255.250") {
            $line[1]
        }
    } | Where-Object { $_ -match "^$Subnet\." } | Select-Object -Unique

    if ($arpOutput.Count -gt 0) {
        Write-Host ("   Ditemukan " + $arpOutput.Count + " perangkat di ARP cache. Memeriksa port ADB (5555)...") -ForegroundColor Green
    } else {
        Write-Host "   Tidak ada perangkat di ARP cache. Mencoba scan subnet..." -ForegroundColor Yellow
        
        # Metode 2: Scan ping cepat (hanya ping IP yang sering dipakai)
        $commonIPs = @("1", "100", "101", "102", "103", "104", "105", "106", "107", "108", "109",
                       "110", "111", "112", "113", "114", "115", "116", "117", "118", "119",
                       "120", "121", "122", "123", "124", "125", "126", "127", "128", "129",
                       "130", "131", "132", "133", "134", "135", "136", "137", "138", "139",
                       "140", "141", "142", "143", "144", "145", "146", "147", "148", "149",
                       "150", "151", "152", "153", "154", "155", "156", "157", "158", "159",
                       "160", "161", "162", "163", "164", "165", "166", "167", "168", "169",
                       "170", "171", "172", "173", "174", "175", "176", "177", "178", "179",
                       "180", "181", "182", "183", "184", "185", "186", "187", "188", "189",
                       "190", "191", "192", "193", "194", "195", "196", "197", "198", "199",
                       "200", "201", "202", "203", "204", "205", "206", "207", "208", "209",
                       "210", "211", "212", "213", "214", "215", "216", "217", "218", "219",
                       "220", "221", "222", "223", "224", "225", "226", "227", "228", "229",
                       "230", "231", "232", "233", "234", "235", "236", "237", "238", "239",
                       "240", "241", "242", "243", "244", "245", "246", "247", "248", "249",
                       "250", "251", "252", "253", "254")
        $pingTasks = @()
        for ($i = 1; $i -le 254; $i++) {
            $ip = $Subnet + '.' + $i
            $pingTasks += (Start-Job -ScriptBlock {
                param($ip)
                $ping = Test-Connection -ComputerName $ip -Count 1 -Quiet -ErrorAction SilentlyContinue
                if ($ping) { return $ip }
                return $null
            } -ArgumentList $ip)
        }
        
        Write-Host "   Menunggu hasil ping (254 host)..." -ForegroundColor Yellow
        $pingResults = $pingTasks | Wait-Job -Timeout 30 | Receive-Job
        $pingTasks | Remove-Job
        
        $arpOutput = $pingResults | Where-Object { $_ -ne $null }
        if ($arpOutput.Count -gt 0) {
            Write-Host ("   Ditemukan " + $arpOutput.Count + " perangkat aktif. Memeriksa port ADB (5555)...") -ForegroundColor Green
        } else {
            Write-Host "   Tidak ada perangkat aktif ditemukan." -ForegroundColor Yellow
            return @()
        }
    }
    
    # Cek port 5555 di setiap IP (synchronous, lebih cepat untuk jumlah sedikit)
    $foundIPs = @()
    $checked = 0
    foreach ($ip in $arpOutput) {
        $checked++
        Write-Host ("   Cek " + $ip + ":5555...") -ForegroundColor Gray -NoNewline
        if (Test-Port5555 -IpAddress $ip) {
            Write-Host " [TERBUKA]" -ForegroundColor Green
            $foundIPs += $ip
        } else {
            Write-Host " [TERTUTUP]" -ForegroundColor DarkGray
        }
    }
    
    if ($foundIPs.Count -gt 0) {
        Write-Host ""
        Write-Host "   Menemukan perangkat Android dengan ADB aktif:" -ForegroundColor Green
        foreach ($ip in $foundIPs) {
            Write-Host ("       -> " + $ip + ":5555") -ForegroundColor Green
        }
    } else {
        Write-Host ""
        Write-Host "   Tidak menemukan perangkat dengan port ADB (5555) terbuka." -ForegroundColor Yellow
        Write-Host "   Pastikan Wireless Debugging sudah diaktifkan di HP:" -ForegroundColor Yellow
        Write-Host "   Setelan > Opsi Pengembang > Wireless Debugging > Aktifkan" -ForegroundColor Yellow
    }
    
    return $foundIPs
}

# ============================================================
# FUNGSI: Konfirmasi dari user
# ============================================================
function Confirm-Step {
    param([string]$Message, [string]$Action = "Lanjutkan")
    
    Write-Host ""
    Write-Host "==================== KONFIRMASI ====================" -ForegroundColor Magenta
    Write-Host "  $Message" -ForegroundColor White
    Write-Host "  Tekan ENTER untuk $Action" -ForegroundColor Cyan
    Write-Host "  Ketik 'b' atau 'BATAL' lalu ENTER untuk membatalkan" -ForegroundColor Red
    Write-Host "====================================================" -ForegroundColor Magenta
    Write-Host ""
    
    $input = Read-Host ">> "
    if ($input -eq "b" -or $input -eq "B" -or $input -eq "BATAL" -or $input -eq "batal") {
        Write-Host ""
        Write-Host "DIBATALKAN oleh pengguna." -ForegroundColor Red
        exit 0
    }
}

# ============================================================
# FUNGSI: Hubungkan ke device via ADB
# ============================================================
function Connect-Device {
    param([string]$TargetDeviceId)
    
    Write-Host ""
    Write-Host ('Menghubungkan ke perangkat: ' + $TargetDeviceId + ' ...') -ForegroundColor Cyan
    $result = & adb connect $TargetDeviceId 2>&1
    Write-Host "   $result" -ForegroundColor Gray
    Start-Sleep -Seconds 2
    
    $devices = & adb devices
    if ($devices -match "$TargetDeviceId\s+device") {
        Write-Host ('   Berhasil terhubung ke ' + $TargetDeviceId) -ForegroundColor Green
        return $true
    } else {
        Write-Host ('   Gagal terhubung ke ' + $TargetDeviceId) -ForegroundColor Red
        return $false
    }
}

# ============================================================
# FUNGSI: Konfirmasi dan hapus build folder
# ============================================================
function Clear-BuildFolder {
    $appBuild = (Get-Location).Path + "\app\build"
    
    if (Test-Path $appBuild) {
        Confirm-Step -Message "Hapus folder build lama untuk mencegah file terkunci?" -Action "HAPUS"
        Write-Host ('Menghapus ' + $appBuild + ' ...') -ForegroundColor Cyan
        try {
            Remove-Item -LiteralPath $appBuild -Recurse -Force -ErrorAction Stop
            Write-Host "   Folder build berhasil dihapus." -ForegroundColor Green
        } catch {
            Write-Host ('   Gagal menghapus folder build: ' + $_.Exception.Message) -ForegroundColor Yellow
            Write-Host "   Coba tutup Android Studio / file explorer, lalu ulangi." -ForegroundColor Yellow
        }
    } else {
        Write-Host "   Folder build tidak ditemukan (bersih)." -ForegroundColor Gray
    }
}

# ============================================================
# FUNGSI: Hentikan Gradle daemon
# ============================================================
function Stop-GradleDaemon {
    Write-Host ""
    Write-Host "Menghentikan Gradle daemon..." -ForegroundColor Cyan
    try {
        & $Gradle --stop 2>&1 | Out-Null
        Write-Host "   Gradle daemon dihentikan." -ForegroundColor Green
    } catch {
        Write-Host ('   Gagal menghentikan Gradle daemon: ' + $_.Exception.Message) -ForegroundColor Yellow
    }
}

# ============================================================
# FUNGSI: Uninstall aplikasi lama
# ============================================================
function Uninstall-OldApp {
    param([string]$TargetDeviceId)
    
    Write-Host ""
    Write-Host "Menghapus versi aplikasi lama di HP..." -ForegroundColor Cyan
    Write-Host ('   Package: ' + $PackageName) -ForegroundColor Gray
    
    Confirm-Step -Message "Hapus aplikasi lama dari HP?" -Action "UNINSTALL"
    
    if ([string]::IsNullOrWhiteSpace($TargetDeviceId)) {
        & adb uninstall "$PackageName" 2>&1 | Out-Null
        & adb uninstall "$PackageName.debug" 2>&1 | Out-Null
    } else {
        & adb -s $TargetDeviceId uninstall "$PackageName" 2>&1 | Out-Null
        & adb -s $TargetDeviceId uninstall "$PackageName.debug" 2>&1 | Out-Null
    }
    Write-Host "   Selesai." -ForegroundColor Green
}

# ============================================================
# FUNGSI: Jalankan Gradle install
# ============================================================
function Invoke-GradleInstall {
    Write-Host ""
    Write-Host "Membangun (build) dan menginstall aplikasi..." -ForegroundColor Cyan
    Write-Host '   Perintah: gradlew.bat :app:installDebug --no-daemon' -ForegroundColor Gray
    
    Confirm-Step -Message "Proses build & install akan memakan waktu beberapa menit." -Action 'BUILD and INSTALL'
    
    Write-Host "Sedang membangun aplikasi. Harap tunggu..." -ForegroundColor Yellow
    Write-Host ""
    
    $process = Start-Process -FilePath "cmd.exe" -ArgumentList "/c gradlew.bat :app:installDebug --no-daemon" -Wait -PassThru -NoNewWindow
    return $process.ExitCode
}

# ============================================================
# FUNGSI: Buka aplikasi di HP
# ============================================================
function Start-AppOnDevice {
    param([string]$TargetDeviceId)
    
    $Activity = "$PackageName/com.example.MainActivity"
    Write-Host ""
    Write-Host "Membuka aplikasi di HP..." -ForegroundColor Cyan
    
    try {
        if ([string]::IsNullOrWhiteSpace($TargetDeviceId)) {
            Start-Process -FilePath "adb" -ArgumentList "shell", "am", "start", "-n", $Activity -WindowStyle Hidden
        } else {
            Start-Process -FilePath "adb" -ArgumentList "-s", $TargetDeviceId, "shell", "am", "start", "-n", $Activity -WindowStyle Hidden
        }
        Write-Host "   Aplikasi terbuka di HP!" -ForegroundColor Green
        [System.Console]::Beep(1000, 500)
    } catch {
        Write-Host ('   Gagal membuka aplikasi otomatis: ' + $_.Exception.Message) -ForegroundColor Yellow
    }
}

# ============================================================
# MAIN SCRIPT
# ============================================================

Clear-Host
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "      SMART-SANYO-CONTROL INSTALLER" -ForegroundColor Cyan
Write-Host "  Build & Install Aplikasi ke HP Android" -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# LANGKAH 1: DETEKSI IP WIFI PC
# ============================================================
Write-Host "==================== LANGKAH 1: DETEKSI JARINGAN ====================" -ForegroundColor Magenta

$localIP = Get-LocalWiFiIP
if ($localIP) {
    Write-Host ('   IP WiFi PC/Laptop: ' + $localIP) -ForegroundColor Green
    $subnetParts = $localIP -split "\."
    $subnet = $subnetParts[0] + '.' + $subnetParts[1] + '.' + $subnetParts[2]
    Write-Host ('   Subnet jaringan: ' + $subnet + '.0/24') -ForegroundColor Gray
} else {
    Write-Host "   Tidak dapat mendeteksi IP WiFi." -ForegroundColor Yellow
    Write-Host "   Pastikan PC/Laptop terhubung ke WiFi." -ForegroundColor Yellow
}

# ============================================================
# LANGKAH 2: DAPATKAN DEVICE ID
# ============================================================
Write-Host ""
Write-Host "==================== LANGKAH 2: DETEKSI PERANGKAT ANDROID ====================" -ForegroundColor Magenta

$finalDeviceId = $DeviceId

# Cek dulu apakah sudah ada device terhubung via ADB (USB atau sebelumnya)
Write-Host "   Memeriksa perangkat yang sudah terhubung via ADB..." -ForegroundColor Cyan
$existingDevices = & adb devices
$existingDeviceLines = $existingDevices | Select-String "device$" | ForEach-Object { $_ -replace '\s+device', '' } | Where-Object { $_ -ne "List of devices attached" -and $_ -ne "" }
if ($existingDeviceLines.Count -gt 0) {
    Write-Host ("   Ditemukan perangkat yang sudah terhubung: " + ($existingDeviceLines -join ", ")) -ForegroundColor Green
    if ([string]::IsNullOrWhiteSpace($finalDeviceId)) {
        Write-Host "   Menggunakan perangkat yang sudah terhubung." -ForegroundColor Cyan
        if ($existingDeviceLines.Count -eq 1) {
            $finalDeviceId = $existingDeviceLines[0]
        } else {
            Write-Host "   Pilih perangkat:" -ForegroundColor Yellow
            for ($i = 0; $i -lt $existingDeviceLines.Count; $i++) {
                Write-Host ('       [' + ($i+1) + '] ' + $existingDeviceLines[$i]) -ForegroundColor White
            }
            $selection = Read-Host ">> Pilih nomor (1-$($existingDeviceLines.Count))"
            $idx = [int]$selection - 1
            if ($idx -ge 0 -and $idx -lt $existingDeviceLines.Count) {
                $finalDeviceId = $existingDeviceLines[$idx]
            }
        }
    }
}

# Kalau belum ada device dan perlu scan
if ([string]::IsNullOrWhiteSpace($finalDeviceId) -and $localIP) {
    Write-Host "   Mode auto-scan (tidak ada -DeviceId, tidak ada device terhubung)." -ForegroundColor Cyan
    Write-Host "   PASTIKAN Wireless Debugging AKTIF di HP dan HP di jaringan SAMA." -ForegroundColor Yellow
    Write-Host "   Setelan > Opsi Pengembang > Wireless Debugging > AKTIFKAN" -ForegroundColor Yellow
    Write-Host "   (Pairing code hanya perlu sekali, selanjutnya langsung connect)" -ForegroundColor Yellow
    Write-Host ""
    
    Confirm-Step -Message "Mulai memindai jaringan untuk perangkat Android?" -Action "SCAN"
    
    $foundDevices = @(Find-AndroidDevicesOnNetwork -Subnet $subnet)
    
    if ($foundDevices.Count -eq 1) {
        $finalDeviceId = $foundDevices[0] + ':5555'
        Write-Host ""
        Write-Host ('   Perangkat terdeteksi: ' + $finalDeviceId) -ForegroundColor Green
    }
    elseif ($foundDevices.Count -gt 1) {
        Write-Host ""
        Write-Host "   Ditemukan beberapa perangkat. Pilih salah satu:" -ForegroundColor Yellow
        for ($i = 0; $i -lt $foundDevices.Count; $i++) {
            Write-Host ('       [' + ($i+1) + '] ' + $foundDevices[$i] + ':5555') -ForegroundColor White
        }
        Write-Host ""
        $selection = Read-Host ">> Pilih nomor (1-$($foundDevices.Count))"
        $idx = [int]$selection - 1
        if ($idx -ge 0 -and $idx -lt $foundDevices.Count) {
            $finalDeviceId = $foundDevices[$idx] + ':5555'
        } else {
            Write-Host "   Pilihan tidak valid." -ForegroundColor Red
            exit 1
        }
    }
    else {
        Write-Host ""
        Write-Host "   Tidak dapat menemukan HP secara otomatis." -ForegroundColor Red
        Write-Host ""
        Write-Host "   Silahkan lakukan salah satu:" -ForegroundColor Yellow
        Write-Host "   1. Colok HP via USB (USB Debugging harus aktif)" -ForegroundColor White
        Write-Host "   2. Jalankan ulang dengan IP manual:" -ForegroundColor White
        Write-Host "      .\run.ps1 -DeviceId 192.168.x.x:5555" -ForegroundColor Cyan
        Write-Host "   3. Pastikan Wireless Debugging sudah AKTIF di HP" -ForegroundColor White
        Write-Host ""
        exit 1
    }
}

# Hubungkan ke device jika menggunakan WiFi
if (-not [string]::IsNullOrWhiteSpace($finalDeviceId) -and $finalDeviceId -match ":5555$") {
    $connected = Connect-Device -TargetDeviceId $finalDeviceId
    if (-not $connected) {
        Write-Host "Gagal terhubung ke perangkat. Periksa kembali." -ForegroundColor Red
        exit 1
    }
}

# Verifikasi ADB
Write-Host ""
Write-Host "Memeriksa koneksi ADB..." -ForegroundColor Cyan
$connectedDevices = & adb devices
if ($connectedDevices -match "\bdevice\b") {
    Write-Host "   Perangkat terdeteksi oleh ADB!" -ForegroundColor Green
} else {
    Write-Host "   GAGAL: Tidak ada perangkat Android yang terdeteksi!" -ForegroundColor Red
    Write-Host "   Pastikan:" -ForegroundColor Yellow
    Write-Host "   - USB Debugging atau Wireless Debugging AKTIF di HP" -ForegroundColor Yellow
    Write-Host "   - HP terhubung ke jaringan yang sama (jika WiFi)" -ForegroundColor Yellow
    Write-Host "   - Kabel USB berfungsi (jika pakai USB)" -ForegroundColor Yellow
    exit 1
}

# ============================================================
# LANGKAH 3: PERSIAPAN BUILD
# ============================================================
Write-Host ""
Write-Host "==================== LANGKAH 3: PERSIAPAN BUILD ====================" -ForegroundColor Magenta

Stop-GradleDaemon
Clear-BuildFolder
Uninstall-OldApp -TargetDeviceId $finalDeviceId

# ============================================================
# LANGKAH 4: BUILD & INSTALL
# ============================================================
Write-Host ""
Write-Host "==================== LANGKAH 4: BUILD and INSTALL ====================" -ForegroundColor Magenta

$buildSuccess = Invoke-GradleInstall

# ============================================================
# LANGKAH 5: SELESAI
# ============================================================
if ($buildSuccess -eq 0) {
    Write-Host ""
    Write-Host "====================================================" -ForegroundColor Green
    Write-Host "   BUILD and INSTALL BERHASIL!" -ForegroundColor Green
    Write-Host "====================================================" -ForegroundColor Green
    
    Start-AppOnDevice -TargetDeviceId $finalDeviceId
    
    Write-Host ""
    Write-Host "Selesai! Anda dapat menutup terminal ini." -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "====================================================" -ForegroundColor Red
    Write-Host "   BUILD GAGAL!" -ForegroundColor Red
    Write-Host "====================================================" -ForegroundColor Red
    
    [System.Console]::Beep(300, 1500)
    Write-Host ""
    Write-Host "Periksa error di atas untuk mengetahui penyebab kegagalan." -ForegroundColor Yellow
    Write-Host "Beberapa penyebab umum:" -ForegroundColor Yellow
    Write-Host "  - File .env tidak ditemukan atau GEMINI_API_KEY tidak valid" -ForegroundColor Yellow
    Write-Host "  - Koneksi internet terputus (gagal download dependensi)" -ForegroundColor Yellow
    Write-Host "  - Ada error kode yang perlu diperbaiki" -ForegroundColor Yellow
}