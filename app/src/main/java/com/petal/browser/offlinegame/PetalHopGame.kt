package com.petal.browser.offlinegame

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.motion.MotionGate
import kotlin.math.min
import kotlin.math.sin

/**
 * Observable wrapper around [PetalHopEngine]. The engine itself is plain Kotlin; this class mirrors
 * the few values the UI needs into Compose state and turns engine events into haptics.
 */
@Stable
class PetalHopState(private val context: Context) : PetalHopEngine.Events {

    private val appContext = context.applicationContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)

    val engine = PetalHopEngine(this).also {
        it.best = prefs.getInt(PetalOfflineGameOverlay.PREF_BEST, 0)
        it.reduceMotion = MotionGate.isReduceMotion(appContext)
    }

    var phase by mutableStateOf(PetalHopEngine.Phase.READY)
        private set
    var score by mutableIntStateOf(0)
        private set
    var best by mutableIntStateOf(engine.best)
        private set
    var newBest by mutableStateOf(false)
        private set

    /** Bumps whenever the score crosses another 100 so the UI can play a spring "pop". */
    var milestoneToken by mutableIntStateOf(0)
        private set

    fun advance(dtSeconds: Float) {
        engine.advance(dtSeconds)
        if (phase != engine.phase) phase = engine.phase
        if (score != engine.score) score = engine.score
    }

    fun restart() {
        engine.reset()
        newBest = false
        phase = engine.phase
        score = 0
    }

    private fun haptic(pattern: PetalHapticEngine.Pattern, intensity: Float, throttleMs: Long = 0L) {
        PetalHapticEngine.getInstance(appContext).playIfEnabled(appContext, pattern, intensity, throttleMs)
    }

    override fun onJump() = haptic(PetalHapticEngine.Pattern.TICK, 0.4f, 40L)

    override fun onLand() = Unit

    override fun onMilestone(score: Int) {
        milestoneToken++
        haptic(PetalHapticEngine.Pattern.DOUBLE_TICK, 0.75f)
    }

    override fun onGameOver(newBest: Boolean) {
        this.newBest = newBest
        if (newBest) {
            best = engine.best
            prefs.edit().putInt(PetalOfflineGameOverlay.PREF_BEST, engine.best).apply()
        }
        PetalHapticEngine.getInstance(appContext).playHeavyClick(appContext)
    }
}

@Composable
fun rememberPetalHopState(): PetalHopState {
    val context = LocalContext.current
    return remember(context) { PetalHopState(context) }
}

private class HopColors(cs: ColorScheme) {
    val player = cs.primary.toArgb()
    val face = cs.onPrimary.toArgb()
    val ground = cs.surfaceContainerHigh.toArgb()
    val groundDetail = cs.outlineVariant.toArgb()
    val farCloud = ColorUtils.setAlphaComponent(cs.surfaceContainerHighest.toArgb(), 140)
    val midCloud = ColorUtils.setAlphaComponent(cs.secondaryContainer.toArgb(), 170)
    val cloudObstacle = cs.secondary.toArgb()
    val thorn = cs.tertiary.toArgb()
    val bee = cs.tertiaryContainer.toArgb()
    val beeDetail = cs.onTertiaryContainer.toArgb()
    val burst = cs.error.toArgb()
    val shadow = ColorUtils.setAlphaComponent(cs.onSurface.toArgb(), 40)
}

private class HopShapes {
    val playerMorph = Morph(MaterialShapes.Flower.normalized(), MaterialShapes.Cookie9Sided.normalized())
    val deathMorph = Morph(MaterialShapes.Cookie9Sided.normalized(), MaterialShapes.SoftBurst.normalized())
    val playerPath = Path()
    val deathPath = Path()
    val cloud = Path().also { MaterialShapes.Puffy.normalized().toPath(it) }
    val thorn = Path().also { MaterialShapes.Burst.normalized().toPath(it) }
    val bee = Path().also { MaterialShapes.Clover4Leaf.normalized().toPath(it) }
}

/**
 * The game surface: one Canvas, one frame loop, one gesture detector. [interactive] false keeps the
 * idle scene animating but ignores taps (used for the "Play while you wait" state).
 */
@Composable
fun PetalHopGame(
    state: PetalHopState,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hopColors = remember(colors) { HopColors(colors) }
    val shapes = remember { HopShapes() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val rect = remember { RectF() }
    var frame by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val description = stringResource(R.string.offline_game_cd_hop)
    val hopLabel = stringResource(R.string.offline_game_hop_action)

    // One loop, paused automatically whenever the host is not at least STARTED.
    LaunchedEffect(state, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (last != 0L) state.advance((now - last) / 1_000_000_000f)
                    last = now
                    frame = now
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = description
                onClick(label = hopLabel) {
                    if (interactive) {
                        state.engine.pressJump()
                        state.engine.releaseJump()
                    }
                    true
                }
            }
            .pointerInput(state, interactive) {
                detectTapGestures(
                    onPress = {
                        if (interactive && state.engine.phase != PetalHopEngine.Phase.OVER) {
                            state.engine.pressJump()
                            tryAwaitRelease()
                            state.engine.releaseJump()
                        }
                    }
                )
            }
    ) {
        // Reading `frame` here (draw phase) invalidates only the draw, never recomposition.
        if (frame < 0L) return@Canvas
        val scale = size.height / PetalHopEngine.WORLD_H
        state.engine.worldW = size.width / scale
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.scale(scale, scale)
            renderHop(nc, state.engine, hopColors, shapes, paint, rect)
            nc.restore()
        }
    }
}

private fun wrap(v: Float, period: Float): Float = ((v % period) + period) % period

private fun renderHop(
    c: android.graphics.Canvas,
    e: PetalHopEngine,
    col: HopColors,
    sh: HopShapes,
    p: Paint,
    r: RectF
) {
    val w = e.worldW
    val h = PetalHopEngine.WORLD_H
    val gy = PetalHopEngine.GROUND_Y
    p.style = Paint.Style.FILL

    // Far pill clouds (slowest parallax layer).
    p.color = col.farCloud
    val farPeriod = w + 180f
    val farScroll = if (e.reduceMotion) 0f else e.scrollFar
    for (k in 0 until 3) {
        val x = wrap(k * (farPeriod / 3f) - farScroll, farPeriod) - 100f
        val y = 22f + (k * 23f) % 46f
        val cw = 80f + k * 18f
        r.set(x, y, x + cw, y + 20f)
        c.drawRoundRect(r, 10f, 10f, p)
    }

    // Mid puffy clouds.
    p.color = col.midCloud
    val midPeriod = w + 160f
    val midScroll = if (e.reduceMotion) 0f else e.scrollMid
    for (k in 0 until 2) {
        val x = wrap(k * (midPeriod / 2f) + 40f - midScroll, midPeriod) - 80f
        val y = 46f + k * 30f
        c.save()
        c.translate(x, y)
        c.scale(64f, 38f)
        c.drawPath(sh.cloud, p)
        c.restore()
    }

    // Ground slab with moving pebbles.
    p.color = col.ground
    r.set(-6f, gy, w + 6f, h + 14f)
    c.drawRoundRect(r, 16f, 16f, p)
    p.color = col.groundDetail
    val pebblePeriod = w + 60f
    for (k in 0 until 9) {
        val x = wrap(k * 53f - e.scrollGround, pebblePeriod) - 20f
        val y = gy + 12f + (k % 3) * 9f
        r.set(x, y, x + 12f + (k % 2) * 8f, y + 4f)
        c.drawRoundRect(r, 2f, 2f, p)
    }

    // Obstacles.
    for (i in 0 until PetalHopEngine.MAX_OBSTACLES) {
        if (!e.obstacleActive[i]) continue
        val ox = e.obstacleX[i]
        val ow = e.obstacleW[i]
        val oh = e.obstacleH[i]
        val oy = e.obstacleY[i]
        c.save()
        when (e.obstacleType[i]) {
            PetalHopEngine.TYPE_CLOUD -> {
                p.color = col.cloudObstacle
                c.translate(ox - ow / 2f, oy - oh / 2f)
                c.scale(ow, oh)
                c.drawPath(sh.cloud, p)
            }
            PetalHopEngine.TYPE_THORN -> {
                p.color = col.thorn
                c.translate(ox, oy)
                c.rotate(e.time * 40f)
                c.scale(ow + 6f, oh + 2f)
                c.translate(-0.5f, -0.5f)
                c.drawPath(sh.thorn, p)
            }
            else -> {
                // Bee: clover body whose wings "flap" via a quick vertical scale pulse.
                p.color = col.bee
                val flap = 1f + 0.16f * sin(e.time * 40f)
                c.translate(ox, oy + sin(e.time * 6f) * 2f)
                c.scale(ow, oh * flap)
                c.translate(-0.5f, -0.5f)
                c.drawPath(sh.bee, p)
                c.restore()
                c.save()
                p.color = col.beeDetail
                c.drawCircle(ox - 4f, oy - 1f, 1.8f, p)
                c.drawCircle(ox + 4f, oy - 1f, 1.8f, p)
            }
        }
        c.restore()
    }

    // Player.
    val bob = if (e.phase == PetalHopEngine.Phase.READY) sin(e.time * 3f) * 2f else 0f
    val py = e.playerY + bob
    if (e.phase != PetalHopEngine.Phase.OVER) {
        val lift = ((gy - PetalHopEngine.PLAYER_R) - e.playerY).coerceAtLeast(0f) / 90f
        p.color = col.shadow
        val sr = PetalHopEngine.PLAYER_R * (1f - 0.45f * min(1f, lift))
        r.set(PetalHopEngine.PLAYER_X - sr, gy - 1f, PetalHopEngine.PLAYER_X + sr, gy + 5f)
        c.drawOval(r, p)

        sh.playerPath.rewind()
        sh.playerMorph.toPath(e.airMorph.coerceIn(0f, 1f), sh.playerPath)
        val sx = 1f - e.squash * 0.7f
        val sy = 1f + e.squash
        val size = PetalHopEngine.PLAYER_R * 2.25f

        p.color = col.player
        c.save()
        c.translate(PetalHopEngine.PLAYER_X, py + PetalHopEngine.PLAYER_R)
        c.scale(sx, sy)
        c.translate(0f, -PetalHopEngine.PLAYER_R)
        c.rotate(Math.toDegrees(e.rotation.toDouble()).toFloat())
        c.scale(size, size)
        c.translate(-0.5f, -0.5f)
        c.drawPath(sh.playerPath, p)
        c.restore()

        // Face stays upright while the petal body rolls.
        c.save()
        c.translate(PetalHopEngine.PLAYER_X, py + PetalHopEngine.PLAYER_R)
        c.scale(sx, sy)
        c.translate(0f, -PetalHopEngine.PLAYER_R)
        p.color = col.face
        val blink = (e.time % 3.4f) < 0.12f
        val eyeH = if (blink) 0.8f else 2.6f
        r.set(-6.4f, -3f - eyeH / 2f, -2.4f, -3f + eyeH / 2f)
        c.drawOval(r, p)
        r.set(2.4f, -3f - eyeH / 2f, 6.4f, -3f + eyeH / 2f)
        c.drawOval(r, p)
        r.set(-2.4f, 2f, 2.4f, 4.6f)
        c.drawRoundRect(r, 2f, 2f, p)
        c.restore()
    } else {
        // Game over: the petal bursts (cookie -> soft burst morph) and fades.
        val t = e.overTime
        val progress = min(1f, t * 5f)
        sh.deathPath.rewind()
        sh.deathMorph.toPath(progress, sh.deathPath)
        val alpha = (1f - min(1f, t * 1.3f))
        p.color = ColorUtils.setAlphaComponent(col.burst, (alpha * 255f).toInt())
        val size = PetalHopEngine.PLAYER_R * 2.25f * (1f + min(t, 0.6f) * 1.2f)
        c.save()
        c.translate(PetalHopEngine.PLAYER_X, py)
        c.rotate(t * 160f)
        c.scale(size, size)
        c.translate(-0.5f, -0.5f)
        c.drawPath(sh.deathPath, p)
        c.restore()
    }
}
