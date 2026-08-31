package com.petal.browser.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.ui.theme.PetalExpressiveTheme

/**
 * Minimal standalone [LifecycleOwner] for the voice search [BottomSheetDialog].
 *
 * The ComposeView inside the dialog previously had its tree lifecycle owner set
 * to the hosting Activity. Since the Activity stays RESUMED for as long as the
 * app is in the foreground, ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
 * never actually disposed the sheet's composition when the dialog was dismissed -
 * only when the whole Activity was destroyed. That meant the DisposableEffect
 * inside PetalVoiceSearchSheet (which cancels/destroys the SpeechRecognizer)
 * never ran on dismiss: every voice search left its SpeechRecognizer instance
 * alive and still bound to the system speech-recognition service. Repeated use
 * piled up leaked recognizer instances/binder connections until the app hung
 * (browser "not responding") the next time it tried to touch the mic.
 *
 * Giving the dialog its own short-lived LifecycleOwner - moved to DESTROYED
 * from the dialog's own onDismissListener - makes the composition (and its
 * DisposableEffect cleanup) tear down exactly when the sheet closes.
 */
private class VoiceSearchDialogLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

object PetalVoiceSearchBridge {
    private var activeDialog: BottomSheetDialog? = null
    private var activeLifecycleOwner: VoiceSearchDialogLifecycleOwner? = null

    @JvmStatic
    fun showVoiceSearchSheet(
        activity: ComponentActivity,
        onResult: (String) -> Unit
    ) {
        activity.runOnUiThread {
            activeDialog?.dismiss()
            activeDialog = null
            activeLifecycleOwner?.let { it.registry.currentState = Lifecycle.State.DESTROYED }
            activeLifecycleOwner = null

            val dialog = BottomSheetDialog(activity)
            activeDialog = dialog

            val dialogLifecycleOwner = VoiceSearchDialogLifecycleOwner()
            activeLifecycleOwner = dialogLifecycleOwner
            dialogLifecycleOwner.registry.currentState = Lifecycle.State.RESUMED

            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(dialogLifecycleOwner)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    PetalExpressiveTheme {
                        PetalVoiceSearchSheet(
                            onResult = { result ->
                                try {
                                    if (dialog.isShowing) {
                                        dialog.dismiss()
                                    }
                                } catch (e: Exception) {}
                                onResult(result)
                            },
                            onDismiss = {
                                try {
                                    if (dialog.isShowing) {
                                        dialog.dismiss()
                                    }
                                } catch (e: Exception) {}
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.setOnDismissListener {
                // This is what now actually releases the SpeechRecognizer: moving the
                // lifecycle to DESTROYED disposes the composition, which runs the
                // DisposableEffect's onDispose in PetalVoiceSearchSheet.
                dialogLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
                if (activeDialog == dialog) {
                    activeDialog = null
                }
                if (activeLifecycleOwner == dialogLifecycleOwner) {
                    activeLifecycleOwner = null
                }
            }
            dialog.show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalVoiceSearchSheet(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var spokenText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Listening...") }
    var isListening by remember { mutableStateOf(false) }
    var rmsValue by remember { mutableFloatStateOf(0f) }
    var recognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var hasDeliveredResult by remember { mutableStateOf(false) }

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    val pulseScale by animateFloatAsState(
        targetValue = if (isListening) 1.0f + (rmsValue / 10f).coerceIn(0f, 0.4f) else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "MicPulse"
    )

    fun startOrRestartListening() {
        // Guard against ever reaching the system speech service without RECORD_AUDIO
        // granted - on many OEM builds that call hangs instead of failing fast via
        // onError, which is what was freezing the browser after this sheet was shown.
        if (!hasAudioPermission()) {
            statusText = "Microphone permission is required for voice search."
            isListening = false
            return
        }
        try {
            recognizerRef?.destroy()
            recognizerRef = null

            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizerRef = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        statusText = "Listening..."
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        statusText = "Listening to your query..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        rmsValue = rmsdB
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        statusText = "Processing voice..."
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        statusText = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap mic to retry."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Tap mic to retry."
                            else -> "Voice recognition error. Tap mic to retry."
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty() && !hasDeliveredResult) {
                            val text = matches[0].trim()
                            if (text.isNotEmpty()) {
                                hasDeliveredResult = true
                                spokenText = text
                                isListening = false
                                onResult(text)
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            spokenText = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            } else {
                statusText = "Speech recognition unavailable on device."
                isListening = false
            }
        } catch (e: Exception) {
            statusText = "Voice search unavailable."
            isListening = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startOrRestartListening()
        } else {
            statusText = "Microphone permission is required for voice search."
            isListening = false
        }
    }

    DisposableEffect(Unit) {
        if (hasAudioPermission()) {
            startOrRestartListening()
        } else {
            statusText = "Requesting microphone permission..."
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        onDispose {
            try {
                recognizerRef?.cancel()
                recognizerRef?.destroy()
                recognizerRef = null
            } catch (e: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Animated Mic Container with Material 3 Expressive Primary Colors
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (isListening) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable {
                        if (!isListening) {
                            if (hasAudioPermission()) {
                                startOrRestartListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = "Voice Search Mic",
                    tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            if (spokenText.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = spokenText,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Text(
                    text = "Try saying: \"Weather in India\" or \"Open github.com\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
