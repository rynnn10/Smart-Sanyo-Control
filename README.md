# Smart Sanyo Control — Versi Website

Versi web dari aplikasi Android, **UI sama persis** (dibangkitkan dari `app/src/main/assets/index.html`), tapi MQTT lewat **WebSocket (WSS)** langsung dari browser — bukan native bridge.

> Update terakhir: Rab 01/07/2026 — web v1.1.0

## Sinkron lintas device
Jadwal solat & riwayat notifikasi sinkron otomatis di App + Web + device manapun — bukan lewat
server sendiri, tapi lewat ESP (satu-satunya yang sudah terhubung ke semua device via broker MQTT
publik). ESP menyiarkan `prayerTimes` (jadwal tersimpan) + `notifLog` (~8 event terakhir: air
kritis/penuh, waktu solat) di status MQTT tiap 3 detik; App & Web tinggal baca & dedupe via
(type, menit). Lihat `mqtt-web.js` (`mergeNotifLog`) dan `SanyoService.kt` (`mergeNotifLog`) untuk detail.

## Isi
| File | Fungsi |
|------|--------|
| `index.html` | Hasil generate — **jangan edit langsung**, edit sumbernya di `app/.../assets/index.html` lalu build ulang |
| `mqtt-web.js` | Shim `MqttAndroid` (WSS ke `broker.emqx.io:8084`) — satu-satunya file khusus web |
| `config.js` | Disalin dari app |
| `favicon.png` | Disalin dari `app/src/main/res/drawable/ic_icon_sanyo.png` — icon sama dengan APK |
| `build-web.ps1` | Regenerate `index.html` + `config.js` + `favicon.png` dari sumber app |
| `test-mqtt-web.js` | `node test-mqtt-web.js` → cek payload MQTT cocok dgn firmware + regresi bug `window.X` |
| `adzan.mp3` | *(opsional)* taruh di sini agar azan bunyi di web saat waktu solat |

## Regenerate setelah UI app berubah
```powershell
powershell -ExecutionPolicy Bypass -File web\build-web.ps1
```

## Menjalankan
WSS butuh **secure context** — jalankan dari `localhost` atau host HTTPS:
```powershell
# opsi cepat (butuh Node)
npx serve web
# atau Python
python -m http.server 8000 --directory web
```
Buka `http://localhost:3000` (atau `:8000`). Untuk publik, host folder `web/` di Netlify / GitHub Pages / Vercel (semua HTTPS).

## Beda dari APK
- **Azan**: web hanya bunyi saat tab terbuka (`adzan.mp3` via `<audio>`). Background/"mati total" hanya bisa di APK (foreground service).
- **Share/notif histori**: web pakai `navigator.share` + `localStorage`.
- Sisanya (kontrol pompa, jadwal, auto on/off, level air, jadwal solat GPS) identik.
