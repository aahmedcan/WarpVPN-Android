package com.example.warp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.warp.ui.theme.WarpTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.warp.ui.viewmodel.WarpViewModel
import com.example.warp.ui.viewmodel.WarpViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSelector(
    selectedLocation: String,
    onLocationSelected: (String) -> Unit
) {
    val locations = listOf("New York", "London", "Tokyo", "Berlin")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLocation,
            onValueChange = {},
            readOnly = true,
            label = { Text("Server Location") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            locations.forEach { location ->
                DropdownMenuItem(
                    text = { Text(location) },
                    onClick = {
                        onLocationSelected(location)
                        expanded = false
                    }
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WarpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WarpVpnScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ignore Battery Optimizations?") },
        text = { Text("To ensure consistent background VPN connectivity, please whitelist this app from battery optimizations.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConnectionStatusBadge(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.DISCONNECTED -> Color.Gray
        ConnectionState.CONNECTING -> Color(0xFFFFC107) // Amber
        ConnectionState.CONNECTED -> Color(0xFF4CAF50)  // Green
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by if (state == ConnectionState.CONNECTING) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ), label = "pulse"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = alpha))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = state.name.lowercase().replaceFirstChar { it.uppercase() },
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun SessionTimer(seconds: Long) {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    val timeString = "%02d:%02d:%02d".format(hours, minutes, secs)
    Text(text = timeString, style = MaterialTheme.typography.headlineMedium)
}


@Composable
fun AutoConnectSetting(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = enabled, onCheckedChange = onToggle)
        Text("Auto-connect on launch")
    }
}

@Composable
fun SpeedTestPanel() {
    var speed by remember { mutableStateOf("Not tested") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Speed Test", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = {
                testing = true
                speed = "Testing..."
                scope.launch(Dispatchers.IO) {
                    // Simple download test
                    val startTime = System.currentTimeMillis()
                    try {
                        val connection = URL("https://www.google.com").openConnection()
                        connection.connectTimeout = 5000
                        connection.getInputStream().readBytes()
                        val duration = (System.currentTimeMillis() - startTime) / 1000.0
                        speed = "Completed in ${"%.2f".format(duration)}s"
                    } catch (e: Exception) {
                        speed = "Error"
                    } finally {
                        testing = false
                    }
                }
            },
            enabled = !testing
        ) {
            Text("Start Speed Test")
        }
        Text("Result: $speed")
    }
}

@Composable
fun WarpVpnScreen(modifier: Modifier = Modifier, viewModel: WarpViewModel = viewModel(factory = WarpViewModelFactory((LocalContext.current.applicationContext as WarpApplication).database.settingDao()))) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle(initialValue = false)
    var selectedLocation by remember { mutableStateOf("New York") }
    var elapsedTime by remember { mutableStateOf(0L) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    LaunchedEffect(Unit) {
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            showDialog = true
        }
        if (autoConnect) {
            // Auto connect logic
            state = ConnectionState.CONNECTING
            scope.launch {
                delay(2000)
                state = ConnectionState.CONNECTED
            }
        }
    }

    LaunchedEffect(state) {
        if (state == ConnectionState.CONNECTED) {
            val startTime = System.currentTimeMillis()
            while (state == ConnectionState.CONNECTED) {
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                delay(200)
            }
        } else {
            elapsedTime = 0
        }
    }

    if (showDialog) {
        BatteryOptimizationDialog(
            onDismiss = { showDialog = false },
            onConfirm = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = android.net.Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
                showDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LocationSelector(selectedLocation = selectedLocation, onLocationSelected = { selectedLocation = it })
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AutoConnectSetting(enabled = autoConnect, onToggle = { viewModel.setAutoConnect(it) })
        
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                ConnectionStatusBadge(state)
                if (state == ConnectionState.CONNECTED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SessionTimer(elapsedTime)
                }
            }
        }
        
        SpeedTestPanel()
        
        Button(
            onClick = {
                if (state == ConnectionState.DISCONNECTED) {
                    state = ConnectionState.CONNECTING
                    scope.launch {
                        delay(2000)
                        state = ConnectionState.CONNECTED
                    }
                } else if (state == ConnectionState.CONNECTED) {
                    state = ConnectionState.DISCONNECTED
                }
            },
            modifier = Modifier.padding(top = 16.dp),
            enabled = state != ConnectionState.CONNECTING
        ) {
            Text(if (state == ConnectionState.CONNECTED) "Disconnect" else "Connect")
        }
    }
}
