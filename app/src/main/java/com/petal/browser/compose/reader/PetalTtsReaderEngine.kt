package com.petal.browser.compose.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalContainmentShapes
import java.util.Locale

/**
 * PetalTtsReaderEngine
 * ─────────────────────────────────────────────────────────────────────────
 * Native Android TextToSpeech speech synthesizer controller for Reader Mode.
 * Splits article text into sentences, manages speech playback, speed rates,
 * and reports the currently spoken sentence index for live highlighting.
 */
class PetalTtsReaderEngine(
    context: Context,
    private val onSentenceChanged: (Int) -> Unit = {},
    private val onPlaybackStateChanged: (Boolean) -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    var isInitialized by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var currentSentenceIndex by mutableIntStateOf(0)
        private set
    var speechRate by mutableFloatStateOf(1.0f)
        private set

    private var sentences: List<String> = emptyList()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isInitialized = true
                setupProgressListener()
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                onPlaybackStateChanged(true)
                utteranceId?.toIntOrNull()?.let { idx ->
                    currentSentenceIndex = idx
                    onSentenceChanged(idx)
                }
            }

            override fun onDone(utteranceId: String?) {
                val nextIdx = (utteranceId?.toIntOrNull() ?: 0) + 1
                if (nextIdx < sentences.size) {
                    speakSentence(nextIdx)
                } else {
                    isSpeaking = false
                    onPlaybackStateChanged(false)
                }
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
                onPlaybackStateChanged(false)
            }
        })
    }

    fun loadText(fullText: String) {
        stop()
        sentences = fullText.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        currentSentenceIndex = 0
    }

    fun play() {
        if (!isInitialized || sentences.isEmpty()) return
        speakSentence(currentSentenceIndex)
    }

    fun pause() {
        tts?.stop()
        isSpeaking = false
        onPlaybackStateChanged(false)
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        currentSentenceIndex = 0
        onPlaybackStateChanged(false)
    }

    fun skipNext() {
        if (currentSentenceIndex + 1 < sentences.size) {
            speakSentence(currentSentenceIndex + 1)
        }
    }

    fun skipPrevious() {
        if (currentSentenceIndex > 0) {
            speakSentence(currentSentenceIndex - 1)
        } else {
            speakSentence(0)
        }
    }

    fun setRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.5f)
        tts?.setSpeechRate(speechRate)
    }

    private fun speakSentence(index: Int) {
        if (index !in sentences.indices) return
        currentSentenceIndex = index
        tts?.setSpeechRate(speechRate)
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, index.toString())
        }
        tts?.speak(sentences[index], TextToSpeech.QUEUE_FLUSH, params, index.toString())
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

/**
 * PetalFloatingTtsPlayerBar
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive floating playback capsule bar anchored at bottom
 * of Reader Mode with Play/Pause, Skip, Previous, and Speed controls.
 */
@Composable
fun PetalFloatingTtsPlayerBar(
    engine: PetalTtsReaderEngine,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = PetalContainmentShapes.Hero,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Speed indicator button
            Box {
                FilledTonalButton(
                    onClick = { showSpeedMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "${engine.speechRate}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    speedOptions.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x", fontWeight = if (engine.speechRate == speed) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                engine.setRate(speed)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }

            // Playback controls (Skip Back, Play/Pause, Skip Forward)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                        engine.skipPrevious()
                    }
                ) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Previous sentence")
                }

                FilledIconButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                        if (engine.isSpeaking) engine.pause() else engine.play()
                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (engine.isSpeaking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (engine.isSpeaking) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                        engine.skipNext()
                    }
                ) {
                    Icon(Icons.Rounded.Forward10, contentDescription = "Next sentence")
                }
            }

            // Close button
            IconButton(
                onClick = {
                    engine.stop()
                    onClose()
                }
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close player")
            }
        }
    }
}
