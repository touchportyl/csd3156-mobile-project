package com.example.tiltmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

// --- Simple data models (you can move these to game/ later) ---
data class Wall(val rect: Rect)
data class Trap(val rect: Rect)
data class Goal(val rect: Rect)

data class Ball(
    var pos: Offset,
    var vel: Offset,
    val radius: Float
)

data class GameState(
    val levelId: Int,
    var elapsedSec: Float = 0f,
    var finished: Boolean = false,
    var failed: Boolean = false,
    val ball: Ball,
    val walls: List<Wall>,
    val traps: List<Trap>,
    val goal: Goal,
    var tiltAccel: Offset = Offset.Zero // set by sensors later
)

// --- Core loop helpers ---
private fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))

private fun circleIntersectsRect(center: Offset, radius: Float, r: Rect): Boolean {
    val closestX = clamp(center.x, r.left, r.right)
    val closestY = clamp(center.y, r.top, r.bottom)
    val dx = center.x - closestX
    val dy = center.y - closestY
    return dx * dx + dy * dy <= radius * radius
}

private fun updatePhysics(state: GameState, dt: Float) {
    if (state.finished || state.failed) return

    state.elapsedSec += dt

    // “Tilt” acceleration -> velocity
    val accel = state.tiltAccel
    val damping = 0.98f
    val maxSpeed = 1200f

    state.ball.vel = Offset(
        (state.ball.vel.x + accel.x * dt),
        (state.ball.vel.y + accel.y * dt)
    )

    // clamp speed
    val vx = clamp(state.ball.vel.x, -maxSpeed, maxSpeed)
    val vy = clamp(state.ball.vel.y, -maxSpeed, maxSpeed)
    state.ball.vel = Offset(vx, vy)

    // integrate position
    var nextPos = state.ball.pos + state.ball.vel * dt

    // collide with walls: very simple response (push out + reflect axis)
    for (w in state.walls) {
        if (circleIntersectsRect(nextPos, state.ball.radius, w.rect)) {
            // crude separation: try resolve X then Y
            val tryX = Offset(state.ball.pos.x, nextPos.y)
            val tryY = Offset(nextPos.x, state.ball.pos.y)

            val hitX = circleIntersectsRect(tryY, state.ball.radius, w.rect) // moving X
            val hitY = circleIntersectsRect(tryX, state.ball.radius, w.rect) // moving Y

            if (hitX) state.ball.vel = Offset(-state.ball.vel.x * 0.6f, state.ball.vel.y)
            if (hitY) state.ball.vel = Offset(state.ball.vel.x, -state.ball.vel.y * 0.6f)

            nextPos = state.ball.pos // stop this frame
            break
        }
    }

    state.ball.pos = nextPos
    state.ball.vel = state.ball.vel * damping

    // traps
    for (t in state.traps) {
        if (circleIntersectsRect(state.ball.pos, state.ball.radius, t.rect)) {
            state.failed = true
            return
        }
    }

    // goal
    if (circleIntersectsRect(state.ball.pos, state.ball.radius, state.goal.rect)) {
        state.finished = true
    }
}

@Composable
fun GameScreen(
    levelId: Int,
    onExit: () -> Unit
) {
    val density = LocalDensity.current

    // You’ll eventually load these from a Level loader
    val state = remember(levelId) {
        val ball = Ball(pos = Offset(200f, 200f), vel = Offset.Zero, radius = 22f)
        GameState(
            levelId = levelId,
            ball = ball,
            walls = listOf(
                Wall(Rect(100f, 100f, 700f, 130f)),
                Wall(Rect(100f, 100f, 130f, 900f)),
                Wall(Rect(100f, 900f, 900f, 930f)),
                Wall(Rect(900f, 200f, 930f, 930f)),
                Wall(Rect(250f, 250f, 800f, 280f)),
            ),
            traps = listOf(
                Trap(Rect(450f, 500f, 520f, 570f))
            ),
            goal = Goal(Rect(820f, 820f, 900f, 900f))
        )
    }

    // Continuous game loop (ticks state, triggers redraw via state mutation)
    LaunchedEffect(levelId) {
        var last = System.nanoTime()
        while (isActive) {
            val now = System.nanoTime()
            val dt = (now - last) / 1_000_000_000f
            last = now

            // TODO: replace with real tilt sensor input
            // For now: no accel -> ball stops unless you add input
            state.tiltAccel = Offset(0f, 0f)

            updatePhysics(state, dt.coerceIn(0f, 0.033f))
            // small delay to avoid maxing CPU; you can tune
            kotlinx.coroutines.delay(16)
        }
    }

    // UI overlay + canvas
    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw walls
            for (w in state.walls) drawRect(color = androidx.compose.ui.graphics.Color.DarkGray, topLeft = w.rect.topLeft, size = w.rect.size)

            // Draw traps
            for (t in state.traps) drawRect(color = androidx.compose.ui.graphics.Color.Red, topLeft = t.rect.topLeft, size = t.rect.size)

            // Draw goal
            drawRect(color = androidx.compose.ui.graphics.Color.Green, topLeft = state.goal.rect.topLeft, size = state.goal.rect.size)

            // Draw ball
            drawCircle(
                color = androidx.compose.ui.graphics.Color.Cyan,
                radius = state.ball.radius,
                center = state.ball.pos,
                style = Fill
            )
        }

        // Overlay UI
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level ${state.levelId}", style = MaterialTheme.typography.titleMedium)
                Text("Time: ${"%.2f".format(state.elapsedSec)}s", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onExit) { Text("Exit") }

                if (state.finished) {
                    Text("Finished!", style = MaterialTheme.typography.titleMedium)
                } else if (state.failed) {
                    Text("Hit trap!", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}