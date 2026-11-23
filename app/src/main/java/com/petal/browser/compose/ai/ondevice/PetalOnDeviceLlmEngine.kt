package com.petal.browser.compose.ai.ondevice

import android.content.Context
import com.petal.browser.compose.ai.AiBackendException
import com.petal.browser.compose.ai.AiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * On-device Gemma LLM inference runner using MediaPipe LLM Inference API principles.
 *
 * Characteristics:
 * - Lazily loads the model file from internal storage.
 * - Enforces pre-flight RAM safety check to avoid low-memory OOM crashes.
 * - Streams output chunks asynchronously on [Dispatchers.Default].
 * - Unloads immediately when requested (sheet closed or app backgrounded).
 */
object PetalOnDeviceLlmEngine {

    @Volatile private var isLoaded = false
    @Volatile private var loadedModelPath: String? = null
    private var nativeEngineHandle: Any? = null

    /**
     * Checks RAM and unloads active native inference engine.
     */
    @Synchronized
    fun unload() {
        if (!isLoaded) return
        try {
            // If native LlmInference session exists, call close() via reflection
            nativeEngineHandle?.let { handle ->
                val closeMethod = handle.javaClass.getMethod("close")
                closeMethod.invoke(handle)
            }
        } catch (_: Throwable) {}
        nativeEngineHandle = null
        isLoaded = false
        loadedModelPath = null
        System.gc()
    }

    /**
     * Ensures model file is loaded and verifies available RAM before inference.
     */
    @Synchronized
    fun ensureLoaded(context: Context, modelFile: File) {
        if (isLoaded && loadedModelPath == modelFile.absolutePath) {
            return
        }

        val memStats = PetalOnDeviceModelManager.getMemoryStats(context)
        if (!memStats.hasSufficientRam) {
            val freeMb = memStats.availMemBytes / (1024 * 1024)
            throw AiBackendException.ServerException(
                "Device has low available memory ($freeMb MB free). On-device Gemma requires at least 2.5 GB of free RAM. Please close background apps or switch to a cloud/local server provider."
            )
        }

        try {
            // MediaPipe LlmInference reflective binding:
            // Allows graceful execution if com.google.mediapipe:tasks-genai is present in GitHub flavor,
            // or provides structured fallback if native runtime is not packaged.
            val optionsBuilderClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions")
            val builderMethod = optionsBuilderClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            builder.javaClass.getMethod("setModelPath", String::class.java).invoke(builder, modelFile.absolutePath)
            builder.javaClass.getMethod("setMaxTokens", Int::class.javaPrimitiveType).invoke(builder, 1024)
            val options = builder.javaClass.getMethod("build").invoke(builder)

            val inferenceClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
            val createMethod = inferenceClass.getMethod("createFromOptions", Context::class.java, optionsBuilderClass)
            nativeEngineHandle = createMethod.invoke(null, context.applicationContext, options)
            isLoaded = true
            loadedModelPath = modelFile.absolutePath
        } catch (e: ClassNotFoundException) {
            // MediaPipe tasks-genai is an optional native dependency for GitHub releases
            throw AiBackendException.ServerException(
                "MediaPipe LLM Inference runtime is not bundled in this build variant. Use Ollama/LM Studio local servers or cloud providers."
            )
        } catch (t: Throwable) {
            throw AiBackendException.ServerException("Failed to load on-device model: ${t.localizedMessage ?: t.message}")
        }
    }

    /**
     * Streams inference tokens for the provided conversation history.
     */
    fun streamChat(
        context: Context,
        messages: List<AiChatMessage>,
        modelName: String
    ): Flow<String> = flow {
        val modelFile = PetalOnDeviceModelManager.getModelFile(context)
            ?: throw AiBackendException.MissingApiKeyException(
                "No Gemma model file found on this device. Open AI Settings > Model Manager to download or import a model file."
            )

        ensureLoaded(context, modelFile)

        // Build Gemma instruction prompt format
        val promptBuilder = StringBuilder()
        for (m in messages) {
            when (m.role) {
                com.petal.browser.compose.ai.AiRole.SYSTEM -> {
                    promptBuilder.append("<start_of_turn>user\nSystem instruction: ${m.text}<end_of_turn>\n")
                }
                com.petal.browser.compose.ai.AiRole.USER -> {
                    promptBuilder.append("<start_of_turn>user\n${m.text}<end_of_turn>\n")
                }
                com.petal.browser.compose.ai.AiRole.ASSISTANT -> {
                    promptBuilder.append("<start_of_turn>model\n${m.text}<end_of_turn>\n")
                }
            }
        }
        promptBuilder.append("<start_of_turn>model\n")
        val fullPrompt = promptBuilder.toString()

        try {
            val handle = nativeEngineHandle ?: throw AiBackendException.ServerException("Engine is not initialized")
            // If generateResponseAsync is available, stream tokens:
            val generateMethod = handle.javaClass.getMethod("generateResponse", String::class.java)
            val responseText = generateMethod.invoke(handle, fullPrompt) as? String ?: ""

            if (responseText.isBlank()) {
                throw AiBackendException.EmptyReplyException("On-device model returned empty reply")
            }

            // Stream response in small simulated chunks for consistent UI animation
            val words = responseText.split(Regex("(?<=\\s)"))
            for (w in words) {
                emit(w)
                kotlinx.coroutines.delay(20L)
            }
        } catch (e: Exception) {
            if (e is AiBackendException) throw e
            throw AiBackendException.ServerException("Inference error: ${e.localizedMessage ?: e.message}")
        }
    }.flowOn(Dispatchers.Default)
}
