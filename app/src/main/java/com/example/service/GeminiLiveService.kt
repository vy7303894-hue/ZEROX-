package com.example.service

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.tools.DeviceTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class GeminiLiveResult {
    data class Success(
        val text: String,
        val audioBytes: ByteArray?,
        val audioMimeType: String?,
        val executedTool: String? = null
    ) : GeminiLiveResult()

    data class Error(val message: String) : GeminiLiveResult()
}

data class ChatMessage(
    val role: String, // "user", "model", or "tool"
    val text: String? = null,
    val audioBytes: ByteArray? = null,
    val toolCallName: String? = null,
    val toolCallArgs: JSONObject? = null,
    val toolResponseResult: String? = null
)

class GeminiLiveService(private val deviceTools: DeviceTools) {
    companion object {
        private const val TAG = "GeminiLiveService"
        // Recommended audio-to-audio native models
        private const val MODEL_NATIVE_AUDIO = "gemini-2.5-flash-native-audio-preview-12-2025"
        private const val MODEL_FALLBACK_AUDIO = "gemini-3.5-flash"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<ChatMessage>()
    var selectedVoice: String = ZoyaPersona.DEFAULT_VOICE
    var sassLevel: Int = 2

    fun resetSession() {
        conversationHistory.clear()
    }

    suspend fun sendAudioTurn(
        pcmAudio16k: ByteArray,
        userPromptHint: String? = null
    ): GeminiLiveResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiLiveResult.Error("API Key is missing. Please add your Gemini API Key in the Secrets Panel.")
        }

        val base64Audio = Base64.encodeToString(pcmAudio16k, Base64.NO_WRAP)
        
        // Add user audio turn
        conversationHistory.add(ChatMessage(role = "user", text = userPromptHint, audioBytes = pcmAudio16k))

        val requestJson = buildRequestBodyJson(base64Audio = base64Audio, textPrompt = userPromptHint)
        executeRequestWithFallback(apiKey, requestJson)
    }

    suspend fun sendTextTurn(textPrompt: String): GeminiLiveResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiLiveResult.Error("API Key is missing. Please add your Gemini API Key in the Secrets Panel.")
        }

        conversationHistory.add(ChatMessage(role = "user", text = textPrompt))
        val requestJson = buildRequestBodyJson(base64Audio = null, textPrompt = textPrompt)
        executeRequestWithFallback(apiKey, requestJson)
    }

    private suspend fun executeRequestWithFallback(apiKey: String, requestJson: JSONObject): GeminiLiveResult {
        // Try native audio model first, then fallback
        val modelsToTry = listOf(MODEL_NATIVE_AUDIO, MODEL_FALLBACK_AUDIO)
        var lastError = "Request failed"

        for (model in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Model $model returned HTTP ${response.code}: $responseBody")
                        lastError = "HTTP ${response.code}: $responseBody"
                        return@use // try next model
                    }

                    return parseGeminiResponse(responseBody)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling model $model: ${e.message}", e)
                lastError = e.message ?: "Network error"
            }
        }

        return GeminiLiveResult.Error(lastError)
    }

    private fun parseGeminiResponse(responseBody: String): GeminiLiveResult {
        return try {
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return GeminiLiveResult.Error("Zoya received no response candidates.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            if (parts == null || parts.length() == 0) {
                return GeminiLiveResult.Error("Empty response from Zoya.")
            }

            var responseText = ""
            var audioBytes: ByteArray? = null
            var audioMimeType: String? = null
            var executedToolMessage: String? = null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)

                // 1. Text part
                if (part.has("text")) {
                    val t = part.getString("text")
                    responseText += (if (responseText.isNotEmpty()) "\n" else "") + t
                }

                // 2. Inline Audio Data part
                if (part.has("inlineData")) {
                    val inlineData = part.getJSONObject("inlineData")
                    val mime = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                    val base64Data = inlineData.optString("data", "")
                    if (base64Data.isNotEmpty()) {
                        audioMimeType = mime
                        audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }

                // 3. Function Call part
                if (part.has("functionCall")) {
                    val functionCall = part.getJSONObject("functionCall")
                    val name = functionCall.optString("name")
                    val args = functionCall.optJSONObject("args") ?: JSONObject()
                    val toolResult = handleToolCall(name, args)
                    executedToolMessage = "[$name] $toolResult"
                    if (responseText.isEmpty()) {
                        responseText = toolResult
                    }
                }
            }

            if (responseText.isBlank() && audioBytes == null && executedToolMessage == null) {
                responseText = "I'm listening darling, tell me more!"
            }

            // Record response in conversation history
            conversationHistory.add(ChatMessage(role = "model", text = responseText, audioBytes = audioBytes))

            // Keep conversation history compact (last 10 turns) to minimize latency
            while (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
            }

            GeminiLiveResult.Success(
                text = responseText,
                audioBytes = audioBytes,
                audioMimeType = audioMimeType,
                executedTool = executedToolMessage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response: ${e.message}", e)
            GeminiLiveResult.Error("Failed to parse response: ${e.message}")
        }
    }

    private fun handleToolCall(toolName: String, args: JSONObject): String {
        return when (toolName) {
            "openWebsite" -> {
                val url = args.optString("url", "https://google.com")
                deviceTools.openWebsite(url)
            }
            "toggleFlashlight" -> {
                val enable = args.optBoolean("enable", true)
                deviceTools.toggleFlashlight(enable)
            }
            "searchGoogle" -> {
                val query = args.optString("query", "Zoya AI")
                deviceTools.searchGoogle(query)
            }
            "playMusic" -> {
                val query = args.optString("query", "trending songs")
                deviceTools.playMusic(query)
            }
            "getDeviceInfo" -> {
                deviceTools.getDeviceInfo()
            }
            "openClockApp" -> {
                deviceTools.openClockApp()
            }
            "setTimer" -> {
                val seconds = args.optInt("seconds", 60)
                val label = args.optString("label", "Zoya Timer")
                deviceTools.setTimer(seconds, label)
            }
            else -> "Tool $toolName not recognized"
        }
    }

    private fun buildRequestBodyJson(base64Audio: String?, textPrompt: String?): JSONObject {
        val root = JSONObject()

        // 1. System Instruction
        val systemInstruction = JSONObject()
        val sysParts = JSONArray()
        sysParts.put(JSONObject().put("text", ZoyaPersona.buildSystemInstruction(sassLevel)))
        systemInstruction.put("parts", sysParts)
        root.put("systemInstruction", systemInstruction)

        // 2. Tools
        val toolsArray = JSONArray()
        val toolsObj = JSONObject()
        val funcArray = JSONArray()

        // openWebsite
        funcArray.put(JSONObject().apply {
            put("name", "openWebsite")
            put("description", "Opens any URL or website in the device's web browser.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The website URL to launch, e.g. https://instagram.com or reddit.com")
                    })
                })
                put("required", JSONArray().put("url"))
            })
        })

        // toggleFlashlight
        funcArray.put(JSONObject().apply {
            put("name", "toggleFlashlight")
            put("description", "Turns the device flashlight / camera torch ON or OFF.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("enable", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "true to illuminate the flashlight, false to turn it off")
                    })
                })
                put("required", JSONArray().put("enable"))
            })
        })

        // searchGoogle
        funcArray.put(JSONObject().apply {
            put("name", "searchGoogle")
            put("description", "Performs a web search on Google.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The query to search on Google")
                    })
                })
                put("required", JSONArray().put("query"))
            })
        })

        // playMusic
        funcArray.put(JSONObject().apply {
            put("name", "playMusic")
            put("description", "Searches and plays a song or music video on YouTube.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The song name, artist, or music genre")
                    })
                })
                put("required", JSONArray().put("query"))
            })
        })

        // getDeviceInfo
        funcArray.put(JSONObject().apply {
            put("name", "getDeviceInfo")
            put("description", "Returns the device battery percentage, charging state, local time, and device model.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // setTimer
        funcArray.put(JSONObject().apply {
            put("name", "setTimer")
            put("description", "Sets a countdown timer on the device.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("seconds", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Timer duration in seconds")
                    })
                    put("label", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional label for the timer")
                    })
                })
                put("required", JSONArray().put("seconds"))
            })
        })

        toolsObj.put("functionDeclarations", funcArray)
        toolsArray.put(toolsObj)
        root.put("tools", toolsArray)

        // 3. Contents (History + Current Turn)
        val contentsArray = JSONArray()

        // Include recent history
        val recentHistory = conversationHistory.takeLast(6)
        for (item in recentHistory) {
            val contentObj = JSONObject()
            contentObj.put("role", item.role)
            val partsArr = JSONArray()

            if (!item.text.isNullOrBlank()) {
                partsArr.put(JSONObject().put("text", item.text))
            }
            if (partsArr.length() > 0) {
                contentObj.put("parts", partsArr)
                contentsArray.put(contentObj)
            }
        }

        // Current turn
        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()

        if (base64Audio != null) {
            currentParts.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Audio)
                })
            })
        }
        if (!textPrompt.isNullOrBlank()) {
            currentParts.put(JSONObject().put("text", textPrompt))
        }

        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)
        root.put("contents", contentsArray)

        // 4. Generation Config
        val genConfig = JSONObject().apply {
            put("temperature", 0.85)
            put("topP", 0.95)
            put("responseModalities", JSONArray().apply {
                put("AUDIO")
                put("TEXT")
            })
            put("speechConfig", JSONObject().apply {
                put("voiceConfig", JSONObject().apply {
                    put("prebuiltVoiceConfig", JSONObject().apply {
                        put("voiceName", selectedVoice)
                    })
                })
            })
        }
        root.put("generationConfig", genConfig)

        return root
    }
}
