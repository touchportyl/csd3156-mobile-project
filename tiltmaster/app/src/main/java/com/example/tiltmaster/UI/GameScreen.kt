package com.example.tiltmaster.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalContext
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

private fun updatePhysics(
    state: GameState,
    dt: Float,
    boundsWidth: Float,
    boundsHeight: Float
) {
    if (state.finished || state.failed) return

    state.elapsedSec += dt

    val accel = state.tiltAccel
    val damping = 0.98f
    val maxSpeed = 1200f

    state.ball.vel = Offset(
        state.ball.vel.x + accel.x * dt,
        state.ball.vel.y + accel.y * dt
    )

    val vx = clamp(state.ball.vel.x, -maxSpeed, maxSpeed)
    val vy = clamp(state.ball.vel.y, -maxSpeed, maxSpeed)
    state.ball.vel = Offset(vx, vy)

    var nextPos = state.ball.pos + state.ball.vel * dt

    val radius = state.ball.radius

    // ----------------------------
    // WALL COLLISION (unchanged)
    // ----------------------------
    for (w in state.walls) {

        val closestX = clamp(nextPos.x, w.rect.left, w.rect.right)
        val closestY = clamp(nextPos.y, w.rect.top, w.rect.bottom)

        val dx = nextPos.x - closestX
        val dy = nextPos.y - closestY

        val distSq = dx * dx + dy * dy

        if (distSq < radius * radius) {

            val dist = kotlin.math.sqrt(distSq)
            val nx = if (dist != 0f) dx / dist else 0f
            val ny = if (dist != 0f) dy / dist else 0f

            val penetration = radius - dist

            nextPos += Offset(nx * penetration, ny * penetration)

            val dot = state.ball.vel.x * nx + state.ball.vel.y * ny
            if (dot < 0f) {
                val restitution = 0.6f
                state.ball.vel = Offset(
                    state.ball.vel.x - (1f + restitution) * dot * nx,
                    state.ball.vel.y - (1f + restitution) * dot * ny
                )
            }
        }
    }

    // ----------------------------
    // ROUNDED SCREEN BOUNDARIES
    // ----------------------------

    val cornerRadius = 60f   // how rounded the screen corners feel
    val restitution = 0.6f

    fun resolveCollision(nx: Float, ny: Float, penetration: Float) {
        nextPos += Offset(nx * penetration, ny * penetration)

        val dot = state.ball.vel.x * nx + state.ball.vel.y * ny
        if (dot < 0f) {
            state.ball.vel = Offset(
                state.ball.vel.x - (1f + restitution) * dot * nx,
                state.ball.vel.y - (1f + restitution) * dot * ny
            )
        }
    }

    // Flat edges
    if (nextPos.x - radius < 0f)
        resolveCollision(1f, 0f, radius - nextPos.x)

    if (nextPos.x + radius > boundsWidth)
        resolveCollision(-1f, 0f, nextPos.x + radius - boundsWidth)

    if (nextPos.y - radius < 0f)
        resolveCollision(0f, 1f, radius - nextPos.y)

    if (nextPos.y + radius > boundsHeight)
        resolveCollision(0f, -1f, nextPos.y + radius - boundsHeight)

    // Rounded corners
    val corners = listOf(
        Offset(cornerRadius, cornerRadius),
        Offset(boundsWidth - cornerRadius, cornerRadius),
        Offset(cornerRadius, boundsHeight - cornerRadius),
        Offset(boundsWidth - cornerRadius, boundsHeight - cornerRadius)
    )

    for (corner in corners) {
        val dx = nextPos.x - corner.x
        val dy = nextPos.y - corner.y
        val distSq = dx * dx + dy * dy
        val maxDist = cornerRadius - radius

        if (distSq < maxDist * maxDist) {
            val dist = kotlin.math.sqrt(distSq)
            if (dist != 0f) {
                val nx = dx / dist
                val ny = dy / dist
                val penetration = maxDist - dist
                resolveCollision(nx, ny, penetration)
            }
        }
    }

    state.ball.pos = nextPos
    state.ball.vel *= damping

    // traps
    for (t in state.traps) {
        if (circleIntersectsRect(state.ball.pos, radius, t.rect)) {
            state.failed = true
            return
        }
    }

    // goal
    if (circleIntersectsRect(state.ball.pos, radius, state.goal.rect)) {
        state.finished = true
    }
}

@Composable
fun GameScreen(
    levelId: Int,
    onExit: () -> Unit
) {
    val density = LocalDensity.current

    val context = LocalContext.current

    var screenSize by remember { mutableStateOf(Size.Zero) }

    // filtered tilt from accelerometer, in -1..1 range
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    var sensorAvailable by remember { mutableStateOf(true) }

    // You’ll eventually load these from a Level loader
    var state by remember(levelId) {
        mutableStateOf(
        //val ball = Ball(pos = Offset(200f, 200f), vel = Offset.Zero, radius = 22f)
        GameState(
            levelId = levelId,
            ball = Ball(pos = Offset(200f, 200f), vel = Offset.Zero, radius = 22f),
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
        )
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            sensorAvailable = false
            return@DisposableEffect onDispose { }
        }

        sensorAvailable = true

        // Low-pass filter variables
        var filteredX = 0f
        var filteredY = 0f
        val alpha = 0.1f           // 0..1, lower = more smoothing
        val maxTilt = 10f          // same as TiltTestScreen

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val rawX = it.values[0]
                    val rawY = it.values[1]

                    // Low-pass filter
                    filteredX = alpha * rawX + (1f - alpha) * filteredX
                    filteredY = alpha * rawY + (1f - alpha) * filteredY

                    // Normalize to -1..1
                    tiltX = (filteredX / maxTilt).coerceIn(-1f, 1f)
                    tiltY = (filteredY / maxTilt).coerceIn(-1f, 1f)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
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
            //state.tiltAccel = Offset(0f, 0f)

            // Map tilt (-1..1) to game acceleration; tweak factor to taste
            val accelFactor = 900f   // pixels per second^2 per 1.0 tilt

            val newState = state.copy(
                tiltAccel = Offset(-tiltX * accelFactor, tiltY * accelFactor)
            )

            // Note: you can flip signs depending on feel
            state.tiltAccel = Offset(
                x = -tiltX * accelFactor,  // tilt right → ball moves right (adjust if opposite)
                y = tiltY * accelFactor    // tilt forward → ball moves down
            )

            updatePhysics(
                newState,
                dt.coerceIn(0f, 0.033f),
                screenSize.width,
                screenSize.height
            )

            state = newState

            // small delay to avoid maxing CPU; you can tune
            kotlinx.coroutines.delay(16)
        }
    }

    // UI overlay + canvas
    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()
                                  .onSizeChanged {
                                      screenSize = Size(it.width.toFloat(), it.height.toFloat())
                                  }
        ) {
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