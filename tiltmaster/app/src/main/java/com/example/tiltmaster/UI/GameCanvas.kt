package com.example.tiltmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.example.tiltmaster.PhysicsState

@Composable
fun GameCanvas(
    physicsState: PhysicsState,
    // optional: allow level drawing later without breaking existing calls
    walls: List<Rect> = emptyList(),
    traps: List<Rect> = emptyList(),
    goal: Rect? = null,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Walls
        for (w in walls) {
            drawRect(color = Color.DarkGray, topLeft = w.topLeft, size = w.size)
        }

        // Traps
        for (t in traps) {
            drawRect(color = Color.Red, topLeft = t.topLeft, size = t.size)
        }

        // Goal
        goal?.let {
            drawRect(color = Color.Green, topLeft = it.topLeft, size = it.size)
        }

        // Ball
        drawCircle(
            color = Color.Red,
            radius = 12f,
            center = Offset(physicsState.x, physicsState.y)
        )
    }
}