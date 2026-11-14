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

private val bg = Color(0xFF090B10)
private val panel = Color(0xFF171C26)
private val accent = Color(0xFF8B5CF6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var root by remember { mutableStateOf(RootStatus.check()) }
            MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = accent)) {
                Column(Modifier.fillMaxSize().background(bg).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("GORILLA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
                    Text("CONTROL  /  DEVICE COMPANION", color = Color.LightGray)
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("DEVICE STATUS", color = Color.LightGray)
                            Text(if (root) "KernelSU / Root: detected" else "KernelSU / Root: not detected", color = Color.White)
                            Button(onClick = { root = RootStatus.check() }, modifier = Modifier.fillMaxWidth()) { Text("CHECK ROOT") }
                        }
                    }
                    Card(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, colors = CardDefaults.cardColors(containerColor = panel)) {
                        Column(Modifier.padding(18.dp)) { Text("ACCESSIBILITY CONTROL", color = Color.White, fontWeight = FontWeight.Bold); Text("Open Android settings to enable the service", color = Color.LightGray) }
                    }
                    Card(onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS)) }, colors = CardDefaults.cardColors(containerColor = panel)) {
                        Column(Modifier.padding(18.dp)) { Text("APPLICATIONS", color = Color.White, fontWeight = FontWeight.Bold); Text("Open system app settings", color = Color.LightGray) }
                    }
                    Text("Local-first build. Remote control and ChatGPT integration are planned for a later secure milestone.", color = Color.LightGray)
                }
            }
        }
    }
}
