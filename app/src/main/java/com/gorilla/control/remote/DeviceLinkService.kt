package com.gorilla.control.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.gorilla.control.MainActivity
import com.gorilla.control.R
import com.gorilla.control.root.RootController
import com.gorilla.control.root.RootStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceLinkService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification("Защищённое соединение готовится")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (pollJob?.isActive != true) {
            pollJob = serviceScope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        val prefs = getSharedPreferences(DevicePrefs.FILE, Context.MODE_PRIVATE)
        var lastHeartbeatAt = 0L
        var rootAvailable = false
        var rootChecked = false
        var lastNotifiedCommand = ""

        while (serviceScope.isActive) {
            try {
                val baseUrl = prefs.getString(DevicePrefs.SERVER_URL, null)
                    ?: throw IllegalStateException("Не задан адрес сервера")
                val token = prefs.getString(DevicePrefs.DEVICE_TOKEN, null)
                    ?: throw IllegalStateException("Устройство не спарено")

                if (!rootChecked) {
                    rootAvailable = withContext(Dispatchers.IO) { RootStatus.check() }
                    rootChecked = true
                }
                if (System.currentTimeMillis() - lastHeartbeatAt >= 15_000) {
                    val resolution = if (rootAvailable) {
                        runCatching { withContext(Dispatchers.IO) { RootController.getResolution() } }.getOrNull()
                    } else null
                    DeviceApi.heartbeat(
                        baseUrl, token, rootAvailable,
                        resolution?.width, resolution?.height
                    )
                    lastHeartbeatAt = System.currentTimeMillis()
                }

                val command = DeviceApi.nextCommand(baseUrl, token)
                if (command != null) {
                    val commandId = command.optString("id")
                    prefs.edit()
                        .putString(DevicePrefs.PENDING_COMMAND, command.toString())
                        .putString(DevicePrefs.CONNECTION_STATE, "Подключено · ожидает подтверждения")
                        .apply()
                    if (commandId.isNotBlank() && commandId != lastNotifiedCommand) {
                        lastNotifiedCommand = commandId
                        updateNotification("Есть запрос на управление — открой приложение")
                    }
                } else {
                    prefs.edit().putString(DevicePrefs.CONNECTION_STATE, "Подключено · ожидание команд").apply()
                    lastNotifiedCommand = ""
                    updateNotification("Подключено к GORILLA MCP")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                prefs.edit()
                    .putString(DevicePrefs.CONNECTION_STATE, "Нет связи: ${error.message ?: "ошибка сети"}")
                    .apply()
                updateNotification("Нет связи с сервером · повторная попытка")
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "GORILLA CONTROL connection",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Показывает активность защищённого соединения с вашим Worker."
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)

        return builder
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("GORILLA CONTROL")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        pollJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "gorilla_device_link"
        private const val NOTIFICATION_ID = 4301
        private const val POLL_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, DeviceLinkService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DeviceLinkService::class.java))
        }
    }
}
