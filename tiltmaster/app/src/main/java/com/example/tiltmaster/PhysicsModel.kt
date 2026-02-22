package com.example.tiltmaster

data class PhysicsState(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float
)

class TiltPhysicsEngine(
    private val accelScale: Float = 200f,
    private val damping: Float = 0.98f,
    private val maxSpeed: Float = 2000f
) {
    var state = PhysicsState(x = 100f, y = 100f, vx = 0f, vy = 0f)
        private set

    // Updated from sensor
    var tiltX: Float = 0f
    var tiltY: Float = 0f

    // Call every frame with dt (seconds)
    fun step(dt: Float) {
        // Map tilt to acceleration (tune signs as you like)
        val ax = -tiltX * accelScale
        val ay = tiltY * accelScale

        var vx = state.vx + ax * dt
        var vy = state.vy + ay * dt

        // Damping (friction)
        vx *= damping
        vy *= damping

        // Clamp speed
        val speedSq = vx*vx + vy*vy
        if (speedSq > maxSpeed * maxSpeed) {
            val scale = maxSpeed / kotlin.math.sqrt(speedSq)
            vx *= scale
            vy *= scale
        }

        var x = state.x + vx * dt
        var y = state.y + vy * dt

        // TODO: collision with maze walls and screen bounds

        state = state.copy(x = x, y = y, vx = vx, vy = vy)
    }
}
