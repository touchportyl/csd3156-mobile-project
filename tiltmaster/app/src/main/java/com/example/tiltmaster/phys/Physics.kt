package com.example.tiltmaster.phys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// --------------------------------------------------
// Data Models
// --------------------------------------------------

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
    var tiltAccel: Offset = Offset.Zero
)

// --------------------------------------------------
// Physics Helpers
// --------------------------------------------------

private fun clamp(v: Float, lo: Float, hi: Float) =
    max(lo, min(hi, v))

private fun circleIntersectsRect(center: Offset, radius: Float, r: Rect): Boolean {
    val closestX = clamp(center.x, r.left, r.right)
    val closestY = clamp(center.y, r.top, r.bottom)
    val dx = center.x - closestX
    val dy = center.y - closestY
    return dx * dx + dy * dy <= radius * radius
}

// --------------------------------------------------
// Physics Update
// --------------------------------------------------
fun updatePhysics(
    state: GameState,
    dt: Float,
    boundsWidth: Float,
    boundsHeight: Float
) {
    if (state.finished || state.failed) return

    state.elapsedSec += dt
    val accel = state.tiltAccel

    val levelFactor = (state.levelId - 1).coerceIn(0, 4)
    val damping = (0.985f - levelFactor * 0.002f).coerceIn(0.972f, 0.985f)
    val maxSpeed = 1100f + levelFactor * 120f

    state.ball.vel = Offset(
        state.ball.vel.x + accel.x * dt,
        state.ball.vel.y + accel.y * dt
    )

    val vx = clamp(state.ball.vel.x, -maxSpeed, maxSpeed)
    val vy = clamp(state.ball.vel.y, -maxSpeed, maxSpeed)
    state.ball.vel = Offset(vx, vy)

    var nextPos = state.ball.pos + state.ball.vel * dt
    val radius = state.ball.radius

    // ---------------- WALL COLLISION ----------------
    for (w in state.walls) {
        val closestX = clamp(nextPos.x, w.rect.left, w.rect.right)
        val closestY = clamp(nextPos.y, w.rect.top, w.rect.bottom)

        val dx = nextPos.x - closestX
        val dy = nextPos.y - closestY
        val distSq = dx * dx + dy * dy

        if (distSq < radius * radius) {
            val dist = sqrt(distSq)
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

    // ---------------- SCREEN BOUNDS ----------------
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

    if (nextPos.x - radius < 0f) resolveCollision(1f, 0f, radius - nextPos.x)
    if (nextPos.x + radius > boundsWidth)
        resolveCollision(-1f, 0f, nextPos.x + radius - boundsWidth)
    if (nextPos.y - radius < 0f) resolveCollision(0f, 1f, radius - nextPos.y)
    if (nextPos.y + radius > boundsHeight)
        resolveCollision(0f, -1f, nextPos.y + radius - boundsHeight)

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
            val dist = sqrt(distSq)
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

    // ---------------- TRAPS ----------------
    for (t in state.traps) {
        if (circleIntersectsRect(state.ball.pos, radius, t.rect)) {
            state.failed = true
            return
        }
    }

    // ---------------- GOAL ----------------
    if (circleIntersectsRect(state.ball.pos, radius, state.goal.rect)) {
        state.finished = true
    }
}