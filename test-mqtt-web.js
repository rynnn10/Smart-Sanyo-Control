// Self-check: payload MqttAndroid shim == kontrak MqttBridge.kt / firmware,
// + regresi bug window.X vs bare X, + sinkron prayerTimes/notifLog lintas device.
// Jalankan: node web/test-mqtt-web.js   (harus print "OK")
const fs = require('fs'), path = require('path'), assert = require('assert');

let published = [];
let handlers = {};
global.document = { addEventListener() {}, getElementById() { return null; } };
global.mqtt = { connect() { return { connected: true, on(ev, cb) { handlers[ev] = cb; }, subscribe() {}, publish(t, p) { published.push(p); }, end() {} }; } };
global.window = global;
global.navigator = {};
global.location = { href: '' };

// localStorage polyfill sederhana (tak ada bawaan di Node)
const _store = {};
global.localStorage = {
  getItem: k => (k in _store ? _store[k] : null),
  setItem: (k, v) => { _store[k] = String(v); },
};

// Meniru variabel state yg dideklarasikan `let`/`var` di index.html (bukan properti window).
global.currentWaterLevel = 0;
global.pumpStatus = false;
global.isOnline = false;
global.espSSIDValue = '';
global.prayerTimes = {};
global.updateUI = () => { global._uiCalled = true; };
global.renderPrayerTimes = () => { global._prayerRendered = true; };
global.updateNotifBadge = () => { global._badgeUpdated = true; };

eval(fs.readFileSync(path.join(__dirname, 'mqtt-web.js'), 'utf8'));

// mqtt-web.js sendiri mendefinisikan window.playAdzanWeb (pakai `new Audio`, tak ada di Node)
// SETELAH baris ini — override lagi di sini supaya bisa diverifikasi tanpa DOM Audio API.
global.playAdzanWeb = () => { global._adzanPlayed = (global._adzanPlayed || 0) + 1; };

MqttAndroid.connect();
const cases = [
  [() => MqttAndroid.send(true),  'ON'],
  [() => MqttAndroid.send(false), 'OFF'],
  [() => MqttAndroid.sendAutoOff(true),  'AUTO_ON'],
  [() => MqttAndroid.sendAutoOff(false), 'AUTO_OFF'],
  [() => MqttAndroid.sendAutoOffLevel(95), 'OFFLEVEL_95'],
  [() => MqttAndroid.sendAutoOnLevel(20),  'ONLEVEL_20'],
  [() => MqttAndroid.sendAutoOnEnabled(true),  'ONENABLED_'],
  [() => MqttAndroid.sendAutoOnEnabled(false), 'OFFENABLED_'],
  [() => MqttAndroid.sendSchedule('[]'), 'SCHEDULE_[]'],
  [() => MqttAndroid.sendBuzzer(5), 'BUZZER_5'],
  [() => MqttAndroid.savePrayerTimes('{"Subuh":"04:40"}'), 'PRAYER_{"Subuh":"04:40"}'],
];
for (const [fn, expect] of cases) { published = []; fn(); assert.strictEqual(published[0], expect, `expected ${expect} got ${published[0]}`); }

// Regresi: pesan status MQTT harus mengubah variabel HALAMAN (bare identifier), bukan window.*
// — inilah bug yang bikin web "keliatan" tak pernah online walau data sudah masuk.
handlers.message('smartsanyo/riyan123/status', Buffer.from(JSON.stringify({ waterLevel: 42, pumpStatus: true, ssid: 'RumahKu' })));
assert.strictEqual(currentWaterLevel, 42, 'currentWaterLevel tidak ter-update (window.X bug?)');
assert.strictEqual(pumpStatus, true, 'pumpStatus tidak ter-update');
assert.strictEqual(espSSIDValue, 'RumahKu', 'espSSIDValue tidak ter-update');
assert.strictEqual(isOnline, true, 'isOnline tidak ter-update (markOnline fallback bug?)');
assert.strictEqual(global._uiCalled, true, 'updateUI tidak dipanggil');

// Sinkron jadwal solat lintas device: status ESP bawa prayerTimes -> halaman ikut update
handlers.message('smartsanyo/riyan123/status', Buffer.from(JSON.stringify({ prayerTimes: { Subuh: '04:41' } })));
assert.deepStrictEqual(prayerTimes, { Subuh: '04:41' }, 'prayerTimes tidak sinkron dari ESP');
assert.strictEqual(JSON.parse(localStorage.getItem('prayerTimes')).Subuh, '04:41', 'prayerTimes tidak tersimpan localStorage');
assert.strictEqual(global._prayerRendered, true, 'renderPrayerTimes tidak dipanggil');

// Sinkron riwayat notifikasi lintas device: notifLog dari ESP -> masuk webNotifHistory + badge + azan
handlers.message('smartsanyo/riyan123/status', Buffer.from(JSON.stringify({
  notifLog: [
    { type: 'water_low', text: 'Air kritis - pompa ON otomatis', t: 1000000 },
    { type: 'prayer', text: 'Waktu Subuh telah tiba', t: 1000060 },
  ],
})));
let hist = JSON.parse(localStorage.getItem('webNotifHistory'));
assert.strictEqual(hist.length, 2, 'notifLog tidak masuk webNotifHistory');
assert.strictEqual(hist[0].type, 'water_low');
assert.strictEqual(hist[0].time, 1000000000, 'time harus epoch detik * 1000');
assert.strictEqual(global._badgeUpdated, true, 'updateNotifBadge tidak dipanggil');
assert.strictEqual(global._adzanPlayed, 1, 'playAdzanWeb tidak dipanggil utk entri prayer');

// Dedupe: notifLog yang SAMA datang lagi (mis. publish ulang tiap 3dtk) -> tidak dobel
handlers.message('smartsanyo/riyan123/status', Buffer.from(JSON.stringify({
  notifLog: [
    { type: 'water_low', text: 'Air kritis - pompa ON otomatis', t: 1000000 },
    { type: 'prayer', text: 'Waktu Subuh telah tiba', t: 1000060 },
  ],
})));
hist = JSON.parse(localStorage.getItem('webNotifHistory'));
assert.strictEqual(hist.length, 2, 'notifLog dobel — dedupe (type,menit) gagal');
assert.strictEqual(global._adzanPlayed, 1, 'playAdzanWeb terpanggil lagi utk entri yg sudah pernah — dedupe gagal');

// Hapus notif terpilih (by time) — v1.2.0
MqttAndroid.deleteNotifications(JSON.stringify([1000000000]));
hist = JSON.parse(localStorage.getItem('webNotifHistory'));
assert.strictEqual(hist.length, 1, 'deleteNotifications tidak menghapus entri terpilih');
assert.strictEqual(hist[0].time, 1000060000, 'entri yang tersisa salah');

console.log('OK');
