# Smart Sanyo Control

Sistem IoT monitoring dan kontrol otomatis pompa air menggunakan **WeMos D1 Mini (ESP8266)** dan **Aplikasi Android**, terhubung via **MQTT**.

> Update terakhir: Rab 01/07/2026 — v2.8.1 (firmware) / v3.5.0 (app Android) / v1.2.0 (web)
>
> 🌐 Web live: **https://rynnn10.github.io/Smart-Sanyo-Control/** (branch `gh-pages`)

---

## Arsitektur Sistem

```
[JSN-SR04T]  ──►  [WeMos D1 Mini]  ──►  MQTT Broker  ◄──  [Aplikasi Android]
[Relay]       ◄──   (ESP8266)       ◄──  broker.emqx.io     (WebView + Paho)
[LCD I2C]              │
[Buzzer]               │ WiFi
[Tombol]               │
              [NTP: pool.ntp.org WIB]
```

Tidak ada server cloud buatan sendiri. Semua komunikasi real-time via MQTT public broker.

---

## Komponen Hardware

| Komponen | Spesifikasi |
|----------|-------------|
| Mikrokontroler | WeMos D1 Mini (ESP8266) |
| Sensor Air | JSN-SR04T Waterproof Ultrasonic |
| Relay | Module Relay 5V 1-channel |
| Display | LCD 1602 I2C (alamat 0x27 atau 0x3F) |
| Buzzer | Buzzer aktif 5V |
| Tombol | Push Button × 2 (ON / OFF manual) |

---

## Wiring Diagram

| Modul | Pin Modul | WeMos D1 Mini |
|-------|-----------|---------------|
| **Relay** | IN | D1 |
| | VCC / GND | 5V / GND |
| **JSN-SR04T** | TRIG | D2 |
| | ECHO | D3 |
| | VCC / GND | 5V / GND |
| **LCD I2C** | SDA | D6 |
| | SCL | D5 |
| | VCC / GND | 3.3V / GND |
| **Tombol ON** | Kaki 1 | D4 |
| | Kaki 2 | GND |
| **Tombol OFF** | Kaki 1 | D7 |
| | Kaki 2 | GND |
| **Buzzer** | (+) | D8 |
| | (−) | GND |

> Tombol menggunakan `INPUT_PULLUP` internal — tidak perlu resistor eksternal.

---

## Kebutuhan Daya & Rakit UPS Sendiri (Panduan Belajar)

> Analisa konsumsi daya seluruh modul + kipas mini + modem WiFi, lalu rekomendasi rakit UPS DIY. Angka arus adalah tipikal pasaran — ukur sendiri untuk hasil presisi (*hardware bukan spek ideal di atas kertas*).

### 1. Konsumsi Arus (rail 5V)

| Modul | Arus tipikal | Arus puncak |
|-------|-------------|-------------|
| WeMos D1 Mini (ESP8266, WiFi ON) | 80 mA | 170 mA (burst TX) |
| JSN-SR04T ultrasonik | 15 mA | 30 mA |
| Relay 1ch 5V (coil aktif) | 70 mA | 75 mA |
| LCD 1602 I2C + backlight | 30 mA | 40 mA |
| Buzzer aktif (saat berbunyi) | ~0 | 30 mA |
| Kipas mini 5V (5015/blower) | 150 mA | 250 mA |
| Tombol ×2 | ~0 | ~0 |
| **Subtotal sistem (5V)** | **~345 mA** | **~595 mA** |

→ Sistem Smart Sanyo saja: **~0,35 A tipikal / ~0,6 A puncak @ 5V** (≈ 1,8–3 W).

### 2. Modem WiFi — 2 skenario

| Skenario | Modem | Konsumsi | Total sistem + modem |
|----------|-------|----------|----------------------|
| **A** | MiFi / modem USB **5V** | ~1 A (5 W) | 5V ~1,4 A (≈ **7–8 W**) — semua 1 rail 5V |
| **B** | Router / ONT rumah **12V** | ~1 A (12 W) | 5V (1,75 W) + 12V (12 W) ≈ **14–15 W** — dua rail |

### 3. Rekomendasi Adaptor (tanpa UPS)

- **Skenario A:** satu adaptor **5V 3A (15W)** cukup untuk semua (margin >2×, aman lonjakan + charging).
- **Skenario B:** adaptor **5V 2A** untuk sistem + adaptor asli modem **12V 1A**; atau satu **12V 3A** + **buck 12→5V 3A**.

### 4. Rakit UPS DIY

Prinsip UPS: **baterai + charger + auto-switchover** — saat PLN mati, beban lanjut dari baterai tanpa putus.

#### Skenario A — UPS 5V (termudah & termurah, semua beban 5V)

Pakai **modul UPS 18650** jadi (TP4056 + boost 5V + proteksi + pass-through) — tidak perlu solder rumit.

| Alat/Bahan | Jumlah | Estimasi (Rp) |
|-----------|--------|---------------|
| Modul UPS 5V 18650 (charge+boost 5V/2A, pass-through) | 1 | 25.000–50.000 |
| Baterai 18650 2500–3000 mAh | 2 (paralel) | 60.000–120.000 |
| Holder 18650 | 2 | 10.000 |
| Adaptor input 5V 3A (charger) | 1 | 35.000–50.000 |
| Fuse 3A + holder, kabel, box | — | 25.000 |
| **Total** | | **±150.000–255.000** |

**Estimasi backup:** 2×2500 mAh @3,7V ≈ 18,5 Wh, terpakai ~80% × efisiensi boost ~85% ≈ **12,6 Wh**.
- Beban 7 W (sistem + modem 5V + kipas) → **±1,8 jam**
- Sistem Sanyo saja ~2,5 W → **±5 jam**
- Mau lebih lama? Tambah sel paralel (tiap 18650 ≈ +2,5 jam untuk beban 2,5 W).

**Wiring (skenario A):**
```
[Adaptor 5V 3A] ── IN+/IN−  ┐
[Pack 18650]    ── B+/B−     ├── Modul UPS 5V
                             └── OUT 5V ──► rail 5V bersama:
   +5V → WeMos 5V(VIN), Relay VCC, LCD VCC, JSN VCC, Kipas +, Buzzer +
   GND → GND semua modul (common ground)
```
PLN nyala: adaptor suplai beban + cas baterai. PLN mati: modul auto-switch ke baterai, beban tak putus.

#### Skenario B — UPS 12V + 5V (modem router 12V)

| Alat/Bahan | Jumlah | Estimasi (Rp) |
|-----------|--------|---------------|
| Baterai 18650 2500–3000 mAh | 6 (3S2P) / 9 (3S3P) | 180.000–540.000 |
| BMS 3S 20–40A (proteksi + balance) | 1 | 15.000–30.000 |
| Charger 12,6V (TP5100 mode 3S / adaptor 12,6V) | 1 | 25.000–60.000 |
| Buck 12→5V 3A (MP1584 / LM2596) | 1 | 10.000–20.000 |
| Modul auto-switchover DC (mini-UPS / relay+diode) | 1 | 30.000–80.000 |
| Holder/spot-weld, fuse 5A, box, kabel | — | 50.000 |
| **Total** | | **±350.000–800.000** |

**Estimasi backup:** 3S2P (6 sel) ≈ 55 Wh (usable ~40 Wh); beban ~14 W → **±2,8 jam**. 3S3P (9 sel) ≈ 83 Wh (usable ~60 Wh) → **±4 jam**.

**Wiring (skenario B):**
```
[Adaptor 12,6V] ──► Charger/BMS (charge in)
[Pack 3S]  ── B−,B1,B2,B+ ──► BMS ──► P+/P− (output terproteksi)
P+ (≈11–12,6V) ──► Modul auto-switchover ──► OUT 12V:
     ├──► Modem router (12V)
     └──► Buck 12→5V ──► rail 5V sistem (WeMos, Relay, LCD, JSN, Kipas, Buzzer)
GND semua disatukan (common ground)
```

### 5. Keselamatan (WAJIB, jangan dilewati)

- **BMS wajib** untuk pack Li-ion — cegah over-charge / over-discharge / short. Jangan pakai 18650 tanpa proteksi.
- Pasang **fuse** di jalur baterai (5V: 3A; 12V: 5A).
- **Jangan campur** sel beda kapasitas/merk/umur dalam satu pack.
- Arus charge ≤ **0,5C** dari kapasitas pack (mis. pack 5000 mAh → charge ≤ 2,5A).
- 18650 bisa terbakar bila short/tertusuk → beri **box tahan panas**, jauhkan dari air (penting di proyek pompa).
- Samakan **ground** (common GND) antara adaptor, UPS, dan semua modul.

> ⚠️ Estimasi budget = harga pasar Indonesia (Tokopedia/Shopee, 2026) dan bisa berubah. Runtime nyata lebih pendek dari teori (efisiensi konverter, umur sel, suhu).

---

## Fitur

### Firmware (WeMos D1 Mini)
- Baca level air setiap 2 detik via JSN-SR04T (median filter 5 sampel)
- **Blind zone detection**: saat air terlalu dekat sensor (<25cm), LCD tampil `Air: PENUH!`
- **Outlier rejection**: lonjakan bacaan >25cm butuh 3 siklus berturut dikonfirmasi — mencegah echo palsu ~60cm dari sensor hangat
- Auto OFF saat air ≥ batas penuh, Auto ON saat air ≤ batas kritis (threshold atur dari app)
- Sinkronisasi waktu **NTP** otomatis (WIB UTC+7) — tanpa library tambahan
- **Penjadwalan** ON/OFF mingguan — terima jadwal JSON dari app via MQTT, eksekusi sesuai waktu
- Dual WiFi failover: otomatis pindah ke jaringan cadangan
- Mode offline: tombol fisik tetap berfungsi tanpa WiFi
- **LCD 16x2 multi-layar bergilir** (v2.6.0): air+pompa (rata tengah) → SSID+dBm+kualitas → hari/tanggal+jam → jadwal solat. Teks >16 kolom otomatis berjalan (scroll)
- **Layar prioritas azan**: saat waktu solat tiba, LCD tampil `Waktu <Solat>` ~3 menit lalu kembali bergilir
- Buzzer: 1 beep = relay berubah, 3 beep = startup

### Aplikasi Android
- Dashboard real-time: level air dengan **canvas wave 3D** (permukaan bergelombang dan miring mengikuti gravitasi HP)
- Warna level air: merah <20%, kuning <50%, biru/cyan ≥50%
- Kontrol manual pompa ON/OFF via MQTT
- Atur jadwal ON/OFF mingguan, kirim ke ESP via MQTT
- Atur threshold Auto OFF/ON dari aplikasi
- Indikator koneksi ESP: online/offline, SSID, RSSI
- Badge konfirmasi jadwal: "Terkirim ke ESP" / "Belum dikirim"
- Dark UI dengan animasi 3D background (Three.js)

---

## Cara Kerja MQTT

### Topic

| Topic | Arah | Isi |
|-------|------|-----|
| `smartsanyo/riyan123/status` | ESP → App | JSON status: `waterLevel`, `pumpStatus`, `autoOffEnabled`, `ssid`, `rssi`, `hasSchedule`, `scheduleCount` |
| `smartsanyo/riyan123/control` | App → ESP | Perintah kontrol (lihat di bawah) |

### Format Perintah (App → ESP)

```json
// Kontrol relay manual
{"command":"ON"}
{"command":"OFF"}

// Auto OFF: pompa mati saat air ≥ level (95%)
{"command":"AUTO_OFF","enabled":true,"level":95}

// Auto ON: pompa hidup saat air ≤ level (20%)
{"command":"AUTO_ON","enabled":true,"level":20}

// Kirim jadwal mingguan
{"command":"SCHEDULE_SET"}
[
  {"onTime":"06:00","offTime":"07:00","days":["Senin","Selasa","Rabu","Kamis","Jumat"]},
  {"onTime":"18:00","offTime":"19:00","days":["Sabtu","Minggu"]}
]
```

Perintah string lain (bukan JSON, langsung ke topic control):

```
BUZZER_5                                   # bunyikan buzzer ESP 5× (azan)
PRAYER_{"Subuh":"04:40","Dzuhur":"11:55"}  # jadwal solat → tampil di LCD ESP (v2.6.0)
```

### Alur Penjadwalan

1. Buat jadwal di app → tekan **Simpan Jadwal**
2. App kirim JSON jadwal via MQTT ke ESP (`SCHEDULE_SET`)
3. ESP simpan di memori, sinkronisasi waktu via NTP (`pool.ntp.org`)
4. Setiap **30 detik** ESP cek apakah waktu sekarang cocok dengan jadwal → eksekusi relay

---

## Setup Firmware ESP8266 (PlatformIO)

### Prasyarat
- [VS Code](https://code.visualstudio.com/) + ekstensi **PlatformIO IDE**
- Firmware ada di project PlatformIO terpisah (`Smart-Sanyo/`)

### 1. Konfigurasi WiFi

Edit `include/config.h`:

```cpp
#define WIFI_SSID         "NamaWiFiAnda"
#define WIFI_PASSWORD     "PasswordWiFiAnda"
#define WIFI_SSID_BACKUP  "WiFiCadangan"      // opsional
#define WIFI_PASS_BACKUP  "PasswordCadangan"  // opsional
```

### 2. Kalibrasi Sensor

Edit konstanta di bagian atas `src/main.cpp`:

```cpp
const int DIST_EMPTY_CM = 100; // jarak sensor → permukaan air saat tangki KOSONG → 0%
const int DIST_FULL_CM  = 25;  // jarak sensor → permukaan air saat tangki PENUH  → 100%
```

> **Penting:** `DIST_FULL_CM` minimal 25cm karena JSN-SR04T tidak bisa mengukur jarak <25cm (blind zone hardware). Jika air menyentuh/mendekati batas ini, LCD akan tampil `Air: PENUH!`.

Cara ukur:
- Isi tangki sampai **penuh** → ukur jarak dari sensor ke permukaan air → isi `DIST_FULL_CM`
- Kosongkan tangki sampai **batas mati pompa** → ukur jarak → isi `DIST_EMPTY_CM`

### 3. Upload Firmware

```bash
pio run -t upload
```

### 4. Verifikasi Serial Monitor (baud 115200)

```
Smart Sanyo v2 Starting...
[WiFi] Terhubung ke: NamaWiFiAnda (IP: 192.168.1.x)
[NTP] Sync OK: 07:30:00 01/07/2026 WIB
[MQTT] Terhubung. ESP siap via MQTT.
[Ultrasonic] Setup: jarak=55 cm, level=50%
[Schedule] Belum ada jadwal — kirim jadwal dari aplikasi Android
```

---

## Build & Install Aplikasi Android

### Prasyarat
- JDK 17+ terinstall
- HP Android dengan **USB Debugging** atau **Wireless Debugging** aktif (Android 11+)

### Cara 1: Script Otomatis (Direkomendasikan)

```powershell
# Auto-scan HP di jaringan yang sama
.\run.ps1

# Manual dengan IP HP yang diketahui
.\run.ps1 -DeviceId 192.168.1.7:5555
```

**Aktifkan Wireless Debugging:**
> Setelan → Opsi Pengembang → Wireless Debugging → Aktifkan

### Cara 2: Kabel USB

```powershell
adb devices
.\gradlew.bat :app:installDebug
```

### Cara 3: Build APK saja

```powershell
.\gradlew.bat assembleRelease
# APK: app/build/outputs/apk/release/
```

---

## Cara Menggunakan Aplikasi

### Pertama Kali

1. Buka aplikasi → tunggu status berubah ke **Online** (beberapa detik)
2. Pastikan WeMos sudah nyala dan terhubung WiFi yang sama dengan MQTT broker
3. Level air akan otomatis tampil di dashboard

### Kontrol Manual Pompa

- Tekan **ON** / **OFF** di dashboard → perintah langsung dikirim via MQTT ke ESP

### Penjadwalan Otomatis

1. Buka tab **Jadwal**
2. Aktifkan toggle **Jadwal Otomatis**
3. Tekan **+ Tambah Jadwal** → atur waktu ON, waktu OFF, dan hari
4. Tekan **Simpan Jadwal** → jadwal dikirim ke ESP via MQTT
5. Badge akan berubah ke **"Terkirim ke ESP ✓"** setelah ESP menerima

### Auto ON/OFF Berdasarkan Level

- Buka tab **Pengaturan**
- Atur **Batas Mati (Auto OFF)**: pompa mati otomatis saat air mencapai level ini (default 95%)
- Atur **Batas Hidup (Auto ON)**: pompa hidup otomatis saat air di bawah level ini (default 20%)

### Tampilan Air 3D

Miringkan HP ke kiri/kanan → permukaan air di dashboard akan ikut miring mengikuti gravitasi. Efek ini menggunakan sensor accelerometer HP via `DeviceOrientationEvent`.

> Di beberapa HP Android, izin `DeviceOrientationEvent` mungkin perlu diaktifkan manual atau tidak tersedia.

---

## Struktur Project

```
Smart-Sanyo-Control/           ← Repo ini (Android app)
├── app/src/main/
│   ├── assets/index.html      # Dashboard WebView (UI + MQTT JS)
│   ├── java/com/example/
│   │   ├── MainActivity.kt    # WebView wrapper + Java-JS bridge
│   │   └── MqttBridge.kt      # Paho MQTT client (Android)
│   └── AndroidManifest.xml
├── run.ps1                    # Script build + install ADB
└── README.md

Smart-Sanyo/ (PlatformIO)      ← Project terpisah (firmware ESP8266)
├── src/main.cpp               # Firmware utama v2.4.1
├── include/config.h           # Kredensial WiFi + pin + MQTT config
└── platformio.ini             # Board: d1_mini
```

---

## Troubleshooting

| Masalah | Solusi |
|---------|--------|
| LCD tampil `Air: PENUH!` tapi tangki belum penuh | Sensor dalam blind zone (<25cm). Naikkan sensor lebih jauh dari permukaan air, atau isi tangki tidak sampai terlalu dekat sensor |
| Level air loncat-loncat (misal tiba-tiba 60%) | Sensor hangat → echo palsu. Sudah ditangani outlier rejection 3-cycle. Jika masih terjadi, naikkan `DIST_FULL_CM` ke 30+ |
| LCD tidak menyala | Coba alamat I2C `0x3F` (ganti dari `0x27`) di `main.cpp` |
| App status "Offline" terus | Periksa WiFi ESP, pastikan terhubung. Cek Serial Monitor untuk error MQTT |
| Jadwal tidak jalan | Pastikan ESP sudah sync NTP — Serial Monitor harus tampil `[NTP] Sync OK`. Perlu koneksi internet saat boot |
| App tidak bisa build | Pastikan JDK 17+ terinstall. Jalankan `.\gradlew.bat --stop` lalu coba lagi |
| HP tidak terdeteksi ADB via WiFi | PC & HP harus di WiFi yang sama. Restart ADB: `adb kill-server` lalu `adb start-server` |
| Serial monitor karakter acak | Normal di awal boot ESP8266 (74880 baud). Tunggu `Smart Sanyo v2 Starting...` |

---

## Changelog

### v2.8.1 (firmware) — Rab 01/07/2026
- **Kapasitas jadwal 5 → 20** (`MAX_SCHEDULES`). Buffer parse JSON `1024 → 8192` (20 jadwal ≈ 5 KB di ArduinoJson) + `MQTT_MAX_PACKET_SIZE 2048 → 3072` (payload 20 jadwal ≈ 1,55 KB). RAM aman — 20 jadwal ≈ 400 byte, heap ESP8266 ~40 KB bebas.
- **Baru di README**: analisa kebutuhan daya seluruh modul + kipas mini + modem WiFi, rekomendasi voltase/adaptor, dan panduan rakit **UPS DIY** (BOM budget + wiring + keselamatan).

### v3.5.0 (app) / v1.2.0 (web) — Rab 01/07/2026
- **UI (Auto Mode popup)**: toggle master "Auto Mode" **dihapus** (redundan). Tiap batas jadi **kartu mandiri** — "Batas Hidup" & "Batas Mati", masing-masing punya sakelar aktif/nonaktif sendiri + slider persen sendiri. Tombol "Pengaturan Auto Mode" dihapus dari popup Pengaturan (sudah ada kartu Auto Mode di halaman utama). Pompa manual kini selalu bisa ditekan (batas otomatis tetap dijalankan ESP).
- **UI (Info & Panduan)**: tombol "Cara Wiring Modul" → **"Info & Panduan Aplikasi"** — berisi ringkasan aplikasi, cara pakai (kontrol pompa, auto mode, jadwal, azan, notifikasi, tombol fisik), + tabel wiring lengkap.
- **UI (Notifikasi)**: tiap notif kini punya **ceklis** → tombol "Hapus (N)" menghapus yang dipilih; "Hapus Semua" kini benar-benar menghapus (dulu cuma menandai terbaca). Native lewat `deleteNotifications()` (SharedPrefs), web lewat localStorage.
- File berubah: `assets/index.html`, `MainActivity.kt` (bridge `deleteNotifications`), `app/build.gradle.kts` (v3.5.0/vc19), `web/mqtt-web.js` (v1.2.0), `README.md`. Self-check `web/test-mqtt-web.js` diperluas (hapus notif).
- **Analisa kapasitas jadwal WeMos**: saat ini dibatasi `MAX_SCHEDULES = 5`. Bukan RAM yang jadi batas (5 jadwal ≈ 100 byte; ESP8266 punya puluhan KB heap bebas) — batas nyatanya konstanta itu + buffer parse `DynamicJsonDocument(1024)` (~8 jadwal) + `MQTT_MAX_PACKET_SIZE 2048` (~20 jadwal di kabel). Aman dinaikkan ke ~15–20 dengan menaikkan `MAX_SCHEDULES` + buffer parse ke ~2048 bila perlu.

### v3.4.0 (app) / v2.8.0 (firmware) — Rab 01/07/2026
- **Baru (Firmware v2.8.0)**: tombol fisik **dwifungsi** — saat WiFi **tak terhubung** = ON/OFF pompa manual (mode darurat); saat **terhubung** = ganti layar LCD (tombol ON=maju, OFF=mundur), mengabaikan sisa waktu dwell **dan** meng-override layar prioritas azan. (Tombol di pin **D4** & **D7**; kontrol pompa saat online tetap via app/MQTT.)
- **Baru (App v3.4.0)**: azan tetap berbunyi **walau aplikasi ditutup total** — `AlarmManager` exact (`setExactAndAllowWhileIdle`) dijadwalkan per waktu solat (`AzanScheduler`) + `BootReceiver` untuk restart setelah HP nyala + minta pembebasan optimasi baterai. *Catatan: force-stop manual oleh user tetap tak bisa dilawan (batasan Android).*
- **Fix (App)**: volume azan kini **bisa diatur tombol volume HP** — audio pakai stream MEDIA (dulu ALARM). Konsekuensi: azan tak lagi menembus mode senyap (trade-off yang diminta).
- Izin baru: `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- File berubah: `src/main.cpp` (firmware), `SanyoService.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` (v3.4.0/vc18), + baru `AzanReceivers.kt`. Self-check `lcd_screen_advance_check.js` diperluas (tombol switch + cancel prioritas).

### v2.7.1 (firmware) — Rab 01/07/2026
- **Baru**: durasi tampil tiap layar LCD kini per-layar (`LCD_DWELL_TICKS[]` array, `src/main.cpp` — cari komentar `>>> ATUR DI SINI`), bukan satu angka untuk semua layar.
- **Baru**: layar jadwal solat memutar Subuh→Isya berurutan (tiap nama solat dapat jatah tampil sendiri) baru pindah ke layar lain — sebelumnya hanya 1 solat per rotasi penuh.
- Nama solat tetap di baris atas LCD, waktu di baris bawah (sudah begitu sejak v2.6.0, dipertahankan).
- File berubah: `src/main.cpp`. Self-check baru: `lcd_screen_advance_check.js`.

### v3.3.0 (app) / v2.7.0 (firmware) / v1.1.0 (web) — Rab 01/07/2026
- **Sinkron lintas device**: jadwal solat & riwayat notifikasi kini otomatis sama di App, Web, dan device manapun — tanpa server sendiri. ESP (satu-satunya yang sudah terhubung ke semua device via broker MQTT publik) jadi sumber bersama: status MQTT (`publishMqttStatus()`) sekarang membawa `prayerTimes` (jadwal tersimpan) + `notifLog` (~8 event terakhir: air kritis/penuh, waktu solat), disiarkan tiap 3 detik.
- **Fix (Firmware)**: `MQTT_MAX_PACKET_SIZE` PubSubClient default 256 byte akan diam-diam memotong/gagal publish status yang lebih besar — dinaikkan ke 2048 (`platformio.ini`).
- **Fix (Firmware)**: bug ArduinoJson — nilai `prayerTimes` sempat ditulis sebagai pointer mentah ke buffer stack lokal (dangling), dibungkus `String()` supaya benar-benar disalin sebelum serialize.
- **Refactor (App)**: `SanyoService.kt` — deteksi notifikasi air kritis/penuh berbasis debounce lokal (bisa beda antar device) dihapus, diganti konsumsi `notifLog` dari ESP (dedupe via `type_menit`). Notifikasi waktu solat lokal (untuk timing audio azan) tetap jalan, ditandai "sudah dilihat" agar tidak dobel saat echo ESP tiba.
- **Baru (Web)**: `mqtt-web.js` — web kini benar-benar punya riwayat notifikasi (sebelumnya selalu kosong, tak ada mekanisme penyimpanan sama sekali) + ikut memutar `adzan.mp3` saat entri `prayer` masuk.
- File berubah: `src/main.cpp`, `platformio.ini` (firmware, folder PlatformIO terpisah), `SanyoService.kt`, `MainActivity.kt`, `app/build.gradle.kts` (v3.3.0/vc17), `web/mqtt-web.js`, `README.md`. Self-check baru: `notif_log_ring_check.js` (firmware), `test-mqtt-web.js` diperluas (web).

### v3.2.1 (app) / v2.6.0 (firmware) / v1.0.1 (web) — Rab 01/07/2026 12:51
- **Baru (Firmware v2.6.0)**: LCD 16x2 **multi-layar bergilir** — (0) air+pompa rata tengah, (1) SSID+dBm+kualitas sinyal, (2) hari/tanggal + jam:menit:detik, (3) jadwal solat tersimpan (bergilir per solat). Baris >16 kolom **berjalan otomatis** (scroll wrap).
- **Baru (Firmware)**: **Layar prioritas** saat waktu solat tiba → tampil `Waktu <Solat>` selama ~3 menit (`PRAYER_PRIORITY_MS`, kalibrasi bila azan lebih panjang), lalu kembali bergilir.
- **Fix (Firmware)**: glitch karakter `A` setelah ON/OFF (mis. `ON A`, `OFFA`) — indikator auto-mode & `W1/W2` di layar utama dihapus (SSID pindah ke layar WiFi).
- **Baru (Firmware)**: MQTT command `PRAYER_{json}` — terima jadwal solat dari app untuk ditampilkan di LCD.
- **Baru (App v3.2.1)**: `savePrayerTimes()` kini juga mengirim jadwal solat ke ESP via `PRAYER_` (`MqttBridge.sendPrayerTimes`).
- **Baru (Web v1.0.1)**: shim web ikut publish `PRAYER_`; web di-deploy ke **GitHub Pages** (branch `gh-pages`) → https://rynnn10.github.io/Smart-Sanyo-Control/
- File berubah: `src/main.cpp` (firmware, folder PlatformIO terpisah), `MqttBridge.kt`, `MainActivity.kt`, `app/build.gradle.kts` (v3.2.1/vc16), `web/mqtt-web.js`, `README.md`. Self-check: `lcd_format_check.js`, `web/test-mqtt-web.js` → OK.

### v3.2.0 — App Android + Web — Rab 01/07/2026 12:51
- **Baru (App)**: Suara azan diputar otomatis saat waktu solat via `MediaPlayer` (stream ALARM, tembus mode senyap) — jalan walau app terbuka maupun tertutup lewat `SanyoService`. Taruh file di `app/src/main/res/raw/adzan.mp3` (lihat `res/raw/readme.txt`). Bila file belum ada, otomatis dilewati (buzzer ESP + notif tetap jalan). *Catatan: "mati total"/HP mati fisik tidak bisa diputar — perlu perangkat menyala.*
- **Baru (Web)**: Versi website standalone di folder `web/` — UI identik dengan APK, MQTT via WebSocket (`wss://broker.emqx.io:8084`). Digenerate dari sumber app (`web/build-web.ps1`), tanpa duplikasi UI. Lihat `web/README.md`.
- File berubah: `SanyoService.kt` (azan), `app/build.gradle.kts` (v3.2.0/vc15), `assets/index.html` (stempel versi), + baru: `web/*`, `res/raw/readme.txt`.
- **LCD I2C**: menunggu file firmware `main.cpp` (tidak ada di repo ini — project PlatformIO terpisah).

### v2.5.1 — Firmware — Sel 01/07/2026
- **Fix krusial**: blind zone tidak lagi menyebabkan relay ON saat tangki penuh setelah reboot
- Root cause: `waterPercent` inisialisasi ke 0; saat blind zone nilai tidak diperbarui → auto ON terpicu (`0% ≤ 20%`)
- Fix: blind zone branch (setup + loop) sekarang set `waterPercent = 100` → auto ON tidak terpicu, auto OFF berjalan normal
- MQTT publish juga mengirim `waterLevel: 100` saat blind zone → aplikasi tampil benar

### v3.0.0 — App Android — Sel 01/07/2026
- **Baru**: Efek air tumpah (splash) — otomatis saat level 100%, atau HP digoyangkan (DeviceMotion)
- **Baru**: 4 tema warna — Ocean (default), Sunset, Forest, Purple Night — pilih di Pengaturan
- **Baru**: Jam & tanggal real-time di header (HH:MM:SS + Hari/Tanggal)
- **Baru**: Jadwal Shalat — ambil otomatis via GPS + aladhan.com, atau edit manual; tiap waktu shalat → buzzer ESP 5×
- **Baru**: `SanyoService` — ForegroundService notifikasi background saat app mati (air hampir habis/penuh, waktu shalat)
- **Baru**: Auto Mode dipindah ke popup tersendiri (buka dari card utama atau Pengaturan)
- **Baru**: Tombol Nyalakan Saklar menyatu dalam kartu Sanyo Status
- **Firmware v2.5.0**: hostname `sanyo-kontrol` (terlihat di daftar perangkat hotspot), perintah `BUZZER_N` via MQTT, `WiFi.setOutputPower(17)` cegah brownout

### v2.4.1 — Firmware — Sel 01/07/2026
- **Baru**: LCD I2C tampil `Air: PENUH!` saat sensor blind zone (jarak <25cm dari sensor)
- Flag `sensorBlindZone` di-set/clear saat setiap pembacaan sensor

### v2.4.0 — App Android — Sel 01/07/2026
- **Baru**: Tampilan air canvas wave 3D — permukaan air bergelombang dan miring mengikuti gravitasi HP (`DeviceOrientationEvent.gamma`)
- Warna adaptif per level: merah <20%, kuning <50%, cyan ≥50%
- Hapus CSS lama `.water-liquid` / `.water-wave` (diganti canvas)

### v2.3.1 — Firmware — Sel 01/07/2026
- **Fix**: Threshold blind zone JSN-SR04T naik 20 → 25cm
- **Fix bug "lupa blind zone"**: tambah outlier rejection — lonjakan bacaan >25cm butuh 3 siklus berturut dikonfirmasi
- **Kalibrasi**: konstanta `DIST_EMPTY_CM` / `DIST_FULL_CM` kini di atas file sebagai knob kalibrasi

### v2.3.0 — Sel 01/07/2026
- Sinkronisasi waktu NTP (WIB UTC+7) ke WeMos — tanpa library tambahan
- Penjadwalan ON/OFF mingguan dari app Android via MQTT, eksekusi otomatis sesuai waktu
- Fix: jadwal tidak pernah dikirim dari app ke ESP (tambah `sendScheduleToEsp()`)
- Fix: method `sendAutoOnEnabled` hilang dari Kotlin bridge
- Serial monitor: log status jadwal aktif/diterima/tidak ada
- App: badge konfirmasi jadwal "Terkirim ke ESP ✓"
- ESP publish `hasSchedule` + `scheduleCount` di MQTT status

### v2.0.0 — Sebelumnya
- Migrasi arsitektur: GAS/cloud → MQTT-only (`broker.emqx.io`)
- Android app berbasis WebView + Paho MQTT client (mengganti HTTP polling)
- Dual WiFi failover, Auto ON/OFF threshold, tombol fisik
- LCD 16x2 I2C, buzzer notifikasi relay

---

## Lisensi

MIT License — Dibuat oleh [Riyan](https://github.com/rynnn10) © 2026
