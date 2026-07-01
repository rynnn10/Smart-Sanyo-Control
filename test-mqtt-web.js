// Self-check: payload MqttAndroid shim == kontrak MqttBridge.kt / firmware.
// Jalankan: node web/test-mqtt-web.js   (harus print "OK")
const fs = require('fs'), path = require('path'), assert = require('assert');

let published = [];
global.document = { addEventListener() {}, getElementById() { return null; } };
global.mqtt = { connect() { return { connected: true, on() {}, subscribe() {}, publish(t, p) { published.push(p); }, end() {} }; } };
global.window = global;
global.navigator = {};
global.location = { href: '' };

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
console.log('OK');
