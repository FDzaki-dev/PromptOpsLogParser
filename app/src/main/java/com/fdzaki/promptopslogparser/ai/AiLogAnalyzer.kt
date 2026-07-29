package com.fdzaki.promptopslogparser.ai

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Calls the real Anthropic Messages API to run the log analysis prompt built by
 * [com.fdzaki.promptopslogparser.scanner.LogPromptBuilder].
 *
 * Uses the user's own API key (see [ApiKeyStore]) — this app makes no calls anywhere
 * unless the user explicitly taps "Analisis dengan AI" and has entered a key.
 */
object AiLogAnalyzer {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-sonnet-5"
    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val MAX_TOKENS = 1500

    private val mainHandler = Handler(Looper.getMainLooper())

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    sealed class AnalyzeResult {
        data class Success(val jsonText: String) : AnalyzeResult()
        data class Error(val message: String) : AnalyzeResult()
    }

    /**
     * @param systemPrompt the full prompt produced by LogPromptBuilder.build(...)
     * @param apiKey the user's own Anthropic API key
     * @param onResult always invoked on the main thread
     */
    fun analyze(systemPrompt: String, apiKey: String, onResult: (AnalyzeResult) -> Unit) {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", MAX_TOKENS)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Analisis log stream di atas sekarang dan kembalikan JSON sesuai skema yang diminta.")
                })
            })
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postResult(onResult, AnalyzeResult.Error("Koneksi gagal: ${e.message}"))
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val apiError = try {
                            JSONObject(raw).optJSONObject("error")?.optString("message")
                        } catch (e: Exception) {
                            null
                        }
                        postResult(
                            onResult,
                            AnalyzeResult.Error("HTTP ${resp.code}: ${apiError ?: raw.take(200)}")
                        )
                        return
                    }
                    try {
                        val json = JSONObject(raw)
                        val contentArray = json.getJSONArray("content")
                        val textBuilder = StringBuilder()
                        for (i in 0 until contentArray.length()) {
                            val block = contentArray.getJSONObject(i)
                            if (block.optString("type") == "text") {
                                textBuilder.append(block.optString("text"))
                            }
                        }
                        postResult(onResult, AnalyzeResult.Success(textBuilder.toString().trim()))
                    } catch (e: Exception) {
                        postResult(onResult, AnalyzeResult.Error("Gagal parsing respons: ${e.message}"))
                    }
                }
            }
        })
    }

    private fun postResult(onResult: (AnalyzeResult) -> Unit, result: AnalyzeResult) {
        mainHandler.post { onResult(result) }
    }
}
