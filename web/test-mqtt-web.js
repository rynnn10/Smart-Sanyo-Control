// Self-check: payload MqttAndroid shim == kontrak MqttBridge.kt / firmware,
// + regresi bug window.X vs bare X (lihat komentar applyStatus di mqtt-web.js).
// Jalankan: node web/test-mqtt-web.js   (harus print "OK")
const fs = require('fs'), path = require('path'), assert = require('assert');

let published = [];
let handlers = {};
global.document = { addEventListener() {}, getElementById() { return null; } };
global.mqtt = { connect() { return { connected: true, on(ev, cb) { handlers[ev] = cb; }, subscribe() {}, publish(t, p) { published.push(p); }, end() {} }; } };
global.window = global;
global.navigator = {};
global.location = { href: '' };

// Meniru variabel state yg dideklarasikan `let` di index.html (bukan properti window).
global.currentWaterLevel = 0;
global.pumpStatus = false;
global.isOnline = false;
global.espSSIDValue = '';
global.updateUI = () => { global._uiCalled = true; };

eval(fs.readFileSync(path.join(__dirname, 'mqtt-web.js'), 'utf8'));

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

console.log('OK');
