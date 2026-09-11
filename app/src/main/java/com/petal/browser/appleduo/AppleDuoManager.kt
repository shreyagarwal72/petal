/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Singleton manager coordinating the Apple Duo (BETA) fold animation effect.
 */

package com.petal.browser.appleduo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.preference.PreferenceManager
import com.petal.browser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader

object AppleDuoManager {

    private const val TAG = "AppleDuoManager"

    const val PREF_KEY_ENABLED = "sp_apple_duo_enabled"
    const val PREF_KEY_WEBSITES = "sp_apple_duo_websites"
    const val PREF_KEY_USE_SENSOR = "sp_apple_duo_use_sensor"
    const val PREF_KEY_MANUAL_TILT = "sp_apple_duo_manual_tilt"
    const val PREF_KEY_AUTO_RECENTER = "sp_apple_duo_auto_recenter"
    const val PREF_KEY_EYE_DISTANCE = "sp_apple_duo_eye_distance"
    const val PREF_KEY_BLUR_SPREAD = "sp_apple_duo_blur_spread"
    const val PREF_KEY_DARKENING = "sp_apple_duo_darkening"

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var motionModel: FoldMotionModel? = null
    private var shaderSource: String? = null

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _showInWebsites = MutableStateFlow(true)
    val showInWebsites: StateFlow<Boolean> = _showInWebsites.asStateFlow()

    private val _useSensor = MutableStateFlow(true)
    val useSensor: StateFlow<Boolean> = _useSensor.asStateFlow()

    private val _manualTilt = MutableStateFlow(0f)
    val manualTilt: StateFlow<Float> = _manualTilt.asStateFlow()

    private val _autoRecenter = MutableStateFlow(true)
    val autoRecenter: StateFlow<Boolean> = _autoRecenter.asStateFlow()

    private val _eyeDistance = MutableStateFlow(450f)
    val eyeDistance: StateFlow<Float> = _eyeDistance.asStateFlow()

    private val _blurSpread = MutableStateFlow(0.12f)
    val blurSpread: StateFlow<Float> = _blurSpread.asStateFlow()

    private val _darkening = MutableStateFlow(0.015f)
    val darkening: StateFlow<Float> = _darkening.asStateFlow()

    private val _currentTilt = MutableStateFlow(0f)
    val currentTilt: StateFlow<Float> = _currentTilt.asStateFlow()

    private val _currentHingeSide = MutableStateFlow(1f)
    val currentHingeSide: StateFlow<Float> = _currentHingeSide.asStateFlow()

    private val _hasSensor = MutableStateFlow(false)
    val hasSensor: StateFlow<Boolean> = _hasSensor.asStateFlow()

    private var currentActiveView: View? = null
    private var currentIsWebsite: Boolean = false
    private var initialized = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            PREF_KEY_ENABLED -> {
                val en = sp.getBoolean(PREF_KEY_ENABLED, false)
                _isEnabled.value = en
                if (en) {
                    if (_useSensor.value) motionModel?.start()
                } else {
                    motionModel?.stop()
                    clearShaderEffectFromActiveView()
                }
                applyShaderEffectToActiveView()
            }
            PREF_KEY_WEBSITES -> {
                _showInWebsites.value = sp.getBoolean(PREF_KEY_WEBSITES, true)
                applyShaderEffectToActiveView()
            }
            PREF_KEY_USE_SENSOR -> {
                val use = sp.getBoolean(PREF_KEY_USE_SENSOR, true)
                _useSensor.value = use
                if (use && _isEnabled.value) {
                    motionModel?.start()
                } else {
                    motionModel?.stop()
                    updateManualTilt(_manualTilt.value)
                }
            }
            PREF_KEY_MANUAL_TILT -> {
                val mt = sp.getFloat(PREF_KEY_MANUAL_TILT, 0f)
                _manualTilt.value = mt
                if (!_useSensor.value) {
                    updateManualTilt(mt)
                }
            }
            PREF_KEY_AUTO_RECENTER -> {
                val ar = sp.getBoolean(PREF_KEY_AUTO_RECENTER, true)
                _autoRecenter.value = ar
                motionModel?.setAutoRecenterEnabled(ar)
            }
            PREF_KEY_EYE_DISTANCE -> {
                _eyeDistance.value = sp.getFloat(PREF_KEY_EYE_DISTANCE, 450f)
                applyShaderEffectToActiveView()
            }
            PREF_KEY_BLUR_SPREAD -> {
                _blurSpread.value = sp.getFloat(PREF_KEY_BLUR_SPREAD, 0.12f)
                applyShaderEffectToActiveView()
            }
            PREF_KEY_DARKENING -> {
                _darkening.value = sp.getFloat(PREF_KEY_DARKENING, 0.015f)
                applyShaderEffectToActiveView()
            }
        }
    }

    fun init(application: Application) {
        if (initialized) return
        initialized = true

        val prefs = PreferenceManager.getDefaultSharedPreferences(application)
        _isEnabled.value = prefs.getBoolean(PREF_KEY_ENABLED, false)
        _showInWebsites.value = prefs.getBoolean(PREF_KEY_WEBSITES, true)
        _useSensor.value = prefs.getBoolean(PREF_KEY_USE_SENSOR, true)
        _manualTilt.value = prefs.getFloat(PREF_KEY_MANUAL_TILT, 0f)
        _autoRecenter.value = prefs.getBoolean(PREF_KEY_AUTO_RECENTER, true)
        _eyeDistance.value = prefs.getFloat(PREF_KEY_EYE_DISTANCE, 450f)
        _blurSpread.value = prefs.getFloat(PREF_KEY_BLUR_SPREAD, 0.12f)
        _darkening.value = prefs.getFloat(PREF_KEY_DARKENING, 0.015f)

        motionModel = FoldMotionModel(application)
        motionModel?.setAutoRecenterEnabled(_autoRecenter.value)
        _hasSensor.value = motionModel?.hasSensor?.value ?: false

        loadShaderSource(application)

        // Observe sensor values
        scope.launch {
            motionModel?.tiltDegrees?.collect { tilt ->
                if (_isEnabled.value && _useSensor.value && _hasSensor.value) {
                    _currentTilt.value = tilt
                    _currentHingeSide.value = if (tilt >= 0f) 1f else -1f
                    applyShaderEffectToActiveView()
                }
            }
        }

        scope.launch {
            motionModel?.hasSensor?.collect { available ->
                _hasSensor.value = available
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        if (_isEnabled.value && _useSensor.value) {
            motionModel?.start()
        }

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (_isEnabled.value && _useSensor.value) {
                    motionModel?.start()
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (_isEnabled.value) {
                    motionModel?.stop()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun loadShaderSource(context: Context) {
        try {
            context.resources.openRawResource(R.raw.duo_fold).use { stream ->
                shaderSource = stream.bufferedReader().use(BufferedReader::readText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load duo_fold.agsl", e)
        }
    }

    fun getShaderSource(context: Context): String {
        if (shaderSource == null) {
            loadShaderSource(context)
        }
        return shaderSource ?: ""
    }

    fun recalibrate() {
        motionModel?.recalibrate()
        if (!_useSensor.value) {
            _manualTilt.value = 0f
            updateManualTilt(0f)
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (enabled) {
            if (_useSensor.value) motionModel?.start()
        } else {
            motionModel?.stop()
            clearShaderEffectFromActiveView()
        }
        applyShaderEffectToActiveView()
    }

    fun setShowInWebsites(show: Boolean) {
        _showInWebsites.value = show
        applyShaderEffectToActiveView()
    }

    fun setUseSensor(use: Boolean) {
        _useSensor.value = use
        if (use && _isEnabled.value) {
            motionModel?.start()
        } else {
            motionModel?.stop()
            updateManualTilt(_manualTilt.value)
        }
    }

    fun setAutoRecenter(auto: Boolean) {
        _autoRecenter.value = auto
        motionModel?.setAutoRecenterEnabled(auto)
    }

    fun setEyeDistance(distance: Float) {
        _eyeDistance.value = distance
        applyShaderEffectToActiveView()
    }

    fun setBlurSpread(spread: Float) {
        _blurSpread.value = spread
        applyShaderEffectToActiveView()
    }

    fun setDarkening(darkening: Float) {
        _darkening.value = darkening
        applyShaderEffectToActiveView()
    }

    fun setManualTilt(degrees: Float) {
        _manualTilt.value = degrees
        motionModel?.setManualTilt(degrees)
        if (!_useSensor.value || !_hasSensor.value) {
            updateManualTilt(degrees)
        }
    }

    private fun updateManualTilt(degrees: Float) {
        val clamped = degrees.coerceIn(-FoldMotionModel.MAX_TILT, FoldMotionModel.MAX_TILT)
        _currentTilt.value = clamped
        _currentHingeSide.value = if (clamped >= 0f) 1f else -1f
        applyShaderEffectToActiveView()
    }

    /**
     * Attaches the active rendering target view (e.g. Activity root layout or container).
     * @param targetView View to apply RenderEffect to
     * @param isWebsite Whether the current content is an external website
     */
    fun attachTargetView(targetView: View?, isWebsite: Boolean) {
        if (currentActiveView !== targetView) {
            clearShaderEffect(currentActiveView)
            currentActiveView = targetView
        }
        currentIsWebsite = isWebsite
        applyShaderEffectToActiveView()
    }

    fun onContentSwitched(isWebsite: Boolean) {
        currentIsWebsite = isWebsite
        applyShaderEffectToActiveView()
    }

    fun applyShaderEffectToActiveView() {
        val view = currentActiveView ?: return
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            view.post { applyShaderEffectToActiveView() }
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            clearShaderEffect(view)
            return
        }

        if (!_isEnabled.value) {
            clearShaderEffect(view)
            return
        }

        // If viewing a website and "show in websites" is turned OFF, remove effect
        if (currentIsWebsite && !_showInWebsites.value) {
            clearShaderEffect(view)
            return
        }

        val tilt = _currentTilt.value
        if (Math.abs(tilt) < 1e-4f) {
            clearShaderEffect(view)
            return
        }

        val src = shaderSource ?: return
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        if (width <= 1f || height <= 1f) {
            return
        }

        try {
            val dm: DisplayMetrics = view.resources.displayMetrics
            val xdpi = dm.xdpi
            val pxPerMm = if (xdpi.isFinite() && xdpi > 0f) xdpi / 25.4f else 6f

            val shader = RuntimeShader(src).apply {
                setFloatUniform("resolution", width, height)
                setFloatUniform("tiltDegrees", tilt)
                setFloatUniform("eyeDistancePx", _eyeDistance.value * pxPerMm)
                setFloatUniform("hingeSide", _currentHingeSide.value)
                setFloatUniform("blurSpread", _blurSpread.value)
                setFloatUniform("darkening", _darkening.value * 6f / pxPerMm)
            }

            val renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
            view.setRenderEffect(renderEffect)
            view.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying RenderEffect to view", e)
        }
    }

    private fun clearShaderEffectFromActiveView() {
        clearShaderEffect(currentActiveView)
    }

    private fun clearShaderEffect(view: View?) {
        if (view == null) return
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            view.post { clearShaderEffect(view) }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.setRenderEffect(null)
                view.invalidate()
            } catch (ignored: Exception) {}
        }
    }
}
