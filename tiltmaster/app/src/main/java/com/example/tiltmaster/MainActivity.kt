package com.example.tiltmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContent {
                Surface {
                    AppNavigation()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun AppNavigation() {
    var showTiltTest by remember { mutableStateOf(false) }

    if (showTiltTest) {
        TiltTestScreen(onBack = { showTiltTest = false })
    } else {
        GameScreen(onNavigateToTiltTest = { showTiltTest = true })
    }
}

@Composable
fun GameScreen(onNavigateToTiltTest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Tilt Master")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateToTiltTest) {
                Text("Test Tilt Input")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GameScreen(onNavigateToTiltTest = {})
}
