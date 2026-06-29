# Smart Sanyo Control

Sistem IoT untuk monitoring dan kontrol otomatis pompa air (Sanyo) menggunakan **Wemos D1 Mini (ESP8266)**, **Aplikasi Android**, dan **Google Apps Script** sebagai backend cloud.

---

## Arsitektur Sistem

```
[Sensor Ultrasonik] ──► [Wemos D1 Mini]  ──► [Google Apps Script]
[Relay Pompa]       ◄──  (ESP8266)       ◄──   (Cloud Database)
[LCD I2C 16x2]              │                        ▲
[Tombol ON/OFF]              │                        │
                             └── WiFi ────────────────┘
                                                       │
                                               [Aplikasi Android]
                                               (WebView + HTTP)
                                                       │
                                               [Firebase FCM]
                                             (Push Notification)
```

---

## Komponen Hardware

| Komponen | Spesifikasi |
|----------|-------------|
| Mikrokontroler | Wemos D1 Mini (ESP8266) |
| Sensor Air | JSN-SR04T (waterproof) atau HC-SR04 |
| Relay | Module Relay 5V 1-channel |
| Display | LCD 1602 I2C (alamat 0x27 atau 0x3F) |
| Tombol | Push Button × 2 |
| Power Supply | Adaptor 5V min. 2A |

---

## Wiring Diagram

| Modul | Pin Modul | Wemos D1 Mini |
|-------|-----------|---------------|
| **Relay 5V** | IN | D1 |
| | VCC / GND | 5V / GND |
| **JSN-SR04T** | TRIG | D2 |
| | ECHO | D3 |
| | VCC / GND | 5V / GND |
| **LCD I2C** | SDA | D6 |
| | SCL | D5 |
| | VCC / GND | 5V / GND |
| **Tombol ON** | Kaki 1 | D4 |
| | Kaki 2 | GND |
| **Tombol OFF** | Kaki 1 | D7 |
| | Kaki 2 | GND |

> **Catatan:** Tombol menggunakan `INPUT_PULLUP` internal — tidak perlu resistor eksternal.

---

## Fitur

- Monitoring ketinggian air real-time via sensor ultrasonik
- Kontrol relay otomatis (**Auto Mode**) berdasarkan level air
- Kontrol relay manual via **Aplikasi Android** dan **tombol fisik**
- **Dual WiFi failover**: Otomatis berpindah ke jaringan cadangan
- **Mode offline**: Pompa tetap bisa dioperasikan via tombol fisik
- **Penjadwalan otomatis** (atur jadwal ON/OFF dari app)
- **Push Notification Firebase** saat air kritis (< 10%) atau penuh (> 90%)
- LCD menampilkan status air, relay, dan jaringan WiFi aktif
- Sync data ke Google Spreadsheet sebagai log aktivitas

---

## Setup Awal

### 1. Clone Project

```bash
git clone https://github.com/rynnn10/Smart-Sanyo-Control.git
cd Smart-Sanyo-Control
```

### 2. Google Apps Script (Backend Cloud)

1. Buka [Google Spreadsheet](https://sheets.google.com) baru
2. Klik **Ekstensi → Apps Script**
3. Hapus semua kode lama, lalu salin isi file `kode.gs` ke editor
4. Klik **Terapkan → Deployment Baru**:
   - Jenis: **Web App**
   - Jalankan sebagai: **Saya**
   - Akses: **Semua Orang**
5. Salin **URL Web App** yang dihasilkan

> 💡 **Setelah langkah 5 selesai**, lanjutkan ke **[🔄 Sinkronisasi Otomatis via clasp](#-sinkronisasi-otomatis-via-clasp)** untuk mengotomatiskan upload `kode.gs` — tidak perlu copy-paste manual lagi.

### 🔄 Sinkronisasi Otomatis via clasp

Agar setiap perubahan pada `kode.gs` lokal langsung tersinkron ke Apps Script tanpa copy-paste manual ke editor:

#### Setup Sekali

**1.** Aktifkan Apps Script API:  
Buka https://script.google.com/home/usersettings → **ON**

**2.** Install dependensi (dijalankan dari root project):
```bash
npm install
```
(clasp sudah terdaftar sebagai devDependency)

**3.** Login clasp:
```bash
npm run gas:login
```
Perintah ini akan membuka browser untuk login dengan akun Google yang sama dengan Apps Script Anda.

**4.** Salin Script ID ke `.clasp.json`:
- Buka Apps Script Editor → ⚙️ **Project Settings** → salin **Script ID**
- Copy template lalu isi Script ID:
```bash
copy .clasp.json.example .clasp.json
```
Edit `.clasp.json` dan isi `scriptId` dengan ID yang sudah disalin.

**5.** Salin Deployment ID ke `.env`:
- Buka Apps Script Editor → **Deploy** → **Manage deployments** → salin **Deployment ID**
- Atau lihat token pada URL Web App: `.../macros/s/<INI>/exec`
- Copy template lalu isi Deployment ID:
```bash
copy .env.example .env
```
Edit `.env` dan isi:
```
CLASP_DEPLOYMENT_ID=AKfycb...
```

**6.** Push pertama `kode.gs` ke editor Apps Script:
```bash
npm run gas:push
```

**7.** Deploy pertama agar Web App langsung live (URL tetap):
```bash
npm run gas:deploy
```

> ⚠️ **Push pertama menimpa** kode online dengan `kode.gs` lokal — pastikan lokal sudah versi terbaru.

#### Cara Pemakaian Harian

| Perintah | Fungsi |
|----------|--------|
| `npm run gas:push` | Upload `kode.gs` ke editor Apps Script (tidak live) |
| `npm run gas:watch` | Auto-upload ke editor tiap kali `kode.gs` disimpan |
| `npm run gas:deploy` | Push + update deployment Web App (URL **TETAP** sama) |
| `npm run gas:watch-deploy` | Auto push + **deploy LIVE** tiap `kode.gs`/`appsscript.json` berubah |
| `npm run gas:open` | Buka Apps Script editor di browser |
| `npm run gas:pull` | Tarik kode terbaru dari editor Apps Script ke lokal |
| `npm run gas:login` | Login ulang ke clasp (ganti akun) |
| `npm run gas:logout` | Logout dari clasp |
| `npm run gas:deploy-id` | Cek Deployment ID yang terkonfigurasi |

#### ⚙️ gas:watch-deploy
- Memantau file `kode.gs` & `appsscript.json`
- Begitu ada perubahan → otomatis **clasp push** lalu **clasp deploy** ke deployment yang sama
- Ada **debounce 1 detik** + **antrian anti-tumpang-tindih**
- ⚠️ **Tiap simpan = kode langsung LIVE + 1 versi baru** di Apps Script
- Cocok dipakai saat **aktif ngoprek**; matikan (**Ctrl+C**) jika tidak dipakai agar tidak deploy kode setengah jadi atau boros versi

> ⚠️ `gas:push` hanya update **editor** Apps Script. Web App yang live baru berubah setelah `gas:deploy` (atau redeploy manual).  
> ⚠️ Deploy ke **deployment ID yang ada** (`-i`) menjaga URL tetap sama; tanpa itu tiap deploy akan membuat URL baru.

---

### 3. Konfigurasi GAS URL (Satu File, Dua Platform)

Edit **satu file ini**:

```
app/src/main/assets/config.js
```

Ganti nilai `gasUrl` dengan URL dari langkah 2:

```javascript
const APP_CONFIG = {
  gasUrl: 'https://script.google.com/macros/s/GANTI_DENGAN_URL_ANDA/exec'
};
```

Lalu jalankan script sinkronisasi (PowerShell):

```powershell
.\sync-config.ps1
```

Script ini akan otomatis memperbarui `GAS_URL` di semua file `config.h` ESP8266.

---

## Flash Firmware ESP8266 (PlatformIO)

### Prasyarat
- [VS Code](https://code.visualstudio.com/) + ekstensi **PlatformIO IDE**

### Langkah-Langkah

**1. Buka Project PlatformIO**

Buka folder `SmartSanyo_ESP8266/` di VS Code:
```
File → Open Folder → pilih SmartSanyo_ESP8266/
```

**2. Edit Kredensial WiFi**

Edit file `SmartSanyo_ESP8266/include/config.h`:

```cpp
#define WIFI_SSID         "NamaWiFiAnda"
#define WIFI_PASSWORD     "PasswordWiFiAnda"
#define WIFI_SSID_BACKUP  "NamaWiFiCadangan"     // opsional
#define WIFI_PASS_BACKUP  "PasswordCadangan"      // opsional
```

Opsional — isi juga untuk notifikasi Telegram:
```cpp
#define BOT_TOKEN  "12345678:ABC..."
#define CHAT_ID    "987654321"
```

**3. Hubungkan Wemos D1 Mini via kabel USB.**

**4. Upload Firmware**

Klik tombol **Upload** (ikon →) di status bar bawah PlatformIO,  
atau tekan `Ctrl+Alt+U`, atau jalankan:

```bash
pio run -t upload
```

**5. Verifikasi via Serial Monitor**

Klik **Serial Monitor** atau tekan `Ctrl+Alt+S`:

```
Smart Sanyo v2 Starting...
Mencoba WiFi utama: NamaWiFiAnda
....
WiFi Utama Connected!
```

> **Catatan:** Beberapa baris karakter acak di awal adalah **normal** — itu pesan boot ROM ESP8266 yang berjalan di 74880 baud.

---

## Build & Install Aplikasi Android

### Prasyarat
- JDK 17+ terinstall
- ADB terinstall
- HP Android dengan **Wireless Debugging** atau **USB Debugging** aktif

### Cara 1: Script Otomatis (Direkomendasikan)

Script `run.ps1` akan otomatis mendeteksi HP di jaringan, build, install, dan buka app:

```powershell
# Auto-scan HP di jaringan yang sama
.\run.ps1

# Manual dengan IP HP yang sudah diketahui
.\run.ps1 -DeviceId 192.168.1.7:5555
```

**Aktifkan Wireless Debugging di HP (Android 11+):**
> Setelan → Opsi Pengembang → Wireless Debugging → Aktifkan

### Cara 2: Via Kabel USB

```powershell
# Pastikan HP terdeteksi
adb devices

# Build dan install sekaligus
.\gradlew.bat :app:installDebug
```

### Cara 3: Build APK Manual

```powershell
.\gradlew.bat assembleRelease
```
APK ada di: `app/build/outputs/apk/release/`

---

## Cara Mengubah GAS URL

Jika URL Google Apps Script berubah (misal setelah deployment baru):

**1.** Edit `app/src/main/assets/config.js`:
```javascript
const APP_CONFIG = {
  gasUrl: 'https://script.google.com/macros/s/URL_BARU/exec'
};
```

**2.** Jalankan sinkronisasi:
```powershell
.\sync-config.ps1
```

**3.** Upload ulang firmware ke ESP8266 (`pio run -t upload`)

**4.** Build ulang Android app (`.\run.ps1`)

---

## Cara Kerja Sistem

```
Setiap 2 detik  : Sensor baca jarak → hitung % air
Setiap 10 detik : ESP kirim ke GAS (waterLevel + SSID + RSSI)
                  GAS simpan, cek jadwal, kirim balik pumpStatus
                  App baca dari GAS → update dashboard
Saat air ≤ 10%  : ESP kirim Telegram + GAS kirim FCM notification
Saat air ≥ 90%  : GAS kirim FCM notification (tangki penuh)
Saat WiFi putus : Mode manual aktif (tombol fisik tetap berfungsi)
```

---

## Struktur Project

```
Smart-Sanyo-Control/
├── app/src/main/assets/
│   ├── config.js             ← SUMBER TUNGGAL GAS URL (edit ini)
│   └── index.html            # Dashboard WebView Android
├── SmartSanyo_ESP8266/       # Firmware ESP8266 (PlatformIO)
│   ├── include/config.h      # Kredensial WiFi + GAS URL (auto-sync)
│   ├── src/main.cpp          # Firmware Smart Sanyo v2
│   └── platformio.ini        # Board: d1_mini
├── kode.gs                   # Google Apps Script (backend)
├── sync-config.ps1           # Sinkronisasi GAS URL ke config.h
├── run.ps1                   # Build & install Android app
└── README.md
```

---

## Troubleshooting

| Masalah | Solusi |
|---------|--------|
| Serial monitor karakter acak | Normal di awal boot. Tunggu `Smart Sanyo v2 Starting...` |
| WiFi tidak tersambung | Periksa SSID/password di `config.h`. Pastikan `board = d1_mini` |
| LCD tidak menyala | Coba alamat I2C `0x3F` (ganti dari `0x27`) di `main.cpp` |
| App tidak bisa build | Pastikan JDK 17+ terinstall. Jalankan `.\gradlew.bat --stop` lalu coba lagi |
| HP tidak terdeteksi ADB | Aktifkan Wireless Debugging. Pastikan PC & HP di WiFi yang sama |
| GAS URL error | Buat **New Deployment** (bukan edit deployment lama) di Apps Script |
| Status WiFi app "Menunggu..." | Tunggu ESP8266 sinkronisasi pertama ke GAS (maks. 10 detik setelah boot) |

---

## Lisensi

MIT License — Dibuat oleh [Riyan](https://github.com/rynnn10) © 2026
