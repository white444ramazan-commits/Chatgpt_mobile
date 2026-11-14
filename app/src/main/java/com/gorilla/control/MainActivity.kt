package com.gorilla.control

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gorilla.control.root.DeviceResolution
import com.gorilla.control.root.RootController
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
            var resolution by remember { mutableStateOf<DeviceResolution?>(null) }
            var message by remember { mutableStateOf("Нажми «Проверить Root» и одобри запрос KernelSU") }
            var x by remember { mutableStateOf("500") }
            var y by remember { mutableStateOf("500") }
            var x1 by remember { mutableStateOf("500") }
            var y1 by remember { mutableStateOf("1200") }
            var x2 by remember { mutableStateOf("500") }
            var y2 by remember { mutableStateOf("400") }
            var duration by remember { mutableStateOf("350") }
            var packageName by remember { mutableStateOf("com.android.settings") }
            var screenshot by remember { mutableStateOf<Bitmap?>(null) }
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
                    Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("GORILLA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
                    Text("ROOT CONTROL  /  REDMI PAD 2 PRO", color = muted, style = MaterialTheme.typography.labelMedium)

                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("СОСТОЯНИЕ УСТРОЙСТВА", color = muted)
                            StatusLine("KernelSU / Root", rootStatus)
                            StatusLine("Разрешение экрана", resolution?.toString() ?: "Не определено")
                            Button(
                                enabled = !busy,
                                onClick = {
                                    runTask("Проверяю Root…") {
                                        val result = withContext(Dispatchers.IO) {
                                            val detected = RootController.hasRoot()
                                            val size = if (detected) RootController.getResolution() else null
                                            detected to size
                                        }
                                        rootStatus = if (result.first) "Доступ Root предоставлен" else "Root не предоставлен"
                                        resolution = result.second
                                        if (result.first) "Root работает. Разрешение: ${result.second}" else "KernelSU не предоставил Root-доступ"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("ПРОВЕРИТЬ ROOT") }
                        }
                    }

                    Text("УПРАВЛЕНИЕ ЭКРАНОМ", color = muted)
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Нажатие", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CoordinateField("X", x, { x = it }, Modifier.weight(1f))
                                CoordinateField("Y", y, { y = it }, Modifier.weight(1f))
                            }
                            Button(
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val px = x.toIntOrNull()
                                    val py = y.toIntOrNull()
                                    if (px == null || py == null) message = "Введи числовые координаты"
                                    else runTask("Выполняю нажатие…") {
                                        withContext(Dispatchers.IO) { RootController.tap(px, py) }
                                        "Нажатие выполнено: $px, $py"
                                    }
                                }
                            ) { Text("ВЫПОЛНИТЬ TAP") }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Свайп", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CoordinateField("X1", x1, { x1 = it }, Modifier.weight(1f))
                                CoordinateField("Y1", y1, { y1 = it }, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CoordinateField("X2", x2, { x2 = it }, Modifier.weight(1f))
                                CoordinateField("Y2", y2, { y2 = it }, Modifier.weight(1f))
                            }
                            CoordinateField("Время, мс (80–2000)", duration, { duration = it }, Modifier.fillMaxWidth())
                            Button(
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val values = listOf(x1, y1, x2, y2, duration).map { it.toIntOrNull() }
                                    if (values.any { it == null }) message = "Проверь координаты и длительность"
                                    else runTask("Выполняю свайп…") {
                                        withContext(Dispatchers.IO) {
                                            RootController.swipe(values[0]!!, values[1]!!, values[2]!!, values[3]!!, values[4]!!)
                                        }
                                        "Свайп выполнен"
                                    }
                                }
                            ) { Text("ВЫПОЛНИТЬ SWIPE") }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Запуск приложения", fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = packageName,
                                onValueChange = { packageName = it },
                                label = { Text("Имя пакета") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    runTask("Запускаю приложение…") {
                                        withContext(Dispatchers.IO) { RootController.launchApp(packageName.trim()) }
                                        "Запуск отправлен: ${packageName.trim()}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("ЗАПУСТИТЬ") }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Снимок экрана", fontWeight = FontWeight.Bold)
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    runTask("Получаю снимок экрана через Root…") {
                                        val bytes = withContext(Dispatchers.IO) { RootController.captureScreen() }
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            ?: throw IllegalStateException("Не удалось декодировать PNG")
                                        screenshot = bitmap
                                        "Снимок экрана получен (${bitmap.width} × ${bitmap.height})"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("СДЕЛАТЬ СКРИНШОТ") }
                            screenshot?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Снимок экрана устройства",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("СТАТУС", color = muted)
                            Text(message, color = Color.White)
                        }
                    }

                    Text(
                        "Root-команды запускаются только после действия пользователя и разрешения KernelSU. В приложение не встроен произвольный удалённый shell. Не отправляй команды на устройства, которыми не владеешь.",
                        color = muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CoordinateField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.length <= 6 && next.all(Char::isDigit)) onValueChange(next) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = muted)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
