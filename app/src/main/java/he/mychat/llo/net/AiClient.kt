package he.mychat.llo.net

import he.mychat.llo.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatResult(
    val content: String,
    val rawRequest: String,
    val rawResponse: String
)

object AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun listModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/') + "/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}  $text")
                val obj = JSONObject(text)
                val arr = obj.optJSONArray("data") ?: JSONArray()
                val result = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isNotBlank()) result.add(id)
                }
                result.sorted()
            }
        }

    suspend fun testModel(
        baseUrl: String,
        apiKey: String,
        model: String,
        enableThinking: Boolean
    ): Pair<Long, String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val result = chat(
            baseUrl, apiKey, model, enableThinking,
            listOf(ChatMessage("user", "你好"))
        )
        val cost = System.currentTimeMillis() - start
        cost to result.content
    }

    private fun buildRequestBody(
        model: String,
        messages: List<ChatMessage>,
        enableThinking: Boolean,
        stream: Boolean
    ): String {
        val arr = JSONArray()
        messages.forEach {
            arr.put(JSONObject().apply {
                put("role", it.role)
                put("content", it.content)
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", arr)
            put("temperature", 0.9)
            if (stream) put("stream", true)
            if (enableThinking) {
                put("enable_thinking", true)
            } else {
                put("enable_thinking", false)
                put("reasoning_effort", "none")
            }
        }
        return body.toString()
    }

    /** 非流式（调试模式使用） */
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        enableThinking: Boolean,
        messages: List<ChatMessage>
    ): ChatResult = withContext(Dispatchers.IO) {

        val bodyStr = buildRequestBody(model, messages, enableThinking, stream = false)
        val pretty = try {
            JSONObject(bodyStr).toString(2)
        } catch (_: Exception) {
            bodyStr
        }

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(bodyStr.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}  $text")
            val obj = JSONObject(text)
            val content = obj.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            ChatResult(content, pretty, text)
        }
    }

    /**
     * 流式聊天，返回 Flow<String>。
     * 每收到一个 SSE chunk 就 emit 一次 delta 文本。
     * 在 flowOn(Dispatchers.IO) 上读取，collect 端在主线程处理。
     */
    fun chatStreamFlow(
        baseUrl: String,
        apiKey: String,
        model: String,
        enableThinking: Boolean,
        messages: List<ChatMessage>
    ): Flow<String> = flow {

        val bodyStr = buildRequestBody(model, messages, enableThinking, stream = true)
        val url = baseUrl.trimEnd('/') + "/chat/completions"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "text/event-stream")
            .post(bodyStr.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errText = resp.body?.string().orEmpty()
                throw IOException("HTTP ${resp.code} $errText")
            }
            val source = resp.body?.source() ?: throw IOException("no body")

            // 逐行读取 SSE。readUtf8Line 会在遇到 \n 时立即返回，
            // 保证每收到一个完整 SSE 行就 emit 一次。
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue

                val data = line.substring(5).trim()
                if (data == "[DONE]") break

                val obj = try {
                    JSONObject(data)
                } catch (_: Exception) {
                    continue
                }

                val delta = obj.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content", "")
                    .orEmpty()

                if (delta.isNotEmpty()) {
                    emit(delta)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}