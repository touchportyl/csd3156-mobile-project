

package com.example.tiltmaster.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.example.tiltmaster.ViewModel.GameViewModel
import kotlinx.coroutines.delay
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

    // Harder levels: more accel, less damping, higher max speed
    val levelFactor = (state.levelId - 1).coerceIn(0, 4) // 0..4
    val damping = (0.985f - levelFactor * 0.002f).coerceIn(0.972f, 0.985f)
    val maxSpeed = (1100f + levelFactor * 120f)

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
    // (these are in "boundsWidth/Height" coords)
    // ----------------------------
    val cornerRadius = 60f
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
    if (nextPos.x - radius < 0f) resolveCollision(1f, 0f, radius - nextPos.x)
    if (nextPos.x + radius > boundsWidth) resolveCollision(-1f, 0f, nextPos.x + radius - boundsWidth)
    if (nextPos.y - radius < 0f) resolveCollision(0f, 1f, radius - nextPos.y)
    if (nextPos.y + radius > boundsHeight) resolveCollision(0f, -1f, nextPos.y + radius - boundsHeight)

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
/**
 * 5 levels
 */
private fun buildLevel(levelId: Int): Triple<List<Wall>, List<Trap>, Goal> {

    // Tall border: x 100..900, y 100..1300 (thickness 40)
    val border = listOf(
        Wall(Rect(100f, 100f, 900f, 140f)),    // top
        Wall(Rect(100f, 100f, 140f, 1300f)),   // left
        Wall(Rect(100f, 1260f, 900f, 1300f)),  // bottom
        Wall(Rect(860f, 100f, 900f, 1300f)),   // right
    )

    return when (levelId.coerceIn(1, 5)) {

        // ===============================================================
        // LEVEL 1
        // ===============================================================
        1 -> {
            val walls = border + listOf(
                // A horizontal divider with a wide gap on the right
                Wall(Rect(140f, 420f, 700f, 460f)),
                // A small pillar to teach control
                Wall(Rect(420f, 720f, 470f, 860f)),
            )

            val traps = listOf(
                Trap(Rect(620f, 560f, 680f, 620f)) // avoidable
            )

            val goal = Goal(Rect(760f, 1160f, 840f, 1240f))
            Triple(walls, traps, goal)
        }

        // ===============================================================
        // LEVEL 2
        // ===============================================================
        2 -> {
            val walls = border + listOf(
                // Gate 1 (opening on RIGHT)
                Wall(Rect(140f, 320f, 700f, 360f)),

                // Gate 2 (opening on LEFT)
                Wall(Rect(300f, 520f, 860f, 560f)),

                // Gate 3 (opening on RIGHT)
                Wall(Rect(140f, 760f, 700f, 800f)),

                // Gate 4 (opening on LEFT) near bottom
                Wall(Rect(300f, 980f, 860f, 1020f)),
            )

            val traps = listOf(
                // Near openings but not inside
                Trap(Rect(740f, 380f, 800f, 440f)),
                Trap(Rect(200f, 600f, 260f, 660f)),
                Trap(Rect(740f, 820f, 800f, 880f)),
            )

            val goal = Goal(Rect(760f, 1160f, 840f, 1240f))
            Triple(walls, traps, goal)
        }

        // ===============================================================
        // LEVEL 3
        // ===============================================================
        3 -> {

            val walls = border + listOf(
                // -------------------------
                // Room separators (horizontal)

                Wall(Rect(140f, 420f, 720f, 460f)),


                Wall(Rect(280f, 780f, 860f, 820f)),

                // -------------------------
                // Vertical divider in the middle
                Wall(Rect(520f, 460f, 560f, 600f)),
                Wall(Rect(520f, 680f, 560f, 780f)),

                // -------------------------
                // Bottom-right goal pocket walls (NOT sealed)
                // Make a pocket where the only entrance is from the LEFT.
                // Pocket top wall (leave a clear entrance from left side)
                Wall(Rect(620f, 1040f, 860f, 1080f)),

                // Pocket left wall (creates the pocket shape)
                //Wall(Rect(620f, 1080f, 660f, 1260f)),

                // A short bottom inner wall to stop straight drop into goal (but still passable)
                Wall(Rect(420f, 1120f, 620f, 1160f)),

                // -------------------------
                // Small blockers (make it maze-like but fair)
                // Top room small post (forces slight steering, not blocking)
                Wall(Rect(420f, 240f, 460f, 340f)),

                // Mid room small post near the right side
                Wall(Rect(760f, 540f, 800f, 660f)),

                // Lower room small post near left
                Wall(Rect(240f, 880f, 280f, 1000f))
            )

            val traps = listOf(
                // Traps placed in "optional space", not on the required doorway line.
                Trap(Rect(200f, 520f, 260f, 580f)),   // mid-left (avoidable)
                Trap(Rect(700f, 620f, 760f, 680f)),   // mid-right (avoidable)
                Trap(Rect(360f, 940f, 420f, 1000f))   // lower-middle (avoidable)
            )

            // Goal placed inside the pocket (reachable from left entrance)
            val goal = Goal(Rect(740f, 1160f, 840f, 1240f))

            Triple(walls, traps, goal)
        }

        // ===============================================================
        // LEVEL 4
        // ===============================================================
        4 -> {
            // Goal inside the blocked top-right quadrant
            val goal = Goal(Rect(785f, 175f, 845f, 235f))

            val walls = border + listOf(
                // Top lane blockers (stop "one tilt right" from spawn)
                Wall(Rect(140f, 220f, 520f, 260f)),   // long bar
                Wall(Rect(600f, 220f, 760f, 260f)),   // leaves a gap between 520..600

                // Mid horizontal separator (forces a drop to lower half)
                Wall(Rect(260f, 420f, 800f, 460f)),   // long bar

                // Left vertical post (prevents free sliding down the left edge)
                Wall(Rect(150f, 260f, 200f, 420f)),

                // Center post (creates decision + prevents straight route)
                Wall(Rect(520f, 460f, 560f, 700f)),

                // Lower mid bar (forces you to go around to the RIGHT side)
                Wall(Rect(140f, 700f, 400f, 740f)),

                Wall(Rect(500f, 700f, 720f, 740f)),

                // Right-side “channel” wall (creates a corridor up towards the quadrant entrance)
                Wall(Rect(720f, 460f, 760f, 1100f)),

                // A bar that blocks you from going into the right channel too early
                Wall(Rect(560f, 540f, 720f, 580f)),

                // Bottom-left bar to stop easy bottom sweep
                Wall(Rect(140f, 1000f, 520f, 1040f)),

                // A post near bottom to force a turn into the right channel
                Wall(Rect(520f, 900f, 560f, 1000f)),

                // =========================================================
                // BLOCK TOP-RIGHT QUADRANT (SEALED AREA)
                // Quadrant region: x 660..900, y 100..420
                // Only entrance: small hole at bottom-right of quadrant.
                // =========================================================

                // Left wall of quadrant
                Wall(Rect(660f, 100f, 700f, 420f)),

                // Top wall of quadrant (cap)
                Wall(Rect(660f, 100f, 900f, 140f)),

                // Bottom wall of quadrant WITH A HOLE ON THE RIGHT
                // hole is from x=820..860 (40px). Adjust if your ball radius is bigger.
                Wall(Rect(660f, 380f, 820f, 420f)),

                // NOTE: right wall is already the border (x=860..900)

                // =========================================================
                // GOAL POCKET INSIDE QUADRANT (OPEN ON LEFT)
                // This prevents the goal being "boxed up".
                // =========================================================

                // Pocket top
                Wall(Rect(740f, 140f, 900f, 170f)),
                // Pocket right
                Wall(Rect(850f, 170f, 900f, 260f)),
                // Pocket bottom
                Wall(Rect(740f, 260f, 800f, 290f))
                // LEFT side open so ball can enter pocket from inside the quadrant
            )

            val traps = listOf(
                // traps placed where you tend to overshoot turns (but not blocking the only path)
                Trap(Rect(460f, 300f, 520f, 360f)),   // near top gap
                Trap(Rect(300f, 560f, 360f, 620f)),   // near mid
                Trap(Rect(620f, 760f, 680f, 820f)),   // near right-channel entry
                Trap(Rect(760f, 900f, 820f, 960f))    // inside right channel, punish rushing
            )

            Triple(walls, traps, goal)
        }
        // ===============================================================
        // LEVEL 5 
        // ===============================================================
        else -> {
            val walls = border + listOf(
                // Build a corridor using “blocking slabs”.
                // Intended route is a long vertical run with alternating side shifts.

                // Blocker A: forces you to go RIGHT early
                Wall(Rect(140f, 260f, 620f, 300f)),

                // Blocker B: forces you to go LEFT
                Wall(Rect(380f, 420f, 860f, 460f)),

                // Blocker C: forces you to go RIGHT
                Wall(Rect(140f, 580f, 620f, 620f)),

                // Blocker D: forces you to go LEFT
                Wall(Rect(380f, 740f, 860f, 780f)),

                // Blocker E: forces you to go RIGHT near end
                Wall(Rect(140f, 900f, 620f, 940f)),

                // Blocker F: forces you to go LEFT one last time
                Wall(Rect(380f, 1060f, 860f, 1100f)),

                // Tightening pillars (still passable)
                Wall(Rect(620f, 300f, 660f, 380f)),
                Wall(Rect(340f, 460f, 380f, 540f)),
                Wall(Rect(620f, 620f, 660f, 700f)),
                Wall(Rect(340f, 780f, 380f, 860f)),
            )

            val traps = listOf(
                // Traps punish overshooting turns, not blocking door gaps
                Trap(Rect(720f, 320f, 780f, 380f)),
                Trap(Rect(200f, 500f, 260f, 560f)),
                Trap(Rect(720f, 680f, 780f, 740f)),
                Trap(Rect(200f, 860f, 260f, 920f)),
                Trap(Rect(720f, 1120f, 780f, 1180f)),
            )

            // Goal near bottom-right, reachable from the last right shift
            val goal = Goal(Rect(760f, 1160f, 840f, 1240f))
            Triple(walls, traps, goal)
        }
    }
}

// --- NEW: bounds-based centering (centers the actual maze, not the 0..1000 board) ---
private fun levelBounds(walls: List<Wall>, traps: List<Trap>, goal: Goal, ball: Ball): Rect {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY

    fun include(r: Rect) {
        left = min(left, r.left)
        top = min(top, r.top)
        right = max(right, r.right)
        bottom = max(bottom, r.bottom)
    }

    for (w in walls) include(w.rect)
    for (t in traps) include(t.rect)
    include(goal.rect)

    // include ball spawn so it doesn't get clipped
    include(
        Rect(
            ball.pos.x - ball.radius,
            ball.pos.y - ball.radius,
            ball.pos.x + ball.radius,
            ball.pos.y + ball.radius
        )
    )

    // padding around the maze content
    val pad = 40f
    return Rect(left - pad, top - pad, right + pad, bottom + pad)
}

@Composable
private fun EndOverlay(
    isWin: Boolean,
    timeSec: Float,
    onRestart: () -> Unit,
    onLevelSelect: () -> Unit,
    onMainMenu: () -> Unit,
    onSettings: () -> Unit
) {
    val title = if (isWin) "You Win!" else "You Lost!"
    val subtitle = if (isWin) "Time: ${"%.2f".format(timeSec)}s" else "Hit trap!"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(16.dp))

                val btnShape = RoundedCornerShape(28.dp)
                val btnHeight = 48.dp
                val stroke = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

                Button(
                    onClick = onRestart,
                    shape = btnShape,
                    modifier = Modifier.fillMaxWidth().height(btnHeight)
                ) { Text("Restart") }
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onLevelSelect,
                    shape = btnShape,
                    border = stroke,
                    modifier = Modifier.fillMaxWidth().height(btnHeight)
                ) { Text("Level Select") }
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onMainMenu,
                    shape = btnShape,
                    border = stroke,
                    modifier = Modifier.fillMaxWidth().height(btnHeight)
                ) { Text("Main Menu") }
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSettings,
                    shape = btnShape,
                    border = stroke,
                    modifier = Modifier.fillMaxWidth().height(btnHeight)
                ) { Text("Settings") }
            }
        }
    }
}

private fun makeInitialState(
    levelId: Int,
    walls: List<Wall>,
    traps: List<Trap>,
    goal: Goal
): GameState {
    return GameState(
        levelId = levelId,
        ball = Ball(
            pos = Offset(200f, 200f),
            vel = Offset.Zero,
            radius = (22f - (levelId - 1).coerceIn(0, 4) * 1.5f)
        ),
        walls = walls,
        traps = traps,
        goal = goal,
        elapsedSec = 0f,
        finished = false,
        failed = false,
        tiltAccel = Offset.Zero
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    levelId: Int,
    onExit: () -> Unit,
    onGoLevelSelect: () -> Unit,
    onGoMainMenu: () -> Unit,
    onGoSettings: () -> Unit,
    vm: GameViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Physics still uses this as your "world bounds"
    val DESIGN_W = 1000f
    val DESIGN_H = 1400f

    var screenSize by remember { mutableStateOf(Size.Zero) }

    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    var sensorAvailable by remember { mutableStateOf(true) }

    val (lvlWalls, lvlTraps, lvlGoal) = remember(levelId) { buildLevel(levelId) }

    var state by remember(levelId) {
        mutableStateOf(makeInitialState(levelId, lvlWalls, lvlTraps, lvlGoal))
    }

    val restartLevel = {
        state = makeInitialState(levelId, lvlWalls, lvlTraps, lvlGoal)
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            sensorAvailable = false
            return@DisposableEffect onDispose { }
        }

        sensorAvailable = true

        var filteredX = 0f
        var filteredY = 0f
        val alpha = 0.1f
        val maxTilt = 10f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val rawX = it.values[0]
                    val rawY = it.values[1]

                    filteredX = alpha * rawX + (1f - alpha) * filteredX
                    filteredY = alpha * rawY + (1f - alpha) * filteredY

                    tiltX = (filteredX / maxTilt).coerceIn(-1f, 1f)
                    tiltY = (filteredY / maxTilt).coerceIn(-1f, 1f)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    LaunchedEffect(levelId) {
        var last = System.nanoTime()
        while (isActive) {
            val now = System.nanoTime()
            val dt = (now - last) / 1_000_000_000f
            last = now

            val levelFactor = (levelId - 1).coerceIn(0, 4)
            val accelFactor = 900f + levelFactor * 140f

            val newState = state.copy(
                tiltAccel = Offset(
                    x = -tiltX * accelFactor,
                    y = tiltY * accelFactor
                )
            )

            updatePhysics(
                newState,
                dt.coerceIn(0f, 0.033f),
                DESIGN_W,
                DESIGN_H
            )

            state = newState
            delay(16)
        }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) {
            val timeMs = (state.elapsedSec * 1000).toLong()
            vm.submitBestTime(levelId, timeMs)
        }
    }

    // --- Top UI height in PX (to avoid drawing under the app bar/status bar) ---
    val appBarHeightDp = 56.dp
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topUiDp = statusBarTopDp + appBarHeightDp
    val topUiPx = with(density) { topUiDp.toPx() }

    // --- NEW: center based on actual content bounds (maze may start at x=100, etc.) ---
    val contentRect = remember(levelId) {
        levelBounds(lvlWalls, lvlTraps, lvlGoal, state.ball)
    }

    val scale = remember(screenSize, topUiPx, contentRect) {
        if (screenSize.width <= 0f || screenSize.height <= 0f) 1f else {
            val availH = (screenSize.height - topUiPx).coerceAtLeast(1f)
            min(screenSize.width / contentRect.width, availH / contentRect.height)
        }
    }

    val offset = remember(screenSize, topUiPx, contentRect, scale) {
        if (screenSize.width <= 0f || screenSize.height <= 0f) Offset.Zero else {
            val availH = (screenSize.height - topUiPx).coerceAtLeast(1f)
            val drawW = contentRect.width * scale
            val drawH = contentRect.height * scale

            val ox = (screenSize.width - drawW) / 2f
            val oy = topUiPx + (availH - drawH) / 2f

            // shift by contentRect.left/top so maze is truly centered
            Offset(
                x = ox - contentRect.left * scale,
                y = oy - contentRect.top * scale
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    screenSize = Size(it.width.toFloat(), it.height.toFloat())
                }
        ) {
            withTransform({
                translate(left = offset.x, top = offset.y)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                for (w in state.walls) {
                    drawRect(
                        color = androidx.compose.ui.graphics.Color.DarkGray,
                        topLeft = w.rect.topLeft,
                        size = w.rect.size
                    )
                }

                for (t in state.traps) {
                    drawRect(
                        color = androidx.compose.ui.graphics.Color.Red,
                        topLeft = t.rect.topLeft,
                        size = t.rect.size
                    )
                }

                drawRect(
                    color = androidx.compose.ui.graphics.Color.Green,
                    topLeft = state.goal.rect.topLeft,
                    size = state.goal.rect.size
                )

                drawCircle(
                    color = androidx.compose.ui.graphics.Color.Cyan,
                    radius = state.ball.radius,
                    center = state.ball.pos,
                    style = Fill
                )
            }
        }

        TopAppBar(
            title = { Text("Level ${state.levelId}") },
            navigationIcon = {
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 84.dp)
                .padding(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Time: ${"%.2f".format(state.elapsedSec)}s",
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.finished) Text("Finished!", style = MaterialTheme.typography.titleMedium)
                else if (state.failed) Text("Hit trap!", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (state.finished || state.failed) {
            EndOverlay(
                isWin = state.finished,
                timeSec = state.elapsedSec,
                onRestart = restartLevel,
                onLevelSelect = onGoLevelSelect,
                onMainMenu = onGoMainMenu,
                onSettings = onGoSettings
            )
        }
    }
}