package com.petal.browser.offlinegame

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Pure simulation for "Petal Hop", the offline runner shown when a page cannot load.
 *
 * Everything runs in a fixed logical world that is [WORLD_H] units tall; the renderer scales the
 * world to the available pixels, so gameplay is identical on phones, tablets and landscape. The
 * class never allocates while stepping (fixed pools, primitive arrays) so the draw loop stays
 * jank-free on low-end devices.
 */
class PetalHopEngine(private val events: Events) {

    enum class Phase { READY, RUNNING, OVER }

    interface Events {
        fun onJump()
        fun onLand()
        fun onMilestone(score: Int)
        fun onGameOver(newBest: Boolean)
    }

    companion object {
        const val WORLD_H = 200f
        const val GROUND_Y = 164f
        const val PLAYER_X = 54f
        const val PLAYER_R = 15f

        const val TYPE_CLOUD = 0
        const val TYPE_THORN = 1
        const val TYPE_BEE = 2

        const val MAX_OBSTACLES = 4

        private const val STEP = 1f / 120f
        private const val MAX_STEPS_PER_FRAME = 4
        private const val GRAVITY = 2000f
        private const val CUT_GRAVITY_MULT = 2.4f
        private const val JUMP_V = 570f
        private const val JUMP_BUFFER_S = 0.12f
        private const val HIT_FORGIVENESS = 4f
        private const val BEE_CENTER_ABOVE_GROUND = 47f
    }

    // --- World / session -------------------------------------------------------------------
    var worldW = 400f
    var phase = Phase.READY
        private set
    var best = 0
    var reduceMotion = false

    var time = 0f
        private set
    var distance = 0f
        private set
    var speed = 0f
        private set
    var overTime = 0f
        private set
    val score: Int get() = (distance / 12f).toInt()

    // --- Player ----------------------------------------------------------------------------
    var playerY = GROUND_Y - PLAYER_R
        private set
    var playerOnGround = true
        private set
    /** Squash (<0) / stretch (>0) driven by a damped spring. */
    var squash = 0f
        private set
    /** 0 = petal (flower) shape, 1 = airborne (cookie) shape. */
    var airMorph = 0f
        private set
    var rotation = 0f
        private set

    private var vy = 0f
    private var squashV = 0f
    private var holding = false
    private var jumpBuffer = 0f
    private var lastMilestone = 0
    private var accumulator = 0f

    // --- Obstacles (fixed pool) --------------------------------------------------------------
    val obstacleActive = BooleanArray(MAX_OBSTACLES)
    val obstacleType = IntArray(MAX_OBSTACLES)
    val obstacleX = FloatArray(MAX_OBSTACLES)
    val obstacleW = FloatArray(MAX_OBSTACLES)
    val obstacleH = FloatArray(MAX_OBSTACLES)
    /** Center Y. */
    val obstacleY = FloatArray(MAX_OBSTACLES)

    private var sinceSpawn = 0f
    private var nextGap = 260f

    // --- Parallax scroll offsets (world units) -----------------------------------------------
    var scrollFar = 0f
        private set
    var scrollMid = 0f
        private set
    var scrollGround = 0f
        private set

    init {
        reset()
    }

    fun reset() {
        phase = Phase.READY
        time = 0f
        distance = 0f
        speed = 0f
        overTime = 0f
        playerY = GROUND_Y - PLAYER_R
        playerOnGround = true
        vy = 0f
        squash = 0f
        squashV = 0f
        airMorph = 0f
        rotation = 0f
        holding = false
        jumpBuffer = 0f
        lastMilestone = 0
        accumulator = 0f
        sinceSpawn = 0f
        nextGap = 260f
        for (i in 0 until MAX_OBSTACLES) obstacleActive[i] = false
    }

    // --- Input -------------------------------------------------------------------------------
    fun pressJump() {
        when (phase) {
            Phase.READY -> {
                phase = Phase.RUNNING
                speed = 235f
                holding = true
                startJump()
            }
            Phase.RUNNING -> {
                holding = true
                if (playerOnGround) startJump() else jumpBuffer = JUMP_BUFFER_S
            }
            Phase.OVER -> Unit
        }
    }

    fun releaseJump() {
        holding = false
    }

    private fun startJump() {
        vy = -JUMP_V
        playerOnGround = false
        jumpBuffer = 0f
        if (!reduceMotion) {
            squash = 0.34f
            squashV = 0f
        }
        events.onJump()
    }

    // --- Stepping ----------------------------------------------------------------------------
    /** Advance by [dt] seconds using a fixed internal timestep. */
    fun advance(dt: Float) {
        accumulator += min(dt, 0.05f)
        var steps = 0
        while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME) {
            step(STEP)
            accumulator -= STEP
            steps++
        }
        if (steps == MAX_STEPS_PER_FRAME) accumulator = 0f
    }

    private fun step(dt: Float) {
        time += dt
        when (phase) {
            Phase.READY -> {
                scrollFar += 6f * dt
                scrollMid += 12f * dt
                scrollGround += 30f * dt
                stepSquash(dt)
            }
            Phase.RUNNING -> stepRunning(dt)
            Phase.OVER -> {
                overTime += dt
                stepSquash(dt)
            }
        }
    }

    private fun stepRunning(dt: Float) {
        speed = min(235f + distance * 0.011f, 500f)
        distance += speed * dt

        // Vertical motion: releasing early cuts the jump short (variable height).
        val g = if (vy < 0f && !holding) GRAVITY * CUT_GRAVITY_MULT else GRAVITY
        vy += g * dt
        playerY += vy * dt
        val floor = GROUND_Y - PLAYER_R
        if (playerY >= floor) {
            playerY = floor
            vy = 0f
            if (!playerOnGround) {
                playerOnGround = true
                if (!reduceMotion) {
                    squash = -0.36f
                    squashV = 0f
                }
                events.onLand()
            }
        } else {
            playerOnGround = false
        }

        if (jumpBuffer > 0f) {
            jumpBuffer -= dt
            if (playerOnGround) startJump()
        }

        stepSquash(dt)
        val morphTarget = if (playerOnGround || reduceMotion) 0f else 1f
        airMorph += (morphTarget - airMorph) * min(1f, dt * 16f)
        rotation += speed * dt / PLAYER_R * 0.5f

        scrollFar += speed * dt * 0.08f
        scrollMid += speed * dt * 0.22f
        scrollGround += speed * dt

        // Obstacles.
        for (i in 0 until MAX_OBSTACLES) {
            if (!obstacleActive[i]) continue
            obstacleX[i] -= speed * dt
            if (obstacleX[i] < -60f) obstacleActive[i] = false
        }
        sinceSpawn += speed * dt
        if (sinceSpawn >= nextGap) spawn()

        // Collisions.
        if (hitsAnyObstacle()) {
            phase = Phase.OVER
            overTime = 0f
            val newBest = score > best
            if (newBest) best = score
            events.onGameOver(newBest)
            return
        }

        val milestone = score / 100
        if (milestone > lastMilestone) {
            lastMilestone = milestone
            events.onMilestone(milestone * 100)
        }
    }

    private fun stepSquash(dt: Float) {
        if (reduceMotion) {
            squash = 0f
            squashV = 0f
            return
        }
        // Critically-underdamped spring back to neutral: bouncy but settles fast.
        squashV += (-320f * squash - 17f * squashV) * dt
        squash += squashV * dt
    }

    private fun spawn() {
        var slot = -1
        for (i in 0 until MAX_OBSTACLES) {
            if (!obstacleActive[i]) {
                slot = i
                break
            }
        }
        if (slot < 0) return

        val roll = Random.nextFloat()
        // Bees only appear once the player has some rhythm going.
        val type = when {
            roll < 0.45f -> TYPE_CLOUD
            roll < 0.8f || score < 40 -> TYPE_THORN
            else -> TYPE_BEE
        }
        obstacleType[slot] = type
        obstacleActive[slot] = true
        obstacleX[slot] = worldW + 40f
        when (type) {
            TYPE_CLOUD -> {
                obstacleW[slot] = 38f
                obstacleH[slot] = 26f
                obstacleY[slot] = GROUND_Y - 13f
            }
            TYPE_THORN -> {
                obstacleW[slot] = 24f
                obstacleH[slot] = 32f
                obstacleY[slot] = GROUND_Y - 16f
            }
            else -> {
                obstacleW[slot] = 28f
                obstacleH[slot] = 22f
                obstacleY[slot] = GROUND_Y - BEE_CENTER_ABOVE_GROUND
            }
        }
        sinceSpawn = 0f
        nextGap = speed * 0.62f + 70f + Random.nextFloat() * 190f
    }

    private fun hitsAnyObstacle(): Boolean {
        val r = PLAYER_R - HIT_FORGIVENESS
        for (i in 0 until MAX_OBSTACLES) {
            if (!obstacleActive[i]) continue
            val halfW = obstacleW[i] / 2f - 3f
            val halfH = obstacleH[i] / 2f - 3f
            val cx = obstacleX[i]
            val cy = obstacleY[i]
            // Circle (player) vs axis-aligned rect (obstacle).
            val nearestX = max(cx - halfW, min(PLAYER_X, cx + halfW))
            val nearestY = max(cy - halfH, min(playerY, cy + halfH))
            val dx = PLAYER_X - nearestX
            val dy = playerY - nearestY
            if (dx * dx + dy * dy < r * r) return true
        }
        return false
    }
}
