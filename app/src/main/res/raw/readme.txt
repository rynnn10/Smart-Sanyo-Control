Taruh file suara azan di folder ini dengan nama PERSIS: adzan.mp3

  app/src/main/res/main/res/raw/adzan.mp3   <-- salah, JANGAN
  app/src/main/res/raw/adzan.mp3            <-- benar

Nama harus huruf kecil semua, tanpa spasi: adzan.mp3
Format yang didukung: .mp3 / .ogg / .wav (ubah nama tetap "adzan").

SanyoService.playAdzan() otomatis memutarnya (stream ALARM) saat waktu solat,
baik aplikasi terbuka maupun tertutup. Jika file belum ada, azan dilewati dan
hanya buzzer ESP + notifikasi yang jalan. Update: Rab 01/07/2026 - v3.2.0
