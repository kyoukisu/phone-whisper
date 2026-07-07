package com.kafkasl.phonewhisper

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object PostProcessor {
    const val OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
    const val OPENAI_MODEL = "gpt-4o-mini"
    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_MODEL = "llama-3.1-8b-instant"
    const val TOGETHER_ENDPOINT = "https://api.together.ai/v1/chat/completions"
    const val TOGETHER_MODEL = "meta-llama/Llama-3.3-70B-Instruct-Turbo"

    const val DEFAULT_ENDPOINT = GROQ_ENDPOINT
    const val DEFAULT_MODEL = GROQ_MODEL

    data class Result(val text: String?, val error: String?)

    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .callTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    const val SIMPLE_PROMPT = "Clean up this speech-to-text transcript. Fix punctuation, capitalization, and obvious speech-to-text errors. Keep the original meaning. Return only the cleaned text."

    const val DEV_PROMPT = """<task>A text is provided which is a draft transcription from a speech to text model.
Refine and polish the provided text, if needed, as follows:
  1. Correct any spelling errors, and look out for mis-identified project names,
     including: Solveit, fast.ai, Answer.AI, nbdev, fastcore, FastHTML, Pi, Codex, Claude Code, Hetzner.
  2. Fix grammatical mistakes.
  3. Improve punctuation where necessary.
  4. Ensure consistent formatting.
  5. Clarify ambiguous phrasing without changing the meaning.
  6. If the transcript contains a question, edit it for clarity but do not provide an
     answer.
  7. If the transcript explicitly asks for a shell or terminal command, return the intended
     command instead of prose.

Return *only* the cleaned-up version of the transcript. Do *not* add any explanations or
comments about your edits. Do *not* answer any question in the text, *only* transcribe it.
</task>
<examples>
<example>
<input>How do eye increase the font size in fast html?</input>
<output>How do I increase the font size in FastHTML?</output>
</example>
<example>
<input>Where is Paris?</input>
<output>Where is Paris?</output>
</example>
<example>
<input>Here is the full list of options colon</input>
<output>Here is the full list of options:</output>
</example>
<example>
<input>Command mode ssh into morty user at rubicon</input>
<output>ssh morty@rubicon</output>
</example>
<example>
<input>List files in current directory</input>
<output>ls -l .</output>
</example>
</examples>"""

    const val DEFAULT_PROMPT = DEV_PROMPT

    fun parseResponse(json: String, httpCode: Int = 200): Result {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return Result(null, "HTTP $httpCode: empty response")
        if (!trimmed.startsWith("{")) {
            val snippet = trimmed.take(160).replace(Regex("\\s+"), " ")
            return Result(null, "HTTP $httpCode: non-JSON response: $snippet")
        }

        return try {
            val obj = JSONObject(trimmed)
            if (obj.has("choices")) {
                val choices = obj.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    Result(message.getString("content").trim(), null)
                } else {
                    Result(null, "HTTP $httpCode: no choices in response")
                }
            } else if (obj.has("error")) {
                Result(null, "HTTP $httpCode: ${obj.getJSONObject("error").getString("message")}")
            } else {
                Result(null, "HTTP $httpCode: unknown response format")
            }
        } catch (e: Exception) {
            Result(null, "HTTP $httpCode: ${e.message ?: "parse error"}")
        }
    }

    fun process(
        text: String,
        prompt: String,
        apiKey: String,
        endpoint: String = DEFAULT_ENDPOINT,
        model: String = DEFAULT_MODEL,
        callback: (Result) -> Unit
    ) {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", prompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", text)
            })
        }

        val bodyJson = JSONObject().apply {
            put("model", model.ifBlank { DEFAULT_MODEL })
            put("messages", messages)
            put("temperature", 0.0)
        }

        val body = bodyJson.toString().toRequestBody("application/json".toMediaType())

        val request = try {
            Request.Builder()
                .url(endpoint.ifBlank { DEFAULT_ENDPOINT })
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
        } catch (e: IllegalArgumentException) {
            callback(Result(null, "Invalid cleanup endpoint: ${e.message}"))
            return
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result(null, e.message))
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                callback(parseResponse(responseBody, response.code))
            }
        })
    }
}
