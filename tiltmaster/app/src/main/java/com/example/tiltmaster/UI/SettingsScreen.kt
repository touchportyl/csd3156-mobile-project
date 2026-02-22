package com.example.tiltmaster.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s = vm.settings.collectAsState().value

    var sensitivity by remember(s.sensitivity) { mutableFloatStateOf(s.sensitivity) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Tilt sensitivity: ${"%.2f".format(sensitivity)}")
            Slider(
                value = s.sensitivity,
                onValueChange = { vm.updateSensitivity(it) },
                valueRange = 0.4f..2.5f
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vibration")
                Switch(checked = s.vibrationEnabled, onCheckedChange = { vm.setVibration(it) })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sound")
                Switch(checked = s.soundEnabled, onCheckedChange = { vm.setSound(it) })
            }
        }
    }
}