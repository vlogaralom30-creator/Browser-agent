package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.agent.AgentRepository
import com.example.data.agent.ApiKeyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiResponse {
    data class Success(
        val text: String?,
        val functionCalls: List<FunctionCall>,
        val usedKeyId: Long,
        val rawResponse: JSONObject
    ) : GeminiResponse()

    data class Error(
        val message: String,
        val isQuotaExhausted: Boolean = false,
        val isRateLimit: Boolean = false
    ) : GeminiResponse()
}

data class FunctionCall(
    val name: String,
    val args: JSONObject
)

data class GeminiModelInfo(
    val name: String,
    val displayName: String,
    val description: String,
    val supportedGenerationMethods: List<String>
)

class GeminiApiClient(
    private val agentRepository: AgentRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateWithFailover(
        model: String,
        contents: JSONArray,
        systemInstruction: String? = null,
        includeTools: Boolean = true,
        temperature: Float = 0.2f
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val enabledKeys = agentRepository.apiKeyDao.getEnabledApiKeys()
        if (enabledKeys.isEmpty()) {
            return@withContext GeminiResponse.Error("No enabled Gemini API keys found. Please add or enable an API key in Model/API settings.")
        }

        val sortedKeys = enabledKeys.sortedWith(
            compareBy<ApiKeyEntity> { it.status != "ACTIVE" && it.status != "WORKING" }
                .thenBy { it.failCount }
                .thenBy { it.priorityOrder }
        )

        var lastError = "All API keys failed or exhausted quota."
        var isLastQuota = false
        var isLastRateLimit = false

        for (apiKeyEntity in sortedKeys) {
            try {
                val requestPayload = JSONObject().apply {
                    put("contents", contents)

                    if (systemInstruction != null) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", systemInstruction) })
                            })
                        })
                    }

                    if (includeTools) {
                        put("tools", BrowserTools.getGeminiToolsDeclaration())
                    }

                    put("generationConfig", JSONObject().apply {
                        put("temperature", temperature)
                        put("topP", 0.95)
                        put("topK", 40)
                    })
                }

                // Resolve model name with fallback for obsolete models
                var targetModel = model.removePrefix("models/")
                if (targetModel.contains("gemini-2.5-flash")) {
                    targetModel = "gemini-2.0-flash"
                }
                var url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=${apiKeyEntity.apiKey}"

                var request = Request.Builder()
                    .url(url)
                    .post(requestPayload.toString().toRequestBody(jsonMediaType))
                    .build()

                var response: Response = client.newCall(request).execute()
                var responseCode = response.code
                var responseBody = response.body?.string() ?: ""

                // Fallback attempt if chosen model is no longer available
                if (!response.isSuccessful && (responseBody.contains("no longer available", ignoreCase = true) || responseBody.contains("not found", ignoreCase = true))) {
                    val fallbackModel = "gemini-1.5-flash"
                    val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/$fallbackModel:generateContent?key=${apiKeyEntity.apiKey}"
                    val fallbackReq = Request.Builder()
                        .url(fallbackUrl)
                        .post(requestPayload.toString().toRequestBody(jsonMediaType))
                        .build()
                    val fallbackResp = client.newCall(fallbackReq).execute()
                    if (fallbackResp.isSuccessful) {
                        response = fallbackResp
                        responseCode = fallbackResp.code
                        responseBody = fallbackResp.body?.string() ?: ""
                    }
                }

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")

                    val functionCalls = mutableListOf<FunctionCall>()
                    val textBuilder = StringBuilder()

                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                textBuilder.append(part.getString("text"))
                            }
                            if (part.has("functionCall")) {
                                val fc = part.getJSONObject("functionCall")
                                val name = fc.getString("name")
                                val args = fc.optJSONObject("args") ?: JSONObject()
                                functionCalls.add(FunctionCall(name, args))
                            }
                        }
                    }

                    agentRepository.markKeySuccess(apiKeyEntity.id)
                    return@withContext GeminiResponse.Success(
                        text = if (textBuilder.isNotEmpty()) textBuilder.toString() else null,
                        functionCalls = functionCalls,
                        usedKeyId = apiKeyEntity.id,
                        rawResponse = json
                    )
                } else {
                    // Handle API Errors and Failover
                    val isQuota = responseCode == 429 || responseBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || responseBody.contains("quota", ignoreCase = true)
                    val isRate = responseCode == 429
                    isLastQuota = isQuota
                    isLastRateLimit = isRate

                    val errorMsg = try {
                        val errJson = JSONObject(responseBody)
                        errJson.optJSONObject("error")?.optString("message") ?: "HTTP $responseCode"
                    } catch (e: Exception) {
                        "HTTP $responseCode: $responseBody"
                    }

                    agentRepository.markKeyFailure(apiKeyEntity.id, errorMsg, isQuotaExhausted = isQuota)
                    lastError = "Key ${apiKeyEntity.getMaskedKey()} failed: $errorMsg. Retrying with next available key..."
                    Log.w("GeminiApiClient", "API key failover triggered: HTTP $responseCode")
                    // Loop continues to next key in sortedKeys
                }
            } catch (e: IOException) {
                agentRepository.markKeyFailure(apiKeyEntity.id, "Network error: ${e.message}", isQuotaExhausted = false)
                lastError = "Network error with ${apiKeyEntity.getMaskedKey()}: ${e.message}"
            } catch (e: Exception) {
                lastError = "Error: ${e.localizedMessage}"
            }
        }

        GeminiResponse.Error(
            message = lastError,
            isQuotaExhausted = isLastQuota,
            isRateLimit = isLastRateLimit
        )
    }

    suspend fun testApiKey(apiKey: String, model: String = "gemini-2.0-flash"): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            var sanitizedModel = model.removePrefix("models/")
            if (sanitizedModel.contains("gemini-2.5-flash")) {
                sanitizedModel = "gemini-2.0-flash"
            }
            var url = "https://generativelanguage.googleapis.com/v1beta/models/$sanitizedModel:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Ping test. Reply with 'OK'.") })
                        })
                    })
                })
            }

            var request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            var response = client.newCall(request).execute()
            var body = response.body?.string() ?: ""

            // Fallback retry if model is obsolete
            if (!response.isSuccessful && (body.contains("no longer available", ignoreCase = true) || body.contains("not found", ignoreCase = true))) {
                val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
                val fallbackReq = Request.Builder()
                    .url(fallbackUrl)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                val fallbackResp = client.newCall(fallbackReq).execute()
                if (fallbackResp.isSuccessful) {
                    response = fallbackResp
                    body = fallbackResp.body?.string() ?: ""
                }
            }

            if (response.isSuccessful) {
                Pair(true, "Key is active and verified.")
            } else {
                val msg = try {
                    JSONObject(body).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                Pair(false, msg)
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.localizedMessage}")
        }
    }

    suspend fun fetchAvailableModels(): List<GeminiModelInfo> = withContext(Dispatchers.IO) {
        val enabledKeys = agentRepository.apiKeyDao.getEnabledApiKeys()
        val defaultModels = listOf(
            GeminiModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", "Ultra-fast, state-of-the-art model for multimodal browser automation.", listOf("generateContent")),
            GeminiModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", "Lightweight, high-speed model for web automation.", listOf("generateContent")),
            GeminiModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", "Advanced complex reasoning and tool calling.", listOf("generateContent")),
            GeminiModelInfo("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite", "Cost-effective, rapid execution model.", listOf("generateContent"))
        )

        val activeKey = enabledKeys.firstOrNull()?.apiKey ?: return@withContext defaultModels

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$activeKey"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val modelsArray = json.optJSONArray("models") ?: return@withContext defaultModels
                val fetched = mutableListOf<GeminiModelInfo>()
                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i)
                    val rawName = m.getString("name")
                    val cleanName = rawName.removePrefix("models/")
                    val methods = mutableListOf<String>()
                    val methodsArr = m.optJSONArray("supportedGenerationMethods")
                    if (methodsArr != null) {
                        for (j in 0 until methodsArr.length()) {
                            methods.add(methodsArr.getString(j))
                        }
                    }

                    // Filter only models that support content generation and are gemini
                    if (methods.contains("generateContent") && cleanName.startsWith("gemini", ignoreCase = true)) {
                        fetched.add(
                            GeminiModelInfo(
                                name = cleanName,
                                displayName = m.optString("displayName", cleanName),
                                description = m.optString("description", "Gemini model"),
                                supportedGenerationMethods = methods
                            )
                        )
                    }
                }
                if (fetched.isNotEmpty()) {
                    return@withContext fetched
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiApiClient", "Failed to fetch live models: ${e.message}")
        }
        return@withContext defaultModels
    }

    companion object {
        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }
}
