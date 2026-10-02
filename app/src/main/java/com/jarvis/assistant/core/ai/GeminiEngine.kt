package com.jarvis.assistant.core.ai

import android.content.Context
import android.util.Log
import com.jarvis.assistant.core.voice.ConversationMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiEngine(private val context: Context) : AIProvider {
    override val name: String = "Gemini"
    private val tag = "GeminiEngine"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    // Default JARVIS Persona system instruction
    private val defaultJarvisSystemPrompt = """
        You are JARVIS, a calm, highly capable personal AI assistant and Android controller for a Samsung Galaxy Tab S5e.
        Your tone is concise, intelligent, respectful, and articulate.
        Never repeat "How can I help you?". Keep spoken answers crisp and actionable.
        When assisting with Class 11 Physics, Chemistry, and Mathematics DPP problems, provide hints and verify steps instead of jumping directly to the final answer.
        If a question cannot be completed due to Android permissions or security, explain the technical limitation honestly without pretending.
    """.trimIndent()

    override suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<ConversationMessage>,
        systemInstruction: String?
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext "Please configure your Gemini API Key in JARVIS Settings to enable cloud reasoning."
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val contentsArray = JSONArray()

            // Include recent conversation context (last 6 turns)
            val recentTurns = conversationHistory.takeLast(6)
            for (msg in recentTurns) {
                val role = if (msg.sender == "USER") "user" else "model"
                val partObj = JSONObject().put("text", msg.text)
                contentsArray.put(JSONObject().put("role", role).put("parts", JSONArray().put(partObj)))
            }

            // Append current prompt
            val currentPart = JSONObject().put("text", prompt)
            contentsArray.put(JSONObject().put("role", "user").put("parts", JSONArray().put(currentPart)))

            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
                val sysPrompt = systemInstruction ?: defaultJarvisSystemPrompt
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysPrompt))))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 800)
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(tag, "Gemini API error ${response.code}: $errBody")
                    return@withContext "I cannot reach Gemini right now (Code ${response.code})."
                }

                val bodyStr = response.body?.string() ?: return@withContext "Received empty response from Gemini."
                val json = JSONObject(bodyStr)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No response text.")
                    }
                }
                return@withContext "Gemini did not return any answer."
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception querying Gemini: ${e.message}", e)
            return@withContext "Connection to Gemini failed: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    override suspend fun analyzeImage(
        prompt: String,
        base64ImageData: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext "Please configure your Gemini API Key in Settings for image and DPP analysis."
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val imagePart = JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", mimeType)
                    put("data", base64ImageData)
                })
            }
            val textPart = JSONObject().put("text", prompt)

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(imagePart).put(textPart))
                ))
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Image analysis failed with error code ${response.code}."
                }
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val candidates = json.optJSONArray("candidates")
                val text = candidates?.getJSONObject(0)?.getJSONObject("content")?.getJSONArray("parts")?.getJSONObject(0)?.getString("text")
                return@withContext text ?: "Could not extract analysis from image."
            }
        } catch (e: Exception) {
            return@withContext "Visual analysis failed: ${e.localizedMessage}"
        }
    }

    private fun getApiKey(): String? {
        val prefs = context.getSharedPreferences("jarvis_secure_prefs", Context.MODE_PRIVATE)
        return prefs.getString("gemini_api_key", null)
    }

    fun saveApiKey(key: String) {
        val prefs = context.getSharedPreferences("jarvis_secure_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key).apply()
    }
}
