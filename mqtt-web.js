// ============================================================
//  Smart Sanyo Control — Web MQTT shim (versi website)
//  Update: Rab 01/07/2026 - web v1.2.0
//  Meniru kontrak native `MqttAndroid` (MainActivity.kt) pakai mqtt.js over WSS,
//  supaya index.html yang sama jalan di browser tanpa diubah.
//  v1.1.0: sinkron jadwal solat + riwayat notifikasi lintas device — ESP jadi sumber
//    bersama via status MQTT ("prayerTimes"+"notifLog"), sama seperti SanyoService.kt.
//  v1.2.0: deleteNotifications() — hapus notif terpilih dari webNotifHistory (paritas app).
// ============================================================
(function () {
  var BROKER        = 'wss://broker.emqx.io:8084/mqtt'; // ponytail: WSS publik EMQX; ganti kalau pindah broker
  var TOPIC_STATUS  = 'smartsanyo/riyan123/status';
  var TOPIC_CONTROL = 'smartsanyo/riyan123/control';
  var client = null;

  function pub(payload) {
    try { if (client && client.connected) client.publish(TOPIC_CONTROL, payload); } catch (e) {}
  }

  // ESP siarkan notifLog (~8 event terakhir) & prayerTimes di status yg sama — dedupe
  // via (type, menit) sama persis pola SanyoService.kt, tulis ke webNotifHistory yg
  // sudah dibaca renderNotifList()/updateNotifBadge() (index.html) tanpa perlu ubah UI.
  function seenKey(type, epochSec) { return type + '_' + Math.floor(epochSec / 60); }
  function getSeenSet() {
    try { return new Set(JSON.parse(localStorage.getItem('webNotifSeenKeys') || '[]')); } catch (e) { return new Set(); }
  }
  function saveSeenSet(s) {
    var a = Array.from(s);
    if (a.length > 40) a = a.slice(a.length - 40);
    try { localStorage.setItem('webNotifSeenKeys', JSON.stringify(a)); } catch (e) {}
  }
  function mergeNotifLog(arr) {
    if (!Array.isArray(arr) || !arr.length) return;
    var seen = getSeenSet();
    var hist; try { hist = JSON.parse(localStorage.getItem('webNotifHistory') || '[]'); } catch (e) { hist = []; }
    var changed = false;
    arr.forEach(function (e) {
      if (!e || !e.t) return; // t=0 -> ESP belum sync NTP saat event ini, lewati
      var key = seenKey(e.type, e.t);
      if (seen.has(key)) return;
      seen.add(key);
      var title = e.type === 'water_low' ? '⚠️ Air Hampir Habis!' :
                  e.type === 'water_high' ? '💧 Tangki Hampir Penuh' :
                  e.type === 'prayer' ? '🕌 Waktu Solat' : 'Notifikasi';
      hist.push({ title: title, text: e.text, type: e.type, time: e.t * 1000 });
      changed = true;
      if (e.type === 'prayer' && typeof playAdzanWeb === 'function') playAdzanWeb();
    });
    if (!changed) return;
    if (hist.length > 30) hist = hist.slice(hist.length - 30);
    try { localStorage.setItem('webNotifHistory', JSON.stringify(hist)); } catch (e) {}
    saveSeenSet(seen);
    if (typeof updateNotifBadge === 'function') updateNotifBadge();
  }

  // index.html mendeklarasikan currentWaterLevel dkk pakai `let` di top-level <script> —
  // `let` TIDAK jadi properti window, jadi assignment harus bare identifier (bukan window.X)
  // supaya nyambung ke scope skrip halaman yang sama. Ini yang bikin web "keliatan" tidak
  // terhubung walau pesan MQTT sudah diterima: window.currentWaterLevel dulu nulis ke
  // properti hantu yang tak pernah dibaca UI. Pola ini sama persis dgn evaluateJavascript
  // di MainActivity.kt (juga pakai bare identifier, bukan window.X).
  function applyStatus(str) {
    try {
      var d = JSON.parse(str);
      if (d.waterLevel     !== undefined) currentWaterLevel = parseInt(d.waterLevel);
      if (d.pumpStatus     !== undefined) pumpStatus = d.pumpStatus;
      if (d.autoOffEnabled !== undefined) autoOffEnabled = d.autoOffEnabled;
      if (d.autoOffLevel   !== undefined) autoOffLevel = d.autoOffLevel;
      if (d.autoOnLevel    !== undefined) autoOnLevel = d.autoOnLevel;
      if (d.ssid           !== undefined) espSSIDValue = d.ssid;
      if (d.rssi           !== undefined) { espRssiValue = d.rssi; if (typeof rssiToQuality === 'function') espQualValue = rssiToQuality(d.rssi); }
      if (d.hasSchedule    !== undefined) espHasSchedule = d.hasSchedule;
      if (d.prayerTimes    !== undefined) {
        prayerTimes = d.prayerTimes;
        try { localStorage.setItem('prayerTimes', JSON.stringify(d.prayerTimes)); } catch (e) {}
        if (typeof renderPrayerTimes === 'function') renderPrayerTimes();
      }
      if (d.notifLog !== undefined) mergeNotifLog(d.notifLog);
      if (typeof markOnline === 'function') markOnline(); else isOnline = true;
      if (typeof updateUI === 'function') updateUI();
    } catch (e) { console.error('MQTT web:', e); }
  }

  window.MqttAndroid = {
    connect: function () {
      if (client) { try { client.end(true); } catch (e) {} }
      var id = 'WebApp_' + Math.floor(100000 + Math.random() * 900000);
      client = mqtt.connect(BROKER, { clientId: id, clean: true, connectTimeout: 10000, keepalive: 20 });
      client.on('connect', function () { client.subscribe(TOPIC_STATUS); });
      client.on('message', function (t, payload) { applyStatus(payload.toString()); });
      client.on('error',   function (e) { console.error('MQTT err', e); });
    },
    send:              function (pumpOn)  { pub(pumpOn ? 'ON' : 'OFF'); },
    sendAutoOff:       function (enabled) { pub(enabled ? 'AUTO_ON' : 'AUTO_OFF'); },
    sendAutoOffLevel:  function (level)   { pub('OFFLEVEL_' + level); },
    sendAutoOnLevel:   function (level)   { pub('ONLEVEL_' + level); },
    sendAutoOnEnabled: function (enabled) { pub(enabled ? 'ONENABLED_' : 'OFFENABLED_'); },
    sendSchedule:      function (json)    { pub('SCHEDULE_' + json); },
    sendBuzzer:        function (count)   { pub('BUZZER_' + count); },
    savePrayerTimes:   function (json)    { try { localStorage.setItem('prayerTimes', json); } catch (e) {} pub('PRAYER_' + json); },
    getStoredNotifications: function ()   { try { return localStorage.getItem('webNotifHistory') || '[]'; } catch (e) { return '[]'; } },
    deleteNotifications: function (json) {
      try {
        var del = new Set(JSON.parse(json));
        var hist = JSON.parse(localStorage.getItem('webNotifHistory') || '[]');
        localStorage.setItem('webNotifHistory', JSON.stringify(hist.filter(function (n) { return !del.has(n.time); })));
      } catch (e) {}
    },
    shareApp: function () {
      if (navigator.share) navigator.share({ title: 'Smart Sanyo Control', text: 'Kontrol pompa air cerdas via MQTT', url: location.href });
      else if (navigator.clipboard) navigator.clipboard.writeText(location.href);
    },
    disconnect:  function () { try { if (client) client.end(true); } catch (e) {} },
    isConnected: function () { return !!(client && client.connected); }
  };

  // Web: azan pakai <audio> (app pakai MediaPlayer di service). File web/adzan.mp3 opsional.
  window.playAdzanWeb = function () {
    try { var a = new Audio('adzan.mp3'); a.play().catch(function () {}); } catch (e) {}
  };

  // App inject versi/timestamp via onPageFinished; di web set sendiri.
  document.addEventListener('DOMContentLoaded', function () {
    var vn = document.getElementById('appVersion');    if (vn) vn.innerHTML  = 'web 1.2.0';
    var bt = document.getElementById('buildTimestamp'); if (bt) bt.innerText = 'Rab 01/07/2026 - web v1.2.0';
  });
})();
