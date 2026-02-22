package com.example.tiltmaster.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tilt Maze Ball", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth(0.6f)) { Text("Play") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth(0.6f)) { Text("Settings") }
        }
    }
}