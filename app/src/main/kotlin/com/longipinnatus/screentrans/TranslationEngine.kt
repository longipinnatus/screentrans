package com.longipinnatus.screentrans

import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object TranslationEngine {
    private const val TAG = "TranslationEngine"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()
    private var currentCall: Call? = null
    private val THINKING_TAGS_REGEX = Regex("<(think|thought|reasoning)>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val MARKDOWN_CODE_BLOCK_REGEX = Regex("^```(?:json)?|```$")

    fun cancel() {
        currentCall?.cancel()
        currentCall = null
    }

    fun fetchModels(baseUrl: String, apiKey: String): List<String> {
        val url = "${baseUrl.trimEnd('/')}/models"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return try {
            val body = client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $responseBody")
                responseBody
            }
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(body, mapType)
            val data = map["data"] as? List<*>
            data?.mapNotNull { item -> (item as? Map<*, *>)?.get("id")?.toString() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models", e)
            emptyList()
        }
    }

    fun translate(
        blocks: List<TextBlock>,
        settings: AppSettings.SettingsData,
        onUpdate: ((Int) -> Unit)? = null,
    ): List<TextBlock> {
        if (blocks.isEmpty()) return blocks
        if (settings.ocrOnly) {
            blocks.forEach { block -> block.translatedText = block.text }
            return blocks
        }

        if (settings.apiKey.isEmpty()) {
            blocks.forEach { block -> block.translatedText = "API Key Required" }
            LogManager.logSimple(LogType.DEBUG, TAG, "Cannot perform translation due to empty API key")
            return blocks
        }

        val systemPrompt = settings.systemPrompt.replace("{targetLanguage}", settings.targetLanguage)
        val fullSystemPrompt = buildString {
            append(systemPrompt)
            if (settings.backgroundInfo.isNotBlank()) {
                append("\n${settings.backgroundInfo}")
            }
        }

        val textsToTranslate = blocks.mapIndexed { index, block ->
            mapOf("id" to index, "text" to block.text)
        }
        val jsonInput = gson.toJson(textsToTranslate)

        val stream = (settings.enableStreaming) && (onUpdate != null)
        val requestBodyMap = mutableMapOf(
            "model" to settings.model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to fullSystemPrompt),
                mapOf("role" to "user", "content" to jsonInput)
            ),
            "stream" to stream
        )

        if (stream) {
            requestBodyMap["stream_options"] = mapOf("include_usage" to true)
        }

        if (settings.forceJsonResponse) {
            requestBodyMap["response_format"] = mapOf("type" to "json_object")
        }

        // Merge custom params
        try {
            val customParamsMap: Map<String, Any> = gson.fromJson(
                settings.extraParams,
                object : TypeToken<Map<String, Any>>() {}.type
            )
            customParamsMap.forEach { (key, value) ->
                requestBodyMap.putIfAbsent(key, value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse custom params", e)
        }

        val url = "${settings.baseUrl.trimEnd('/')}/chat/completions"
        val jsonBody = gson.toJson(requestBodyMap)

        LogManager.logRequest(
            TAG,
            url = url,
            stream = stream,
            body = jsonBody
        )

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .post(jsonBody.toRequestBody(JSON))
            .build()

        val call = client.newCall(request)
        currentCall = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    LogManager.logSimple(LogType.ERROR, TAG, "Response failed: ${response.code}, body: $errorBody")
                    throw Exception("HTTP ${response.code}")
                }
                
                if (stream) {
                    handleStreamingResponse(response, blocks, onUpdate)
                } else {
                    handleNormalResponse(response, blocks)
                    onUpdate?.invoke(0)
                }
            }
        } catch (e: Exception) {
            if (call.isCanceled()) {
                LogManager.logSimple(LogType.DEBUG, TAG, "Translation cancelled")
            } else {
                LogManager.logSimple(LogType.ERROR, TAG, "Exception (${e.javaClass.simpleName}): ${e.message}")
            }
            
            blocks.forEach { block ->
                // If it's null OR it equals the original text (placeholder), it means it's not translated
                if ((block.translatedText == null) || (block.translatedText == block.text)) {
                    block.translatedText = "[Exception]"
                }
            }
            onUpdate?.invoke(0)
        } finally {
            if (currentCall == call) {
                currentCall = null
            }
        }
        return blocks
    }

    private fun handleNormalResponse(response: Response, blocks: List<TextBlock>) {
        val body = response.body.string()
        
        LogManager.logResponse(TAG, response.code, response.message, body)

        val result = gson.fromJson(body, LLMResponse::class.java)
        result.usage?.let { TokenStatsManager.addUsage(it) }
        val rawContent = result.choices[0].message.content ?: ""
        
        val translatedTexts = parseJsonResponse(rawContent)
            ?: throw Exception("Parse Error: Invalid JSON format")

        applyTranslations(blocks, translatedTexts)
    }

    private fun handleStreamingResponse(response: Response, blocks: List<TextBlock>, onUpdate: ((Int) -> Unit)?) {
        val reader = response.body.source().inputStream().bufferedReader()
        val fullContent = StringBuilder()
        val seenIds = mutableSetOf<Int>()
        var finalUsage: Usage? = null

        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") return@forEach
                        
                        try {
                            val chunk = gson.fromJson(data, StreamResponse::class.java)
                            
                            // Capture usage stats
                            chunk.usage?.let { finalUsage = it }

                            val content = chunk.choices?.getOrNull(0)?.delta?.content ?: ""
                            fullContent.append(content)
                            
                            // Incremental regex parsing
                            tryParseIncremental(fullContent.toString(), seenIds) { id, text ->
                                if (id < blocks.size) {
                                    blocks[id].translatedText = text
                                    onUpdate?.invoke(text.length)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Maybe malformed chunks, skip: $data", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.logSimple(LogType.ERROR, TAG, "Streaming error: ${e.message}")
            throw e // Re-throw to be caught by the outer translate() catch block
        } finally {
            LogManager.logResponse(TAG, response.code, response.message, fullContent.toString())
        }

        finalUsage?.let { TokenStatsManager.addUsage(it) }
        
        // Final fallback parsing
        val finalTexts = parseJsonResponse(fullContent.toString())
        if (finalTexts != null) {
            applyTranslations(blocks, finalTexts)
            var missingLength = 0
            var hasMissed = false
            finalTexts.forEachIndexed { index, s -> 
                if (index !in seenIds) {
                    seenIds.add(index)
                    missingLength += s.length
                    hasMissed = true
                }
            }
            if (hasMissed) {
                LogManager.logSimple(LogType.WARNING, TAG, "Incremental parser missed some blocks; recovered in final fallback.")
            }
            if (missingLength > 0) onUpdate?.invoke(missingLength)
        }
    }

    private fun tryParseIncremental(content: String, seenIds: MutableSet<Int>, callback: (Int, String) -> Unit) {
        // Strip various thinking tags for incremental parsing
        val cleanContent = content.replace(THINKING_TAGS_REGEX, "")
        
        // Robust regex for {"id": 0, "text": "..."} or {"id": "0", "text": "..."}
        val pattern = """\{\s*"id"\s*:\s*"?(\d+)"?\s*,\s*"text"\s*:\s*"((?:[^"\\]|\\.)*)"\s*\}""".toRegex()
        pattern.findAll(cleanContent).forEach { match ->
            val id = match.groupValues[1].toIntOrNull()
            val text = match.groupValues[2]
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\")
            
            if (id != null && id !in seenIds) {
                seenIds.add(id)
                callback(id, text)
            }
        }
    }

    private fun applyTranslations(blocks: List<TextBlock>, translatedTexts: List<String>) {
        val finalTexts = if (translatedTexts.size > blocks.size) {
            LogManager.logSimple(LogType.WARNING, TAG, "Response size mismatch: expected ${blocks.size}, got ${translatedTexts.size}. Merging extras.")
            val head = translatedTexts.take(blocks.size - 1)
            val tail = translatedTexts.asSequence().drop(blocks.size - 1).joinToString("\n")
            head + tail
        } else {
            if (translatedTexts.size < blocks.size && translatedTexts.isNotEmpty()) {
                LogManager.logSimple(LogType.WARNING, TAG, "Response size mismatch: expected ${blocks.size}, got ${translatedTexts.size}.")
            }
            translatedTexts
        }

        blocks.forEachIndexed { index, block ->
            if (index < finalTexts.size) {
                block.translatedText = finalTexts[index]
            } else if (block.translatedText == null) {
                block.translatedText = block.text
            }
        }
    }

    private fun parseJsonResponse(content: String): List<String>? {
        // 1. Strip various thinking tags
        val strippedContent = content.replace(THINKING_TAGS_REGEX, "").trim()

        // 2. Strip Markdown code blocks
        var cleanContent = strippedContent.let {
            if (it.startsWith("```")) {
                it.replace(MARKDOWN_CODE_BLOCK_REGEX, "").trim()
            } else it
        }

        // 3. Last resort: If still not a valid JSON start, find the first '[' or '{'
        if (!cleanContent.startsWith("[") && !cleanContent.startsWith("{")) {
            val startIndex = cleanContent.indexOfAny(charArrayOf('[', '{'))
            if (startIndex != -1) {
                cleanContent = cleanContent.substring(startIndex)
            }
        }
        
        return try {
            val listType = object : TypeToken<List<Any>>() {}.type
            val rawList: List<Any> = gson.fromJson(cleanContent, listType)
            extractTexts(rawList)
        } catch (e: Exception) {
            try {
                Log.w(TAG, "Failed to parse JSON response, trying to mitigate: $cleanContent", e)
                val mapType = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(cleanContent, mapType)
                val list = map.values.find { it is List<*> } as? List<*>
                list?.let { extractTexts(it) }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to parse JSON response: $cleanContent", e2)
                null
            }
        }
    }

    private fun extractTexts(items: List<*>): List<String> {
        return items.map { item ->
            if (item is Map<*, *>) {
                item["text"]?.toString() ?: ""
            } else {
                item.toString()
            }
        }
    }

    @Keep
    data class LLMResponse(val choices: List<Choice>, val usage: Usage? = null)
    @Keep
    data class Choice(val message: Message)
    @Keep
    data class Message(val content: String?)

    @Keep
    data class StreamResponse(val choices: List<StreamChoice>?, val usage: Usage? = null)
    @Keep
    data class StreamChoice(val delta: StreamDelta)
    @Keep
    data class StreamDelta(val content: String?)

    @Keep
    data class Usage(
        @SerializedName("prompt_tokens") val promptTokens: Int,
        @SerializedName("completion_tokens") val completionTokens: Int,
        @SerializedName("total_tokens") val totalTokens: Int,
        @SerializedName("prompt_cache_hit_tokens") val promptCacheHitTokens: Int? = null,
        @SerializedName("prompt_cache_miss_tokens") val promptCacheMissTokens: Int? = null,
        @SerializedName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null
    )
    @Keep
    data class PromptTokensDetails(
        @SerializedName("cached_tokens") val cachedTokens: Int? = null
    )
}
