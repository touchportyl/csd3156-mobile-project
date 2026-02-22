package com.example.tiltmaster.UI

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.tiltmaster.PhysicsState

@Composable
fun GameCanvas(
    physicsState: PhysicsState,
    // later: maze data, goal, traps, etc.
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // TODO: draw maze walls, goal, traps here

        // Ball
        drawCircle(
            color = Color.Red,
            radius = 12f,
            center = Offset(physicsState.x, physicsState.y)
        )
    }
}
