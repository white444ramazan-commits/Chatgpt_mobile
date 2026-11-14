package com.gorilla.control

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gorilla.control.root.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val bg = Color(0xFF090B10)
private val panel = Color(0xFF171C26)
private val accent = Color(0xFF8B5CF6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var rootStatus by remember { mutableStateOf("Не проверен") }
            val scope = rememberCoroutineScope()
            MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = accent)) {
                Column(
                    Modifier.fillMaxSize().background(bg).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("GORILLA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
                    Text("CONTROL  /  DEVICE COMPANION", color = Color.LightGray)
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("DEVICE STATUS", color = Color.LightGray)
                            Text("KernelSU / Root: $rootStatus", color = Color.White)
                            Button(
                                onClick = {
                                    rootStatus = "Проверка..."
                                    scope.launch {
                                        val detected = withContext(Dispatchers.IO) { RootStatus.check() }
                                        rootStatus = if (detected) "обнаружен" else "не обнаружен"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("CHECK ROOT") }
                        }
                    }
                    Card(
                        onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        colors = CardDefaults.cardColors(containerColor = panel),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("ACCESSIBILITY CONTROL", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Открыть настройки Android и включить службу вручную", color = Color.LightGray)
                        }
                    }
                    Card(
                        onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS)) },
                        colors = CardDefaults.cardColors(containerColor = panel),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("APPLICATIONS", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Открыть системные настройки приложений", color = Color.LightGray)
                        }
                    }
                    Text(
                        "Локальный режим. Удалённое управление и интеграция ChatGPT будут добавлены отдельным защищённым этапом.",
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}
