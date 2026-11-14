package com.gorilla.control

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
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
import com.gorilla.control.control.GorillaAccessibilityService
import com.gorilla.control.root.RootStatus
import com.gorilla.control.security.RestrictedSettingsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val bg = Color(0xFF090B10)
private val panel = Color(0xFF171C26)
private val accent = Color(0xFF8B5CF6)
private val muted = Color(0xFFADB5C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var rootStatus by remember { mutableStateOf("Не проверен") }
            var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled()) }
            var message by remember { mutableStateOf("Выбери действие ниже") }
            val scope = rememberCoroutineScope()

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = bg,
                    surface = panel,
                    primary = accent,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(bg)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "GORILLA",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        "CONTROL  /  REDMI PAD 2 PRO PROFILE",
                        color = muted,
                        style = MaterialTheme.typography.labelMedium
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = panel),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("СОСТОЯНИЕ УСТРОЙСТВА", color = muted)
                            StatusLine("KernelSU / Root", rootStatus)
                            StatusLine(
                                "Accessibility",
                                if (accessibilityEnabled) "Включён" else "Не включён"
                            )
                            OutlinedButton(
                                onClick = {
                                    rootStatus = "Проверка..."
                                    scope.launch {
                                        val detected = withContext(Dispatchers.IO) {
                                            RootStatus.check()
                                        }
                                        rootStatus = if (detected) {
                                            "Доступ Root обнаружен"
                                        } else {
                                            "Root не обнаружен или не разрешён"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ПРОВЕРИТЬ ROOT")
                            }
                            OutlinedButton(
                                onClick = { accessibilityEnabled = isAccessibilityEnabled() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ПРОВЕРИТЬ ACCESSIBILITY")
                            }
                        }
                    }

                    Text("НАСТРОЙКА ДОСТУПА", color = muted)

                    ActionCard(
                        title = "1. Подготовить Restricted Settings",
                        subtitle = "Попросить Android открыть системное объяснение блокировки"
                    ) {
                        val opened = RestrictedSettingsFlow.openSystemExplanation(this@MainActivity)
                        message = if (opened) {
                            "Системное окно вызвано. Прочитай его и подтверди переход к инструкции."
                        } else {
                            "HyperOS не открыл системное окно через этот интерфейс. Используй ручной порядок ниже."
                        }
                    }

                    ActionCard(
                        title = "2. Открыть сведения о GORILLA CONTROL",
                        subtitle = "После системного предупреждения проверь меню страницы приложения"
                    ) {
                        openAppInfo()
                    }

                    ActionCard(
                        title = "3. Открыть специальные возможности",
                        subtitle = "Вернуться к списку загруженных служб и включить GORILLA CONTROL"
                    ) {
                        openAccessibilitySettings()
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = panel),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("ПОРЯДОК ДЛЯ ANDROID 16", fontWeight = FontWeight.Bold)
                            Text(
                                "После шага 1 открой сведения о приложении. Если HyperOS покажет пункт «Разрешить ограниченные настройки», выбери его и подтверди блокировкой экрана. Затем вернись в специальные возможности и включи службу.",
                                color = muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Если этого пункта нет даже после системного предупреждения, прошивка не предоставляет штатный пользовательский переключатель для этого APK. Приложение не может выдать себе это разрешение.",
                                color = muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = panel),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("СТАТУС", color = muted)
                            Text(message, color = Color.White)
                        }
                    }

                    Text(
                        "Доступ Accessibility позволяет службе видеть часть содержимого интерфейса и выполнять действия от имени пользователя. Включай его только если доверяешь приложению. GORILLA CONTROL не включает доступ автоматически.",
                        color = muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(
            this,
            GorillaAccessibilityService::class.java
        ).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun openAppInfo() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = muted)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = panel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
