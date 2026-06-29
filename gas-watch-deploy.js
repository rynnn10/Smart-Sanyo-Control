/**
 * Watch-deploy script untuk clasp + Google Apps Script.
 *
 * Memantau perubahan pada kode.gs dan appsscript.json,
 * lalu otomatis push + deploy ke deployment yang sama.
 *
 * URL Web App TETAP sama karena deploy ke deployment ID yang ada (-i).
 *
 * Usage:
 *   node gas-watch-deploy.js
 *   (atau via npm: npm run gas:watch-deploy)
 */

const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

// Load .env
let envPath = path.join(__dirname, ".env");
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, "utf8");
  envContent.split("\n").forEach((line) => {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith("#")) {
      const eqIdx = trimmed.indexOf("=");
      if (eqIdx > 0) {
        const key = trimmed.substring(0, eqIdx).trim();
        const val = trimmed.substring(eqIdx + 1).trim();
        if (key && val) {
          process.env[key] = val;
          console.log(`  ${key}=${val.substring(0, 20)}${val.length > 20 ? "..." : ""}`);
        }
      }
    }
  });
}

const deploymentId = process.env.CLASP_DEPLOYMENT_ID || "";
const deployFlag = deploymentId ? `-i ${deploymentId}` : "";

if (!deploymentId) {
  console.warn("⚠️  CLASP_DEPLOYMENT_ID tidak ditemukan di .env!");
  console.warn("   Setiap deploy akan membuat deployment BARU (URL berubah).");
  console.warn("   Isi .env dengan: CLASP_DEPLOYMENT_ID=token_dari_url_web_app");
} else {
  console.log(`✅ Deployment ID: ${deploymentId}`);
}

console.log("👀 Watch-deploy aktif. Memantau: kode.gs, appsscript.json");
console.log("   Setiap perubahan → clasp push + clasp deploy (URL tetap)");
console.log("⚠️  Tekan Ctrl+C untuk berhenti.");
console.log("");

// Debounce & queue
const DEBOUNCE_MS = 1000;
const debounceTimers = {};
let queue = Promise.resolve();

function doPushAndDeploy(filePath) {
  queue = queue
    .then(() => {
      return new Promise((resolve) => {
        console.log(`📤 [${new Date().toLocaleTimeString()}] Perubahan: ${path.basename(filePath)}`);

        try {
          console.log("   → Push ke editor Apps Script...");
          const pushOut = execSync("npx clasp push", {
            encoding: "utf8",
            cwd: __dirname,
          });
          console.log(pushOut.trim() || "     (OK)");
        } catch (e) {
          console.error("   ❌ Push gagal:", e.stderr?.trim() || e.message);
          resolve();
          return;
        }

        try {
          console.log("   → Deploy ke Web App...");
          const deployCmd = `npx clasp deploy ${deployFlag}`.trim();
          const deployOut = execSync(deployCmd, {
            encoding: "utf8",
            cwd: __dirname,
          });
          console.log(deployOut.trim());
        } catch (e) {
          console.error("   ❌ Deploy gagal:", e.stderr?.trim() || e.message);
        }

        // Delay kecil antar antrian
        setTimeout(resolve, 500);
      });
    })
    .catch((err) => {
      console.error("   ❌ Error:", err.message);
    });
}

// Gunakan fs.watch sebagai fallback (built-in, no chokidar needed)
const filesToWatch = ["kode.gs", "appsscript.json"];

filesToWatch.forEach((file) => {
  const filePath = path.join(__dirname, file);
  if (!fs.existsSync(filePath)) {
    console.warn(`⚠️  File tidak ditemukan (akan dibuat otomatis oleh clasp): ${file}`);
    return;
  }

  fs.watch(filePath, (eventType) => {
    if (eventType !== "change" && eventType !== "rename") return;

    // Debounce: reset timer tiap perubahan
    if (debounceTimers[file]) clearTimeout(debounceTimers[file]);
    debounceTimers[file] = setTimeout(() => {
      doPushAndDeploy(filePath);
    }, DEBOUNCE_MS);
  });

  console.log(`   ✓ Memantau: ${file}`);
});

// Jaga proses tetap berjalan
process.on("SIGINT", () => {
  console.log("\n👋 Watch-deploy dihentikan.");
  process.exit(0);
});

console.log("\n✅ Siap! Tunggu perubahan file...");