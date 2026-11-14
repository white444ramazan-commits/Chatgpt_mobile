package com.gorilla.control

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gorilla.control.remote.DeviceApi
import com.gorilla.control.remote.DeviceLinkService
import com.gorilla.control.remote.DevicePrefs
import com.gorilla.control.root.RootController
import com.gorilla.control.root.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val bg = Color(0xFF090B10)
private val panel = Color(0xFF171C26)
private val accent = Color(0xFF8B5CF6)
private val muted = Color(0xFFADB5C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = getSharedPreferences(DevicePrefs.FILE, MODE_PRIVATE)
            var rootStatus by remember { mutableStateOf("Не проверен") }
            var serverUrl by remember { mutableStateOf(prefs.getString(DevicePrefs.SERVER_URL, "") ?: "") }
            var pairingCode by remember { mutableStateOf("") }
            var linked by remember { mutableStateOf(!prefs.getString(DevicePrefs.DEVICE_TOKEN, null).isNullOrBlank()) }
            var connectionState by remember { mutableStateOf(prefs.getString(DevicePrefs.CONNECTION_STATE, "Отключено") ?: "Отключено") }
            var pendingJson by remember { mutableStateOf(prefs.getString(DevicePrefs.PENDING_COMMAND, null)) }
            var message by remember { mutableStateOf("Локальное управление доступно через KernelSU.") }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            fun runTask(label: String, task: suspend () -> String) {
                if (busy) return
                scope.launch {
                    busy = true
                    message = label
                    try {
                        message = task()
                    } catch (e: Exception) {
                        message = e.message ?: "Операция не выполнена"
                    } finally {
                        busy = false
                    }
                }
            }

            LaunchedEffect(Unit) {
                while (isActive) {
                    pendingJson = prefs.getString(DevicePrefs.PENDING_COMMAND, null)
                    connectionState = prefs.getString(DevicePrefs.CONNECTION_STATE, "Отключено") ?: "Отключено"
                    linked = !prefs.getString(DevicePrefs.DEVICE_TOKEN, null).isNullOrBlank()
                    delay(900)
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = bg, surface = panel, primary = accent,
                    onBackground = Color.White, onSurface = Color.White
                )
            ) {
                Column(
                    Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("GORILLA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
                    Text("CONTROL  /  SECURE DEVICE LINK", color = muted, style = MaterialTheme.typography.labelMedium)

                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("СОСТОЯНИЕ УСТРОЙСТВА", color = muted)
                            StatusLine("KernelSU / Root", rootStatus)
                            StatusLine("Связь с MCP", connectionState)
                            Button(
                                enabled = !busy,
                                onClick = {
                                    runTask("Проверяю Root…") {
                                        val detected = withContext(Dispatchers.IO) { RootStatus.check() }
                                        rootStatus = if (detected) "Доступ Root обнаружен" else "Root не обнаружен или не разрешён"
                                        rootStatus
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("ПРОВЕРИТЬ ROOT") }
                        }
                    }

                    Text("ПОДКЛЮЧЕНИЕ CHATGPT", color = muted)
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = { Text("HTTPS-адрес GORILLA MCP Worker") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!linked) {
                                OutlinedTextField(
                                    value = pairingCode,
                                    onValueChange = { pairingCode = it },
                                    label = { Text("DEVICE_PAIRING_CODE") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !busy && serverUrl.isNotBlank() && pairingCode.isNotBlank(),
                                    onClick = {
                                        runTask("Сопрягаю планшет…") {
                                            val paired = withContext(Dispatchers.IO) {
                                                DeviceApi.pair(serverUrl, pairingCode)
                                            }
                                            prefs.edit()
                                                .putString(DevicePrefs.SERVER_URL, serverUrl.trim().trimEnd('/'))
                                                .putString(DevicePrefs.DEVICE_TOKEN, paired.token)
                                                .putBoolean("paired", true)
                                                .apply()
                                            pairingCode = ""
                                            linked = true
                                            DeviceLinkService.start(this@MainActivity)
                                            "Планшет спарен. Запущено защищённое HTTPS-соединение."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("PAIR AND CONNECT") }
                            } else {
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        runTask("Запускаю защищённый канал…") {
                                            prefs.edit().putString(DevicePrefs.SERVER_URL, serverUrl.trim().trimEnd('/')).apply()
                                            DeviceLinkService.start(this@MainActivity)
                                            "Канал запускается. Оставь уведомление GORILLA CONTROL включённым."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("ПОДКЛЮЧИТЬ") }
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = {
                                        DeviceLinkService.stop(this@MainActivity)
                                        prefs.edit()
                                            .remove(DevicePrefs.DEVICE_TOKEN)
                                            .remove(DevicePrefs.PENDING_COMMAND)
                                            .putString(DevicePrefs.CONNECTION_STATE, "Отключено")
                                            .apply()
                                        linked = false
                                        connectionState = "Отключено"
                                        message = "Устройство отключено и отвязано от текущего токена."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("ОТКЛЮЧИТЬ И ОТВЯЗАТЬ") }
                            }
                            Text(
                                "Для сопряжения сервер должен быть развёрнут. Pairing code хранится как секрет Cloudflare Worker и вводится только здесь.",
                                color = muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    pendingJson?.let { raw ->
                        val command = runCatching { JSONObject(raw) }.getOrNull()
                        if (command != null) {
                            val commandId = command.optString("id")
                            val commandName = command.optString("name")
                            val payload = command.optJSONObject("payload") ?: JSONObject()
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF272034)), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("ЗАПРОС ОЖИДАЕТ ТВОЕГО ПОДТВЕРЖДЕНИЯ", color = Color(0xFFD7C5FF), fontWeight = FontWeight.Bold)
                                    Text("Команда: $commandName", color = Color.White)
                                    Text(payload.toString(), color = muted)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = !busy,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                runTask("Выполняю подтверждённую команду…") {
                                                    val base = prefs.getString(DevicePrefs.SERVER_URL, null)
                                                        ?: throw IllegalStateException("Адрес сервера не задан")
                                                    val token = prefs.getString(DevicePrefs.DEVICE_TOKEN, null)
                                                        ?: throw IllegalStateException("Устройство не спарено")
                                                    try {
                                                        val resultMessage = withContext(Dispatchers.IO) {
                                                            when (commandName) {
                                                                "tap" -> {
                                                                    RootController.tap(payload.getInt("x"), payload.getInt("y"))
                                                                    "Нажатие выполнено"
                                                                }
                                                                "swipe" -> {
                                                                    RootController.swipe(
                                                                        payload.getInt("x1"), payload.getInt("y1"),
                                                                        payload.getInt("x2"), payload.getInt("y2"),
                                                                        payload.optInt("durationMs", 350)
                                                                    )
                                                                    "Свайп выполнен"
                                                                }
                                                                "launch_app" -> {
                                                                    val packageName = payload.getString("packageName")
                                                                    RootController.launchApp(packageName)
                                                                    "Запуск приложения отправлен: $packageName"
                                                                }
                                                                else -> throw IllegalArgumentException("Неизвестный тип команды")
                                                            }
                                                        }
                                                        withContext(Dispatchers.IO) {
                                                            DeviceApi.submitResult(base, token, commandId, "completed", JSONObject().put("message", resultMessage))
                                                        }
                                                        prefs.edit().remove(DevicePrefs.PENDING_COMMAND).apply()
                                                        resultMessage
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.IO) {
                                                            runCatching {
                                                                DeviceApi.submitResult(base, token, commandId, "failed", JSONObject().put("error", e.message ?: "Execution failed"))
                                                            }
                                                        }
                                                        prefs.edit().remove(DevicePrefs.PENDING_COMMAND).apply()
                                                        throw e
                                                    }
                                                }
                                            }
                                        ) { Text("РАЗРЕШИТЬ") }
                                        OutlinedButton(
                                            enabled = !busy,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                runTask("Отклоняю запрос…") {
                                                    val base = prefs.getString(DevicePrefs.SERVER_URL, null)
                                                        ?: throw IllegalStateException("Адрес сервера не задан")
                                                    val token = prefs.getString(DevicePrefs.DEVICE_TOKEN, null)
                                                        ?: throw IllegalStateException("Устройство не спарено")
                                                    withContext(Dispatchers.IO) {
                                                        DeviceApi.submitResult(base, token, commandId, "rejected", JSONObject().put("message", "Rejected by device owner"))
                                                    }
                                                    prefs.edit().remove(DevicePrefs.PENDING_COMMAND).apply()
                                                    "Запрос отклонён."
                                                }
                                            }
                                        ) { Text("ОТКЛОНИТЬ") }
                                    }
                                }
                            }
                        }
                    }

                    Text("СТАТУС", color = muted)
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) { Text(message, color = Color.White) }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ЗАЩИТА", fontWeight = FontWeight.Bold)
                            Text(
                                "ChatGPT может только поставить команду в очередь. TAP, SWIPE и запуск приложений выполняются только после нажатия «РАЗРЕШИТЬ» здесь. Произвольной удалённой shell-команды нет.",
                                color = muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:$packageName"))) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("СВЕДЕНИЯ О ПРИЛОЖЕНИИ") }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = muted)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
