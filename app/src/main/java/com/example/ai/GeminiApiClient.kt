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
                val isGroq = apiKeyEntity.provider == "GROQ" ||
                        apiKeyEntity.apiKey.startsWith("gsk_") ||
                        apiKeyEntity.baseUrl.contains("groq.com")

                val isOpenRouterOrOpenAiOrGroq = isGroq ||
                        apiKeyEntity.provider == "OPENROUTER" ||
                        apiKeyEntity.provider == "OPENAI" ||
                        apiKeyEntity.apiKey.startsWith("sk-or-") ||
                        apiKeyEntity.apiKey.startsWith("sk-") ||
                        apiKeyEntity.baseUrl.isNotBlank()

                if (isOpenRouterOrOpenAiOrGroq) {
                    val result = executeOpenAiCompatibleRequest(
                        apiKeyEntity = apiKeyEntity,
                        model = model,
                        contents = contents,
                        systemInstruction = systemInstruction,
                        includeTools = includeTools,
                        temperature = temperature
                    )
                    agentRepository.markKeySuccess(apiKeyEntity.id)
                    return@withContext result
                }

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
                agentRepository.markKeyFailure(apiKeyEntity.id, e.message ?: "Unknown error", isQuotaExhausted = false)
                lastError = "Error with ${apiKeyEntity.getMaskedKey()}: ${e.localizedMessage}"
            }
        }

        GeminiResponse.Error(
            message = lastError,
            isQuotaExhausted = isLastQuota,
            isRateLimit = isLastRateLimit
        )
    }

    private fun executeOpenAiCompatibleRequest(
        apiKeyEntity: ApiKeyEntity,
        model: String,
        contents: JSONArray,
        systemInstruction: String?,
        includeTools: Boolean,
        temperature: Float
    ): GeminiResponse {
        val isGroq = apiKeyEntity.provider == "GROQ" || apiKeyEntity.apiKey.startsWith("gsk_") || apiKeyEntity.baseUrl.contains("groq.com")
        val isOpenRouter = apiKeyEntity.provider == "OPENROUTER" || apiKeyEntity.apiKey.startsWith("sk-or-") || apiKeyEntity.baseUrl.contains("openrouter")

        val endpoint = if (apiKeyEntity.baseUrl.isNotBlank()) {
            val base = apiKeyEntity.baseUrl.trimEnd('/')
            if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        } else if (isGroq) {
            "https://api.groq.com/openai/v1/chat/completions"
        } else {
            "https://openrouter.ai/api/v1/chat/completions"
        }

        var targetModel = model.removePrefix("models/")
        if (isGroq) {
            if (targetModel.contains("gemini") || targetModel.isBlank() || targetModel == "gpt-4o-mini" || targetModel == "llama-3.3-70b-versatile") {
                targetModel = "llama-3.1-8b-instant"
            }
        } else if (isOpenRouter || apiKeyEntity.provider == "OPENROUTER" || apiKeyEntity.apiKey.startsWith("sk-or-")) {
            if (targetModel == "google/gemini-1.5-flash:free" || targetModel == "google/gemini-2.0-flash-exp:free" || targetModel.contains("gemini-2.5-flash")) {
                targetModel = "inclusionai/ling-3.0-flash-sante:free"
            }
        } else if (targetModel.contains("gemini-2.5-flash")) {
            targetModel = "gemini-2.0-flash"
        }

        val messages = JSONArray()

        if (!systemInstruction.isNullOrBlank()) {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
        }

        for (i in 0 until contents.length()) {
            val c = contents.getJSONObject(i)
            val role = c.optString("role", "user")
            val openAiRole = if (role == "model") "assistant" else if (role == "system") "system" else "user"

            val parts = c.optJSONArray("parts")
            val textBuilder = StringBuilder()
            val toolCallsList = JSONArray()

            if (parts != null) {
                for (j in 0 until parts.length()) {
                    val part = parts.getJSONObject(j)
                    if (part.has("text")) {
                        textBuilder.append(part.getString("text"))
                    }
                    if (part.has("functionCall")) {
                        val fc = part.getJSONObject("functionCall")
                        toolCallsList.put(JSONObject().apply {
                            put("id", "call_${System.currentTimeMillis()}_$j")
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", fc.getString("name"))
                                put("arguments", fc.optJSONObject("args")?.toString() ?: "{}")
                            })
                        })
                    }
                    if (part.has("functionResponse")) {
                        val fr = part.getJSONObject("functionResponse")
                        val name = fr.optString("name", "tool_result")
                        val respObj = fr.optJSONObject("response") ?: JSONObject()
                        messages.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", "call_$name")
                            put("content", respObj.toString())
                        })
                    }
                }
            }

            if (textBuilder.isNotEmpty() || toolCallsList.length() > 0) {
                val msgObj = JSONObject().apply {
                    put("role", openAiRole)
                    if (textBuilder.isNotEmpty()) {
                        put("content", textBuilder.toString())
                    } else {
                        put("content", "")
                    }
                    if (toolCallsList.length() > 0) {
                        put("tool_calls", toolCallsList)
                    }
                }
                messages.put(msgObj)
            }
        }

        val payload = JSONObject().apply {
            put("model", targetModel)
            put("messages", messages)
            if (includeTools) {
                put("tools", BrowserTools.getOpenAiToolsDeclaration())
            }
            put("temperature", temperature)
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer ${apiKeyEntity.apiKey.trim()}")
            .addHeader("HTTP-Referer", "https://github.com/vlogaralom30-creator/Browser-agent")
            .addHeader("X-Title", "Browser Agent")

        val response = client.newCall(reqBuilder.build()).execute()
        val responseCode = response.code
        val responseBody = response.body?.string() ?: ""

        if (response.isSuccessful) {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")

            var text: String? = message?.optString("content", null)
            if (text?.isBlank() == true) text = null

            val functionCalls = mutableListOf<FunctionCall>()
            val toolCalls = message?.optJSONArray("tool_calls")
            if (toolCalls != null) {
                for (i in 0 until toolCalls.length()) {
                    val tc = toolCalls.getJSONObject(i)
                    val func = tc.optJSONObject("function")
                    if (func != null) {
                        val name = func.getString("name")
                        val argsStr = func.optString("arguments", "{}")
                        val argsObj = try { JSONObject(argsStr) } catch (e: Exception) { JSONObject() }
                        functionCalls.add(FunctionCall(name, argsObj))
                    }
                }
            }

            return GeminiResponse.Success(
                text = text,
                functionCalls = functionCalls,
                usedKeyId = apiKeyEntity.id,
                rawResponse = json
            )
        } else {
            val errorMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "HTTP $responseCode"
            } catch (e: Exception) {
                "HTTP $responseCode: $responseBody"
            }
            throw Exception("API Error ($responseCode): $errorMsg")
        }
    }

    suspend fun testApiKey(apiKey: String, model: String = "gemini-2.0-flash", provider: String = "GEMINI", baseUrl: String = ""): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val isGroq = provider == "GROQ" || trimmedKey.startsWith("gsk_") || baseUrl.contains("groq.com")
        val isOpenRouter = provider == "OPENROUTER" || trimmedKey.startsWith("sk-or-") || baseUrl.contains("openrouter")
        val isOpenAi = provider == "OPENAI" || (trimmedKey.startsWith("sk-") && !isOpenRouter && !isGroq) || baseUrl.isNotBlank()

        if (isGroq || isOpenRouter || isOpenAi) {
            try {
                val endpoint = if (baseUrl.isNotBlank()) {
                    val base = baseUrl.trimEnd('/')
                    if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
                } else if (isGroq) {
                    "https://api.groq.com/openai/v1/chat/completions"
                } else {
                    "https://openrouter.ai/api/v1/chat/completions"
                }

                val testModel = if (isOpenRouter || provider == "OPENROUTER" || trimmedKey.startsWith("sk-or-")) {
                    if (model == "google/gemini-1.5-flash:free" || model == "google/gemini-2.0-flash-exp:free" || model.isBlank() || !model.contains("/")) {
                        "inclusionai/ling-3.0-flash-sante:free"
                    } else {
                        model
                    }
                } else if (isGroq) {
                    "llama-3.1-8b-instant"
                } else {
                    "gpt-4o-mini"
                }

                val payload = JSONObject().apply {
                    put("model", testModel)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Ping test. Reply OK.")
                        })
                    })
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .addHeader("Authorization", "Bearer $trimmedKey")
                    .addHeader("HTTP-Referer", "https://github.com/vlogaralom30-creator/Browser-agent")
                    .addHeader("X-Title", "Browser Agent")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val provLabel = if (isGroq) "Groq" else if (isOpenRouter) "OpenRouter" else "OpenAI"
                    return@withContext Pair(true, "$provLabel key is active and verified.")
                } else {
                    val msg = try {
                        JSONObject(body).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                    } catch (e: Exception) {
                        "HTTP ${response.code}"
                    }
                    return@withContext Pair(false, msg)
                }
            } catch (e: Exception) {
                return@withContext Pair(false, "Connection error: ${e.localizedMessage}")
            }
        }

        try {
            var sanitizedModel = model.removePrefix("models/")
            if (sanitizedModel.contains("gemini-2.5-flash")) {
                sanitizedModel = "gemini-2.0-flash"
            }
            var url = "https://generativelanguage.googleapis.com/v1beta/models/$sanitizedModel:generateContent?key=$trimmedKey"
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
                val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$trimmedKey"
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

        val defaultGroqModels = listOf(
            GeminiModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B Instant (Groq Free)", "Ultra-fast, low-latency 8B model on Groq.", listOf("generateContent")),
            GeminiModelInfo("llama3-70b-8192", "Llama 3 70B (Groq Free)", "Meta's 70B model with 8192 context on Groq.", listOf("generateContent")),
            GeminiModelInfo("deepseek-r1-distill-llama-70b", "DeepSeek R1 Distill Llama 70B (Groq Free)", "High-reasoning model distilled by DeepSeek on Groq.", listOf("generateContent")),
            GeminiModelInfo("mixtral-8x7b-32768", "Mixtral 8x7B 32k (Groq Free)", "Mistral AI MoE model with 32k context on Groq.", listOf("generateContent")),
            GeminiModelInfo("gemma2-9b-it", "Gemma 2 9B Instruct (Groq Free)", "Google's open weights Gemma 2 model on Groq.", listOf("generateContent"))
        )

        val defaultGeminiModels = listOf(
            GeminiModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", "Lightweight, high-speed model for web automation.", listOf("generateContent")),
            GeminiModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", "Ultra-fast, state-of-the-art model for multimodal browser automation.", listOf("generateContent")),
            GeminiModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", "Advanced complex reasoning and tool calling.", listOf("generateContent")),
            GeminiModelInfo("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite", "Cost-effective, rapid execution model.", listOf("generateContent"))
        )

        val defaultOpenRouterModels = listOf(
            GeminiModelInfo("inclusionai/ling-3.0-flash-sante:free", "Ling 3.0 Flash Sante (Free) [PRIMARY]", "Inclusion AI fast multimodal reasoning model on OpenRouter.", listOf("generateContent")),
            GeminiModelInfo("inclusionai/ling-3.0-flash:free", "Ling 3.0 Flash (Free)", "Inclusion AI Ling 3.0 Flash free tier model.", listOf("generateContent")),
            GeminiModelInfo("openrouter/free", "Free Models Auto Router [BEST FREE]", "Automatically routes prompts to the best active free model on OpenRouter.", listOf("generateContent")),
            GeminiModelInfo("google/gemini-2.0-flash-lite-001:free", "Gemini 2.0 Flash Lite (OpenRouter Free)", "Google's 2.0 Flash Lite via OpenRouter Free Tier.", listOf("generateContent")),
            GeminiModelInfo("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B Instruct (Free)", "Meta 70B open weights model via OpenRouter Free Tier.", listOf("generateContent")),
            GeminiModelInfo("deepseek/deepseek-r1:free", "DeepSeek R1 Reasoning (Free)", "DeepSeek premier reasoning model via OpenRouter Free Tier.", listOf("generateContent")),
            GeminiModelInfo("nvidia/nemotron-3-super:free", "Nemotron 3 Super (Free)", "NVIDIA Nemotron 3 Super model on OpenRouter Free.", listOf("generateContent")),
            GeminiModelInfo("nvidia/nemotron-3.5-lightning:free", "Nemotron 3.5 Lightning (Free)", "NVIDIA Nemotron 3.5 Lightning ultra-fast model.", listOf("generateContent")),
            GeminiModelInfo("google/gemma-4-31b:free", "Gemma 4 31B (Free)", "Google Gemma open weights 31B model.", listOf("generateContent")),
            GeminiModelInfo("google/gemma-4-26b-a4b:free", "Gemma 4 26B A4B (Free)", "Google Gemma 26B model.", listOf("generateContent")),
            GeminiModelInfo("liquid/lfm2.5-2.6b:free", "LFM 2.5 2.6B (Free)", "Liquid Foundation Model 2.5.", listOf("generateContent")),
            GeminiModelInfo("lingyiwan/ling-3.0-flash-sante:free", "Ling 3.0 Flash Sante (Free)", "Ling 3.0 Flash Sante model.", listOf("generateContent")),
            GeminiModelInfo("lingyiwan/ling-3.0-flash-fin:free", "Ling 3.0 Flash Fin (Free)", "Ling 3.0 Flash Fin model.", listOf("generateContent")),
            GeminiModelInfo("dots/dots3-note-preview:free", "Dots3-Note Preview (Free)", "Dots3 Note preview model.", listOf("generateContent")),
            GeminiModelInfo("flux/flux-tts:free", "Flux TTS (Free)", "Flux Text-To-Speech audio model.", listOf("generateContent")),
            GeminiModelInfo("inkling/inkling:free", "Inkling (Free)", "Inkling model on OpenRouter.", listOf("generateContent")),
            GeminiModelInfo("inkling/inkling-small:free", "Inkling Small (Free)", "Inkling Small model.", listOf("generateContent")),
            GeminiModelInfo("north/north-mini-code:free", "North Mini Code (Free)", "North Mini Code generation model.", listOf("generateContent")),
            GeminiModelInfo("laguna/laguna-s-2.1:free", "Laguna S 2.1 (Free)", "Laguna S 2.1 model.", listOf("generateContent")),
            GeminiModelInfo("laguna/laguna-xs-2.1:free", "Laguna XS 2.1 (Free)", "Laguna XS 2.1 model.", listOf("generateContent")),
            GeminiModelInfo("s2.1/s2.1-pro-free:free", "S2.1 Pro Free (Free)", "S2.1 Pro Free model.", listOf("generateContent")),
            GeminiModelInfo("nvidia/nemotron-3-ultra:free", "Nemotron 3 Ultra (Free)", "NVIDIA Nemotron 3 Ultra model.", listOf("generateContent")),
            GeminiModelInfo("nvidia/nemotron-3-nano-omni:free", "Nemotron 3 Nano Omni (Free)", "NVIDIA Nemotron 3 Nano Omni model.", listOf("generateContent")),
            GeminiModelInfo("nvidia/nemotron-3-embed-1b:free", "Nemotron 3 Embed 1B (Free)", "NVIDIA Nemotron 3 Embed 1B.", listOf("generateContent")),
            GeminiModelInfo("nvidia/llama-nemotron-rerank-vl-1b-v2:free", "Llama Nemotron Rerank VL 1B V2 (Free)", "NVIDIA Llama Nemotron Rerank VL 1B V2.", listOf("generateContent")),
            GeminiModelInfo("qwen/qwen-2.5-72b-instruct:free", "Qwen 2.5 72B Instruct (Free)", "Alibaba 72B instruct model via OpenRouter Free Tier.", listOf("generateContent")),
            GeminiModelInfo("mistralai/mistral-7b-instruct:free", "Mistral 7B Instruct (Free)", "Fast 7B model via OpenRouter Free Tier.", listOf("generateContent")),
            GeminiModelInfo("openai/gpt-4o-mini", "GPT-4o Mini", "OpenAI lightweight multimodal model.", listOf("generateContent")),
            GeminiModelInfo("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "Anthropic top reasoning model.", listOf("generateContent")),
            GeminiModelInfo("deepseek/deepseek-chat", "DeepSeek V3 Chat", "DeepSeek V3 chat model via OpenRouter.", listOf("generateContent"))
        )

        val activeKey = enabledKeys.firstOrNull() ?: return@withContext defaultGroqModels + defaultGeminiModels + defaultOpenRouterModels

        val isGroq = activeKey.provider == "GROQ" || activeKey.apiKey.startsWith("gsk_") || activeKey.baseUrl.contains("groq.com")
        val isOpenRouter = activeKey.provider == "OPENROUTER" || activeKey.apiKey.startsWith("sk-or-") || activeKey.baseUrl.contains("openrouter")

        if (isGroq) {
            try {
                val url = "https://api.groq.com/openai/v1/models"
                val request = Request.Builder().url(url).addHeader("Authorization", "Bearer ${activeKey.apiKey.trim()}").get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val dataArr = json.optJSONArray("data")
                    if (dataArr != null && dataArr.length() > 0) {
                        val fetched = mutableListOf<GeminiModelInfo>()
                        for (i in 0 until dataArr.length()) {
                            val m = dataArr.getJSONObject(i)
                            val id = m.getString("id")
                            fetched.add(
                                GeminiModelInfo(
                                    name = id,
                                    displayName = "$id (Groq Free)",
                                    description = "Groq ultra-fast LPU inference model",
                                    supportedGenerationMethods = listOf("generateContent")
                                )
                            )
                        }
                        if (fetched.isNotEmpty()) {
                            return@withContext fetched + defaultGroqModels + defaultGeminiModels + defaultOpenRouterModels
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiApiClient", "Failed to fetch Groq models: ${e.message}")
            }
            return@withContext defaultGroqModels + defaultGeminiModels + defaultOpenRouterModels
        }

        if (isOpenRouter) {
            try {
                val url = "https://openrouter.ai/api/v1/models"
                val request = Request.Builder().url(url).addHeader("Authorization", "Bearer ${activeKey.apiKey}").get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val dataArr = json.optJSONArray("data")
                    if (dataArr != null && dataArr.length() > 0) {
                        val fetched = mutableListOf<GeminiModelInfo>()
                        for (i in 0 until dataArr.length()) {
                            val m = dataArr.getJSONObject(i)
                            val id = m.getString("id")
                            val name = m.optString("name", id)
                            val desc = m.optString("description", "OpenRouter model")
                            val isFree = id.contains(":free")
                            val displayName = if (isFree) "$name [FREE]" else name

                            fetched.add(
                                GeminiModelInfo(
                                    name = id,
                                    displayName = displayName,
                                    description = desc,
                                    supportedGenerationMethods = listOf("generateContent")
                                )
                            )
                        }
                        // Ensure openrouter/free and top default models are at the top
                        val sorted = fetched.sortedByDescending { it.name.contains(":free") || it.name == "openrouter/free" }
                        val combined = (defaultOpenRouterModels + sorted).distinctBy { it.name }
                        return@withContext combined
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiApiClient", "Failed to fetch OpenRouter models: ${e.message}")
            }
            return@withContext defaultOpenRouterModels + defaultGeminiModels
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${activeKey.apiKey}"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val modelsArray = json.optJSONArray("models") ?: return@withContext defaultGeminiModels + defaultOpenRouterModels
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
                    return@withContext fetched + defaultOpenRouterModels
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiApiClient", "Failed to fetch live models: ${e.message}")
        }
        return@withContext defaultGeminiModels + defaultOpenRouterModels
    }

    companion object {
        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }
}
