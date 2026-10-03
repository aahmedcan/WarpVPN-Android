package com.example.warp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                WarpVpnScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun WarpVpnScreen(modifier: Modifier = Modifier) {
    var isConnected by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("WarpVPN Connection Status: ${if (isConnected) "Connected" else "Disconnected"}")
        Button(onClick = { isConnected = !isConnected }, modifier = Modifier.padding(top = 16.dp)) {
            Text(if (isConnected) "Disconnect" else "Connect")
        }
    }
}
