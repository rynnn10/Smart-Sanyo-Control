/*
 * 1. Buka Google Spreadsheet Anda > Ekstensi > Apps Script.
 * 2. Ganti semua kode lama dengan kode di bawah ini.
 * 3. Terapkan > Deployment Baru (Pastikan Anda membuat Deployment BARU setiap kali mengubah kode).
 * 4. Akses Webapp url yang baru dan perbarui di variabel `appsScriptUrl` pada file index.html Android jika URL berubah.
 */

function doGet(e) {
  var action = e.parameter.action;
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Dashboard");

  if (!action) {
    return ContentService.createTextOutput(
      JSON.stringify({ error: "No Action" }),
    ).setMimeType(ContentService.MimeType.JSON);
  }

  var props = PropertiesService.getScriptProperties();

  // --- CEK NOTIFIKASI GLOBAL (ESP8266 & MODE DEMO WEB) ---
  if (e.parameter.waterLevel !== undefined) {
    cekNotifikasiAir(e.parameter.waterLevel, props);
  }
  // --------------------------------------------------------

  // Jika SpreadSheet masih kosong, buat Header Otomatis
  if (sheet && sheet.getLastRow() === 0) {
    sheet.appendRow([
      "Timestamp",
      "Aktivitas Terbaru",
      "Status Pompa",
      "Mode Auto/Manual",
      "Level Air",
      "Penjadwalan",
      "Data Jadwal",
    ]);
    sheet.getRange("A1:G1").setFontWeight("bold").setBackground("#d9ead3");
    sheet.setFrozenRows(1); // Tetapkan header di atas jika discroll
    sheet.setColumnWidth(1, 160); // Timestamp
    sheet.setColumnWidth(2, 200); // Aktivitas
    // Dan lainnya
  }

  var logActivity = "";

  if (action === "set") {
    if (e.parameter.pumpStatus !== undefined) {
      props.setProperty("pumpStatus", e.parameter.pumpStatus);
      logActivity =
        "MENGUBAH STATUS POMPA: " + e.parameter.pumpStatus.toUpperCase();
    }
    if (e.parameter.autoMode !== undefined) {
      props.setProperty("autoMode", e.parameter.autoMode);
      logActivity =
        "MENGUBAH MODE: " +
        (e.parameter.autoMode === "true" ? "AUTO" : "MANUAL");
    }
    if (e.parameter.scheduleEnabled !== undefined) {
      props.setProperty("scheduleEnabled", e.parameter.scheduleEnabled);
      logActivity =
        (e.parameter.scheduleEnabled === "true"
          ? "MENGAKTIFKAN"
          : "MEMATIKAN") + " JADWAL";
    }
    if (e.parameter.schedules !== undefined) {
      props.setProperty("schedules", e.parameter.schedules);
      logActivity = "MEMPERBARUI DATA JADWAL";
    }
    if (e.parameter.autoOffLevel !== undefined) {
      props.setProperty("autoOffLevel", e.parameter.autoOffLevel);
      logActivity = "BATAS MATI OTOMATIS: " + e.parameter.autoOffLevel + "%";
    }
    if (e.parameter.autoOffEnabled !== undefined) {
      props.setProperty("autoOffEnabled", e.parameter.autoOffEnabled);
      logActivity = "AUTO-OFF: " + (e.parameter.autoOffEnabled === "true" ? "DIAKTIFKAN" : "DINONAKTIFKAN");
    }
  } else if (action === "sync" && e.parameter.waterLevel) {
    props.setProperty("waterLevel", e.parameter.waterLevel);
    if (e.parameter.ssid !== undefined) props.setProperty("ssid", e.parameter.ssid);
    if (e.parameter.rssi !== undefined) props.setProperty("rssi", e.parameter.rssi);
    // Tombol fisik ESP bisa update pumpStatus sekalian lewat sync
    if (e.parameter.pumpStatus !== undefined) props.setProperty("pumpStatus", e.parameter.pumpStatus);
    // Simpan waktu sync terakhir untuk cek isOnline real-time
    props.setProperty("lastSyncTime", new Date().getTime().toString());
    logActivity =
      "SINKRONISASI ESP/HW - LEVEL AIR: " + e.parameter.waterLevel + "%";
  }

  // Tarik semua status dari properti untuk dituliskan ke rapi ke spreadsheet
  var currentPump = props.getProperty("pumpStatus") === "true";
  var currentAuto = props.getProperty("autoMode") === "true";
  var currentLevel = props.getProperty("waterLevel") || "0";
  var currentScheduleState = props.getProperty("scheduleEnabled") === "true";
  var schedulesData = props.getProperty("schedules") || "[]";

  // Catat baris baru ke dalam Spreadsheet Dashboard jika ini bukan sekadar panggilan get (fetch UI)
  if (action === "set" || (action === "sync" && e.parameter.waterLevel)) {
    if (sheet) {
      var timestamp = Utilities.formatDate(
        new Date(),
        Session.getScriptTimeZone(),
        "dd/MM/yyyy HH:mm:ss",
      );
      sheet.appendRow([
        timestamp,
        logActivity,
        currentPump ? "ON" : "OFF",
        currentAuto ? "AUTO" : "MANUAL",
        currentLevel + "%",
        currentScheduleState ? "AKTIF" : "MATI",
        schedulesData,
      ]);
    }
  }

  // Mengembalikan respon (JSON) ke aplikasi Android/WEMOS
  if (action === "set") {
    return ContentService.createTextOutput(
      JSON.stringify({ success: true }),
    ).setMimeType(ContentService.MimeType.JSON);
  } else if (action === "sync") {
    // ESP8266 melaporkan water level dan meminta status
    if (e.parameter.waterLevel) {
      var currentLevel = e.parameter.waterLevel;
      props.setProperty("waterLevel", currentLevel);

      // --- TAMBAHKAN LOGIKA NOTIFIKASI AIR DI SINI ---
      var waterInt = parseInt(currentLevel);
      var now = new Date().getTime();

      // Pisahkan cooldown agar pengetesan Kritis -> Penuh tidak saling blokir (1 Jam = 3600000 ms)
      var lastKritis = parseInt(props.getProperty("lastKritisTime") || "0");
      var lastPenuh = parseInt(props.getProperty("lastPenuhTime") || "0");

      if (waterInt <= 10 && now - lastKritis > 3600000) {
        sendFCMNotification(
          "⚠️ Air Tangki Kritis!",
          "Sisa air tinggal " + waterInt + "%. Segera nyalakan Sanyo!",
        );
        props.setProperty("lastKritisTime", now.toString());
        props.setProperty("lastPenuhTime", "0"); // Reset cooldown penuh
      } else if (waterInt >= 90 && now - lastPenuh > 3600000) {
        sendFCMNotification(
          "✅ Tangki Air Penuh",
          "Air sudah mencapai " + waterInt + "%. Pompa bisa dimatikan.",
        );
        props.setProperty("lastPenuhTime", now.toString());
        props.setProperty("lastKritisTime", "0"); // Reset cooldown kritis
      } else if (waterInt > 10 && waterInt < 90) {
        // Jika air berada di batas normal (11% - 89%), langsung RESET semua cooldown
        // Ini memastikan notifikasi akan selalu menyala jika air tiba-tiba habis lagi
        props.setProperty("lastKritisTime", "0");
        props.setProperty("lastPenuhTime", "0");
      }
      // -----------------------------------------------
    }

    var currentPump = props.getProperty("pumpStatus") === "true";

    // ====================================================================
    // SISIPKAN LOGIKA PENJADWALAN & NOTIFIKASINYA DI SINI
    // ====================================================================
    var currentScheduleState = props.getProperty("scheduleEnabled") === "true";
    var schedulesData = props.getProperty("schedules") || "[]";

    // Jadwal: mendukung format baru { enabled, onTime, offTime, repeat }
    if (currentScheduleState && schedulesData !== "[]") {
      var schedArr = JSON.parse(schedulesData);
      var curTimeStr = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "HH:mm");
      var todayNum = new Date().getDay(); // 0=Min, 1=Sen, ..., 6=Sab

      for (var i = 0; i < schedArr.length; i++) {
        var sched = schedArr[i];
        // Dukung format baru (enabled) dan lama (isActive)
        var isActive = sched.enabled !== undefined ? sched.enabled : (sched.isActive || false);
        if (!isActive) continue;
        // Cek hari pengulangan
        if (sched.repeat && sched.repeat.length > 0 && sched.repeat.indexOf(todayNum) === -1) continue;

        // Format baru: onTime / offTime
        if (sched.onTime !== undefined && sched.onTime === curTimeStr && !currentPump) {
          currentPump = true;
          props.setProperty("pumpStatus", "true");
          logActivity = "JADWAL ON: " + sched.onTime;
          sendFCMNotification("⏰ Jadwal ON", "Sanyo otomatis MENYALA sesuai jadwal pukul " + sched.onTime);
        }
        if (sched.offTime !== undefined && sched.offTime === curTimeStr && currentPump) {
          currentPump = false;
          props.setProperty("pumpStatus", "false");
          logActivity = "JADWAL OFF: " + sched.offTime;
          sendFCMNotification("⏰ Jadwal OFF", "Sanyo otomatis MATI sesuai jadwal pukul " + sched.offTime);
        }

        // Format lama: time / action (kompatibilitas)
        if (sched.time !== undefined && sched.time === curTimeStr) {
          var actionToTake = sched.action === "ON";
          if (currentPump !== actionToTake) {
            currentPump = actionToTake;
            props.setProperty("pumpStatus", currentPump.toString());
            var teks = currentPump ? "MENYALA" : "MATI";
            logActivity = "JADWAL " + teks + ": " + sched.time;
            sendFCMNotification("⏰ Jadwal Tereksekusi", "Sanyo " + teks + " pukul " + sched.time);
          }
        }
      }
    }

    // Auto-off di sisi GAS: jika level air >= batas → matikan pompa
    var autoOffLevelVal = parseInt(props.getProperty("autoOffLevel") || "95");
    var autoOffEnabledVal = props.getProperty("autoOffEnabled") === "true";
    if (autoOffEnabledVal && parseInt(e.parameter.waterLevel) >= autoOffLevelVal && currentPump) {
      currentPump = false;
      props.setProperty("pumpStatus", "false");
      logActivity = "AUTO-OFF: Air " + e.parameter.waterLevel + "% >= " + autoOffLevelVal + "%";
      sendFCMNotification(
        "💧 Batas Air Tercapai",
        "Sanyo otomatis MATI. Air mencapai " + e.parameter.waterLevel + "% (batas " + autoOffLevelVal + "%)"
      );
    }
    // ====================================================================

    var autoOffLevelRet = parseInt(props.getProperty("autoOffLevel") || "95");
    var autoOffEnabledRet = props.getProperty("autoOffEnabled") === "true";
    return ContentService.createTextOutput(
      JSON.stringify({
        pumpStatus: currentPump,
        autoOffLevel: autoOffLevelRet,
        autoOffEnabled: autoOffEnabledRet,
        status: "OK",
      }),
    ).setMimeType(ContentService.MimeType.JSON);
  } else if (action === "get") {
    var ssid = props.getProperty("ssid") || "";
    var rssi = parseInt(props.getProperty("rssi") || "-100");
    var quality = rssi >= -50 ? 100 : rssi <= -100 ? 0 : Math.round(2 * (rssi + 100));
    var lastSync = parseInt(props.getProperty("lastSyncTime") || "0");
    var isOnline = (new Date().getTime() - lastSync) < 30000;
    var autoOffLevelGet = parseInt(props.getProperty("autoOffLevel") || "95");
    var autoOffEnabledGet = props.getProperty("autoOffEnabled") === "true";
    return ContentService.createTextOutput(
      JSON.stringify({
        pumpStatus: currentPump,
        autoMode: currentAuto,
        waterLevel: parseInt(currentLevel),
        scheduleEnabled: currentScheduleState,
        schedules: schedulesData,
        ssid: ssid,
        rssi: rssi,
        quality: quality,
        isOnline: isOnline,
        autoOffLevel: autoOffLevelGet,
        autoOffEnabled: autoOffEnabledGet,
      }),
    ).setMimeType(ContentService.MimeType.JSON);
  }
}

function sendFCMNotification(title, body) {
  // GANTI INI: Masukkan Project ID Firebase Anda (berupa teks)
  var projectId = "project-78a7877a-5b6d-46e2-ab9";

  // GANTI INI: Masukkan Token dari aplikasi Android Anda (dari Logcat VS Code)
  var fcmToken =
    "eNbKdVGFQRWUC0tLOjEMGS:APA91bGiC4xqHBNTUgghGGCSqUnhSq9h6HbdiWKvnfruA7f8IgetzbMwe_mdrUEBdUVwvKSMKvINF0Bk7ZjyxLL_mKvYhZQUi5ohLZrkUkebgQW4OaSCoSY";

  // Endpoint resmi API v1
  var url =
    "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";

  // Struktur payload khusus untuk v1 API
  var payload = {
    message: {
      token: fcmToken,
      notification: {
        title: title,
        body: body,
      },
    },
  };

  var options = {
    method: "post",
    headers: {
      // Mengambil kunci akses otomatis tanpa butuh file JSON
      Authorization: "Bearer " + ScriptApp.getOAuthToken(),
      "Content-Type": "application/json",
    },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true,
  };

  try {
    var response = UrlFetchApp.fetch(url, options);
    Logger.log("Hasil Notifikasi: " + response.getContentText());
  } catch (e) {
    Logger.log("Error Notifikasi: " + e);
  }
}

function tesNotifikasi() {
  // Pastikan fcmToken dan projectId di dalam fungsi sendFCMNotification sudah benar
  sendFCMNotification(
    "Uji Coba Sanyo",
    "Berhasil! Notifikasi Firebase sudah terhubung ke HP.",
  );
}

function cekNotifikasiAir(waterLevelStr, props) {
  var waterInt = parseInt(waterLevelStr);
  var now = new Date().getTime();

  var lastKritis = parseInt(props.getProperty("lastKritisTime") || "0");
  var lastPenuh = parseInt(props.getProperty("lastPenuhTime") || "0");

  if (waterInt <= 10 && now - lastKritis > 3600000) {
    sendFCMNotification(
      "⚠️ Air Tangki Kritis!",
      "Sisa air tinggal " + waterInt + "%. Segera nyalakan Sanyo!",
    );
    props.setProperty("lastKritisTime", now.toString());
    props.setProperty("lastPenuhTime", "0");
  } else if (waterInt >= 90 && now - lastPenuh > 3600000) {
    sendFCMNotification(
      "✅ Tangki Air Penuh",
      "Air sudah mencapai " + waterInt + "%. Pompa bisa dimatikan.",
    );
    props.setProperty("lastPenuhTime", now.toString());
    props.setProperty("lastKritisTime", "0");
  } else if (waterInt > 10 && waterInt < 90) {
    props.setProperty("lastKritisTime", "0");
    props.setProperty("lastPenuhTime", "0");
  }
}