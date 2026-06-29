// ============================================================
//  KONFIGURASI UTAMA — SUMBER TUNGGAL GAS URL
//  Jika URL GAS berubah, edit file ini SAJA lalu jalankan:
//      .\sync-config.ps1
//  Perubahan akan otomatis disinkronkan ke firmware ESP8266.
// ============================================================
const APP_CONFIG = {
  gasUrl: 'https://script.google.com/macros/s/AKfycbwZ6omAYIcQWndSVs-X7ZLp4JriwwnA4DQH7qP25PYcWcp3kvofK_hPUorTB8YYXHU/exec',
  // Auto-off toggle: jika true → pompa mati otomatis saat air >= autoOffLevel
  // Jika false → pompa TIDAK mati otomatis (harus manual atau via jadwal)
  autoOffEnabled: true,
  autoOffLevel: 95
};
