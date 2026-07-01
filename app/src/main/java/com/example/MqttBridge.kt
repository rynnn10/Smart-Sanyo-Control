package com.example

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

// Update: 07/01/2026 05:29 - v2.2.0
// Tambah sendAutoOnEnabled untuk toggle auto-on terpisah (ONENABLED_ / OFFENABLED_)
class MqttBridge(private val onMessageCallback: (String) -> Unit) {
    companion object {
        private const val TAG = "MqttBridge"
        private const val BROKER = "tcp://broker.emqx.io:1883"
        private const val TOPIC_STATUS = "smartsanyo/riyan123/status"
        private const val TOPIC_CONTROL = "smartsanyo/riyan123/control"
    }

    private var client: MqttClient? = null
    private val clientId = "AndroidApp_" + (100000..999999).random()

    fun connect() {
        try {
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20
                userName = ""
                password = "".toCharArray()
            }

            client = MqttClient(BROKER, clientId, MemoryPersistence()).apply {
                setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "MQTT connection lost: ${cause?.message}")
                        // Auto reconnect setelah 5 detik
                        Thread.sleep(5000)
                        connect()
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.toString() ?: return
                        Log.d(TAG, "MQTT received [$topic]: $payload")
                        onMessageCallback(payload)
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        // Not needed
                    }
                })
                connect(options)
                subscribe(TOPIC_STATUS, 0)
                Log.d(TAG, "MQTT connected to $BROKER")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MQTT connect error: ${e.message}")
        }
    }

    fun sendCommand(pumpOn: Boolean) {
        try {
            val payload = if (pumpOn) "ON" else "OFF"
            val msg = MqttMessage(payload.toByteArray()).apply {
                qos = 0
            }
            client?.publish(TOPIC_CONTROL, msg)
            Log.d(TAG, "MQTT sent: ${if (pumpOn) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "MQTT send error: ${e.message}")
        }
    }

    fun sendAutoOffCommand(enabled: Boolean) {
        try {
            val payload = if (enabled) "AUTO_ON" else "AUTO_OFF"
            val msg = MqttMessage(payload.toByteArray()).apply {
                qos = 0
            }
            client?.publish(TOPIC_CONTROL, msg)
            Log.d(TAG, "MQTT sent autoOff: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "MQTT autoOff send error: ${e.message}")
        }
    }

    fun sendAutoOffLevel(level: Int) {
        try {
            val payload = "OFFLEVEL_$level"
            val msg = MqttMessage(payload.toByteArray()).apply { qos = 0 }
            client?.publish(TOPIC_CONTROL, msg)
            Log.d(TAG, "MQTT sent autoOffLevel: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "MQTT autoOffLevel send error: ${e.message}")
        }
    }

    fun sendAutoOnLevel(level: Int) {
        try {
            val payload = "ONLEVEL_$level"
            val msg = MqttMessage(payload.toByteArray()).apply { qos = 0 }
            client?.publish(TOPIC_CONTROL, msg)
            Log.d(TAG, "MQTT sent autoOnLevel: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "MQTT autoOnLevel send error: ${e.message}")
        }
    }

    fun sendAutoOnEnabled(enabled: Boolean) {
        try {
            val payload = if (enabled) "ONENABLED_" else "OFFENABLED_"
            val msg = MqttMessage(payload.toByteArray()).apply { qos = 0 }
            client?.publish(TOPIC_CONTROL, msg)
            Log.d(TAG, "MQTT sent autoOnEnabled: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "MQTT autoOnEnabled send error: ${e.message}")
        }
    }

    fun sendSchedule(json: String) {
        try {
            val payload = "SCHEDULE_$json"
            val msg = MqttMessage(payload.toByteArray()).apply { qos = 0 }
            client?.publish(TOPIC_CONTROL, msg)
            Log.d(TAG, "MQTT sent schedule")
        } catch (e: Exception) {
            Log.e(TAG, "MQTT schedule send error: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            client?.disconnect()
            client?.close()
        } catch (e: Exception) {
            Log.e(TAG, "MQTT disconnect error: ${e.message}")
        }
    }
}