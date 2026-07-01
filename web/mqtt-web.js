// ============================================================
//  Smart Sanyo Control — Web MQTT shim (versi website)
//  Update: Rab 01/07/2026 12:51 - web v1.0.1
//  Meniru kontrak native `MqttAndroid` (MainActivity.kt) pakai mqtt.js over WSS,
//  supaya index.html yang sama jalan di browser tanpa diubah.
// ============================================================
(function () {
  var BROKER        = 'wss://broker.emqx.io:8084/mqtt'; // ponytail: WSS publik EMQX; ganti kalau pindah broker
  var TOPIC_STATUS  = 'smartsanyo/riyan123/status';
  var TOPIC_CONTROL = 'smartsanyo/riyan123/control';
  var client = null;

  function pub(payload) {
    try { if (client && client.connected) client.publish(TOPIC_CONTROL, payload); } catch (e) {}
  }

  // Sama persis dgn handler evaluateJavascript di MainActivity.kt (MQTT status → global UI).
  function applyStatus(str) {
    try {
      var d = JSON.parse(str);
      if (d.waterLevel   !== undefined) window.currentWaterLevel = parseInt(d.waterLevel);
      if (d.pumpStatus   !== undefined) window.pumpStatus     = d.pumpStatus;
      if (d.autoOffEnabled !== undefined) window.autoOffEnabled = d.autoOffEnabled;
      if (d.autoOffLevel !== undefined) window.autoOffLevel   = d.autoOffLevel;
      if (d.autoOnLevel  !== undefined) window.autoOnLevel    = d.autoOnLevel;
      if (d.ssid         !== undefined) window.espSSIDValue    = d.ssid;
      if (d.rssi         !== undefined) { window.espRssiValue = d.rssi; if (window.rssiToQuality) window.espQualValue = window.rssiToQuality(d.rssi); }
      if (d.hasSchedule  !== undefined) window.espHasSchedule  = d.hasSchedule;
      if (typeof window.markOnline === 'function') window.markOnline(); else window.isOnline = true;
      if (typeof window.updateUI === 'function') window.updateUI();
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
    var vn = document.getElementById('appVersion');    if (vn) vn.innerHTML  = 'web 1.0.1';
    var bt = document.getElementById('buildTimestamp'); if (bt) bt.innerText = 'Rab 01/07/2026 12:51 - web v1.0.1';
  });
})();
