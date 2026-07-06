package com.kafkasl.phonewhisper

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object TranscriberClient {
    const val OPENAI_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
    const val OPENAI_MODEL = "whisper-1"
    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val GROQ_MODEL = "whisper-large-v3-turbo"

    const val DEFAULT_ENDPOINT = GROQ_ENDPOINT
    const val DEFAULT_MODEL = GROQ_MODEL
    const val DEFAULT_LANGUAGE = "ru"

    data class Result(val text: String?, val error: String?)

    private val client = OkHttpClient()

    fun parseResponse(json: String, httpCode: Int = 200): Result {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return Result(null, "HTTP $httpCode: empty response")
        if (!trimmed.startsWith("{")) {
            val snippet = trimmed.take(160).replace(Regex("\\s+"), " ")
            return Result(null, "HTTP $httpCode: non-JSON response: $snippet")
        }

        return try {
            val obj = JSONObject(trimmed)
            when {
                obj.has("text") -> Result(obj.getString("text").trim(), null)
                obj.has("error") -> Result(null, "HTTP $httpCode: ${obj.getJSONObject("error").getString("message")}")
                else -> Result(null, "HTTP $httpCode: unknown response")
            }
        } catch (e: Exception) {
            Result(null, "HTTP $httpCode: ${e.message ?: "parse error"}")
        }
    }

    fun transcribe(
        wavData: ByteArray,
        apiKey: String,
        endpoint: String = DEFAULT_ENDPOINT,
        model: String = DEFAULT_MODEL,
        language: String = DEFAULT_LANGUAGE,
        callback: (Result) -> Unit
    ) {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model.ifBlank { DEFAULT_MODEL })
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("file", "audio.wav", wavData.toRequestBody("audio/wav".toMediaType()))

        language.trim().takeIf { it.isNotBlank() }?.let {
            builder.addFormDataPart("language", it)
        }

        val body = builder.build()

        val request = try {
            Request.Builder()
                .url(endpoint.ifBlank { DEFAULT_ENDPOINT })
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
        } catch (e: IllegalArgumentException) {
            callback(Result(null, "Invalid STT endpoint: ${e.message}"))
            return
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result(null, e.message))
            override fun onResponse(call: Call, response: Response) =
                callback(parseResponse(response.body?.string() ?: "", response.code))
        })
    }
}
