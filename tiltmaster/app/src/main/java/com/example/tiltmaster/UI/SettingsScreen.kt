package com.example.tiltmaster.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var sensitivity by remember { mutableFloatStateOf(1.0f) }
    var vibration by remember { mutableStateOf(true) }
    var sound by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Tilt sensitivity: ${"%.2f".format(sensitivity)}")
            Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0.4f..2.5f)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vibration")
                Switch(checked = vibration, onCheckedChange = { vibration = it })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sound")
                Switch(checked = sound, onCheckedChange = { sound = it })
            }

            // later: save to Room
        }
    }
}