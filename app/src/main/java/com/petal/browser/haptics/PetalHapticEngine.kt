package com.petal.browser.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Petal Haptic Engine
 * Adapted from Ever-Haptics for high-precision tactile feedback on Android.
 */
class PetalHapticEngine private constructor(context: Context) {

    enum class Pattern {
        CLICK,
        TICK,
        HEAVY_CLICK,
        DOUBLE_CLICK,
        SOFT_BUMP,
        DOUBLE_TICK
    }

    private val vibrator: Vibrator?
    val hasVibrator: Boolean
    val hasAmplitudeControl: Boolean
    private val touchAttrs: VibrationAttributes?

    private val effectCache: ConcurrentHashMap<Int, VibrationEffect> = ConcurrentHashMap()
    private val lastPlayMs: ConcurrentHashMap<Pattern, AtomicLong> = ConcurrentHashMap()

    init {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibrator = manager?.defaultVibrator ?: (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        hasVibrator = vibrator != null && vibrator.hasVibrator()
        hasAmplitudeControl = hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator!!.hasAmplitudeControl()

        touchAttrs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
        } else {
            null
        }

        for (p in Pattern.values()) {
            lastPlayMs[p] = AtomicLong(0)
        }
    }

    fun play(pattern: Pattern, intensity: Float = 1.0f) {
        if (!hasVibrator || vibrator == null) return
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        if (clampedIntensity < MIN_AUDIBLE_INTENSITY) return

        val minIntervalMs = getMinIntervalMs(pattern)
        val now = System.currentTimeMillis()
        val last = lastPlayMs[pattern]
        if (last != null) {
            val prev = last.get()
            if (now - prev < minIntervalMs) {
                return
            }
            last.set(now)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = getOrCreateEffect(pattern, clampedIntensity)
            if (effect != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && touchAttrs != null) {
                        vibrator.vibrate(effect, touchAttrs)
                    } else {
                        vibrator.vibrate(effect)
                    }
                    return
                } catch (ignored: Exception) {
                }
            }
        }

        // Fallback for pre-O or if effect failed
        playLegacyFallback(pattern, clampedIntensity)
    }

    private fun getMinIntervalMs(pattern: Pattern): Long {
        return when (pattern) {
            Pattern.CLICK -> 30L
            Pattern.TICK -> 20L
            Pattern.HEAVY_CLICK -> 80L
            Pattern.DOUBLE_CLICK -> 100L
            Pattern.SOFT_BUMP -> 40L
            Pattern.DOUBLE_TICK -> 60L
        }
    }

    private fun getOrCreateEffect(pattern: Pattern, intensity: Float): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val bucket = (intensity * (INTENSITY_BUCKETS - 1)).toInt().coerceIn(0, INTENSITY_BUCKETS - 1)
        val key = pattern.ordinal * INTENSITY_BUCKETS + bucket

        val cached = effectCache[key]
        if (cached != null) return cached

        val effect = buildEffect(pattern, (bucket + 1).toFloat() / INTENSITY_BUCKETS.toFloat())
        if (effect != null) {
            effectCache[key] = effect
        }
        return effect
    }

    private fun buildEffect(pattern: Pattern, intensity: Float): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        // Try predefined composition / primitives on Android 10+ (Q / R)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val predefined = getPredefinedEffect(pattern)
            if (predefined != null && intensity >= 0.7f) {
                return predefined
            }
        }

        val amp = if (hasAmplitudeControl) {
            (intensity * 255f).toInt().coerceIn(1, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }

        return when (pattern) {
            Pattern.CLICK -> VibrationEffect.createOneShot(12, amp)
            Pattern.TICK -> VibrationEffect.createOneShot(7, (amp * 0.6f).toInt().coerceAtLeast(1))
            Pattern.HEAVY_CLICK -> VibrationEffect.createOneShot(24, amp)
            Pattern.SOFT_BUMP -> VibrationEffect.createOneShot(18, (amp * 0.45f).toInt().coerceAtLeast(1))
            Pattern.DOUBLE_CLICK -> {
                val timings = longArrayOf(0, 10, 35, 12)
                val amps = intArrayOf(0, amp, 0, (amp * 0.85f).toInt().coerceAtLeast(1))
                if (hasAmplitudeControl) {
                    VibrationEffect.createWaveform(timings, amps, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
            }
            Pattern.DOUBLE_TICK -> {
                val timings = longArrayOf(0, 6, 25, 6)
                val amps = intArrayOf(0, (amp * 0.5f).toInt().coerceAtLeast(1), 0, (amp * 0.4f).toInt().coerceAtLeast(1))
                if (hasAmplitudeControl) {
                    VibrationEffect.createWaveform(timings, amps, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
            }
        }
    }

    private fun getPredefinedEffect(pattern: Pattern): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return when (pattern) {
                    Pattern.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    Pattern.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    Pattern.HEAVY_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    Pattern.DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    else -> null
                }
            } catch (ignored: Exception) {
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun playLegacyFallback(pattern: Pattern, intensity: Float) {
        if (vibrator == null) return
        try {
            when (pattern) {
                Pattern.CLICK -> vibrator.vibrate(10)
                Pattern.TICK -> vibrator.vibrate(5)
                Pattern.HEAVY_CLICK -> vibrator.vibrate(25)
                Pattern.SOFT_BUMP -> vibrator.vibrate(15)
                Pattern.DOUBLE_CLICK -> vibrator.vibrate(longArrayOf(0, 10, 30, 12), -1)
                Pattern.DOUBLE_TICK -> vibrator.vibrate(longArrayOf(0, 5, 20, 5), -1)
            }
        } catch (ignored: Exception) {
        }
    }

    companion object {
        private const val INTENSITY_BUCKETS = 16
        private const val MIN_AUDIBLE_INTENSITY = 0.02f
        private var sInstance: PetalHapticEngine? = null

        @Synchronized
        @JvmStatic
        fun getInstance(context: Context): PetalHapticEngine {
            if (sInstance == null) {
                sInstance = PetalHapticEngine(context)
            }
            return sInstance!!
        }
    }
}
