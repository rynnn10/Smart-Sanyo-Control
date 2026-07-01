// ============================================================
//  KONFIGURASI UTAMA — MQTT-only, no GAS/cloud
//  Update: 07/01/2026 05:25 - v2.2.0
// ============================================================
const APP_CONFIG = {
  mqttBroker: 'broker.emqx.io',
  mqttPort: 1883,
  mqttTopicStatus: 'smartsanyo/riyan123/status',
  mqttTopicControl: 'smartsanyo/riyan123/control',
  autoOffEnabled: true,
  autoOffLevel: 95
};