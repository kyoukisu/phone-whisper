package com.kafkasl.phonewhisper

import android.content.SharedPreferences

object CloudProfiles {
    const val PROVIDER_GROQ = "groq"
    const val PROVIDER_TOGETHER = "together"
    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_CUSTOM = "custom"

    const val STT_CUSTOM = "custom"
    const val CHAT_CUSTOM = "custom"
    const val DEFAULT_STT_PRESET = "groq_whisper_turbo"
    const val DEFAULT_CHAT_PRESET = "groq_llama_fast"

    data class Provider(
        val id: String,
        val title: String,
        val keyHint: String
    )

    data class SttPreset(
        val key: String,
        val title: String,
        val subtitle: String,
        val providerId: String,
        val endpoint: String,
        val model: String,
        val language: String = "ru",
        val retryOnEmpty: Boolean = false,
        val fallbackModel: String = ""
    )

    data class ChatPreset(
        val key: String,
        val title: String,
        val subtitle: String,
        val providerId: String,
        val endpoint: String,
        val model: String
    )

    val PROVIDERS = listOf(
        Provider(PROVIDER_GROQ, "Groq", "gsk_..."),
        Provider(PROVIDER_TOGETHER, "Together", "tgp_v1_..."),
        Provider(PROVIDER_OPENAI, "OpenAI", "sk-..."),
        Provider(PROVIDER_CUSTOM, "Custom", "API key")
    )

    val STT_PRESETS = listOf(
        SttPreset(
            key = "groq_whisper_turbo",
            title = "Groq · Whisper Turbo",
            subtitle = "fast multilingual Whisper",
            providerId = PROVIDER_GROQ,
            endpoint = TranscriberClient.GROQ_ENDPOINT,
            model = TranscriberClient.GROQ_MODEL
        ),
        SttPreset(
            key = "groq_whisper_large",
            title = "Groq · Whisper Large",
            subtitle = "more accurate multilingual Whisper",
            providerId = PROVIDER_GROQ,
            endpoint = TranscriberClient.GROQ_ENDPOINT,
            model = "whisper-large-v3"
        ),
        SttPreset(
            key = "together_parakeet",
            title = "Together · Parakeet v3",
            subtitle = "fast multilingual ASR, retries then Whisper fallback",
            providerId = PROVIDER_TOGETHER,
            endpoint = TranscriberClient.TOGETHER_ENDPOINT,
            model = TranscriberClient.TOGETHER_PARAKEET_MODEL,
            retryOnEmpty = true,
            fallbackModel = TranscriberClient.TOGETHER_WHISPER_MODEL
        ),
        SttPreset(
            key = "together_whisper",
            title = "Together · Whisper Large",
            subtitle = "stable Whisper on Together",
            providerId = PROVIDER_TOGETHER,
            endpoint = TranscriberClient.TOGETHER_ENDPOINT,
            model = TranscriberClient.TOGETHER_WHISPER_MODEL
        ),
        SttPreset(
            key = "openai_whisper",
            title = "OpenAI · Whisper",
            subtitle = "standard OpenAI whisper-1",
            providerId = PROVIDER_OPENAI,
            endpoint = TranscriberClient.OPENAI_ENDPOINT,
            model = TranscriberClient.OPENAI_MODEL
        ),
        SttPreset(
            key = STT_CUSTOM,
            title = "Custom STT",
            subtitle = "custom endpoint/model/language",
            providerId = PROVIDER_CUSTOM,
            endpoint = "",
            model = "",
            language = "ru"
        )
    )

    val CHAT_PRESETS = listOf(
        ChatPreset(
            key = "groq_llama_fast",
            title = "Groq · Llama 3.1 8B",
            subtitle = "fast cleanup",
            providerId = PROVIDER_GROQ,
            endpoint = PostProcessor.GROQ_ENDPOINT,
            model = PostProcessor.GROQ_MODEL
        ),
        ChatPreset(
            key = "groq_gpt_oss_20b",
            title = "Groq · GPT-OSS 20B",
            subtitle = "newer Groq cleanup model",
            providerId = PROVIDER_GROQ,
            endpoint = PostProcessor.GROQ_ENDPOINT,
            model = "openai/gpt-oss-20b"
        ),
        ChatPreset(
            key = "groq_llama_70b",
            title = "Groq · Llama 3.3 70B",
            subtitle = "better quality, slower/stricter limits",
            providerId = PROVIDER_GROQ,
            endpoint = PostProcessor.GROQ_ENDPOINT,
            model = "llama-3.3-70b-versatile"
        ),
        ChatPreset(
            key = "together_llama_70b",
            title = "Together · Llama 3.3 70B",
            subtitle = "tested cleanup model",
            providerId = PROVIDER_TOGETHER,
            endpoint = PostProcessor.TOGETHER_ENDPOINT,
            model = PostProcessor.TOGETHER_MODEL
        ),
        ChatPreset(
            key = "openai_gpt_4o_mini",
            title = "OpenAI · GPT-4o mini",
            subtitle = "standard OpenAI cleanup",
            providerId = PROVIDER_OPENAI,
            endpoint = PostProcessor.OPENAI_ENDPOINT,
            model = PostProcessor.OPENAI_MODEL
        ),
        ChatPreset(
            key = CHAT_CUSTOM,
            title = "Custom cleanup",
            subtitle = "custom chat endpoint/model",
            providerId = PROVIDER_CUSTOM,
            endpoint = "",
            model = ""
        )
    )

    fun provider(id: String): Provider = PROVIDERS.firstOrNull { it.id == id } ?: PROVIDERS.last()
    fun sttPreset(key: String): SttPreset = STT_PRESETS.firstOrNull { it.key == key } ?: sttPreset(DEFAULT_STT_PRESET)
    fun chatPreset(key: String): ChatPreset = CHAT_PRESETS.firstOrNull { it.key == key } ?: chatPreset(DEFAULT_CHAT_PRESET)

    fun activeSttPreset(prefs: SharedPreferences): SttPreset = sttPreset(prefs.getString("stt_preset", DEFAULT_STT_PRESET) ?: DEFAULT_STT_PRESET)
    fun activeChatPreset(prefs: SharedPreferences): ChatPreset = chatPreset(prefs.getString("chat_preset", DEFAULT_CHAT_PRESET) ?: DEFAULT_CHAT_PRESET)

    fun activeSttEndpoint(prefs: SharedPreferences): String {
        val preset = activeSttPreset(prefs)
        return if (preset.key == STT_CUSTOM) {
            prefs.getString("stt_endpoint", TranscriberClient.DEFAULT_ENDPOINT) ?: TranscriberClient.DEFAULT_ENDPOINT
        } else preset.endpoint
    }

    fun activeSttModel(prefs: SharedPreferences): String {
        val preset = activeSttPreset(prefs)
        return if (preset.key == STT_CUSTOM) {
            prefs.getString("stt_model", TranscriberClient.DEFAULT_MODEL) ?: TranscriberClient.DEFAULT_MODEL
        } else preset.model
    }

    fun activeSttLanguage(prefs: SharedPreferences): String {
        val preset = activeSttPreset(prefs)
        return if (preset.key == STT_CUSTOM) {
            prefs.getString("stt_language", TranscriberClient.DEFAULT_LANGUAGE) ?: TranscriberClient.DEFAULT_LANGUAGE
        } else preset.language
    }

    fun activeChatEndpoint(prefs: SharedPreferences): String {
        val preset = activeChatPreset(prefs)
        return if (preset.key == CHAT_CUSTOM) {
            prefs.getString("chat_endpoint", PostProcessor.DEFAULT_ENDPOINT) ?: PostProcessor.DEFAULT_ENDPOINT
        } else preset.endpoint
    }

    fun activeChatModel(prefs: SharedPreferences): String {
        val preset = activeChatPreset(prefs)
        return if (preset.key == CHAT_CUSTOM) {
            prefs.getString("chat_model", PostProcessor.DEFAULT_MODEL) ?: PostProcessor.DEFAULT_MODEL
        } else preset.model
    }

    fun apiKeyPref(providerId: String): String = "api_key_provider_$providerId"
    fun apiKey(prefs: SharedPreferences, providerId: String): String = prefs.getString(apiKeyPref(providerId), "") ?: ""
    fun activeSttApiKey(prefs: SharedPreferences): String = apiKey(prefs, activeSttPreset(prefs).providerId)
    fun activeChatApiKey(prefs: SharedPreferences): String = apiKey(prefs, activeChatPreset(prefs).providerId)

    fun keySummary(prefs: SharedPreferences, providerId: String): String {
        val key = apiKey(prefs, providerId)
        return if (key.isBlank()) "Tap to set" else "••••${key.takeLast(4)}"
    }

    fun migrateLegacy(prefs: SharedPreferences) {
        val edit = prefs.edit()
        var changed = false

        fun putStringIfBlank(key: String, value: String) {
            if (prefs.getString(key, "").isNullOrBlank()) {
                edit.putString(key, value)
                changed = true
            }
        }

        fun putString(key: String, value: String) {
            edit.putString(key, value)
            changed = true
        }

        val legacyKey = prefs.getString("api_key", "").orEmpty()
        if (legacyKey.isNotBlank()) {
            putStringIfBlank(apiKeyPref(providerForApiKey(legacyKey)), legacyKey)
        }

        if (prefs.getString("stt_preset", "").isNullOrBlank()) {
            putString("stt_preset", inferSttPreset(prefs, legacyKey))
        }

        if (prefs.getString("chat_preset", "").isNullOrBlank()) {
            putString("chat_preset", inferChatPreset(prefs, legacyKey))
        }

        if (changed) edit.apply()
    }

    private fun providerForApiKey(key: String): String = when {
        key.startsWith("gsk_") -> PROVIDER_GROQ
        key.startsWith("tgp_") -> PROVIDER_TOGETHER
        key.startsWith("sk-") -> PROVIDER_OPENAI
        else -> PROVIDER_CUSTOM
    }

    private fun inferSttPreset(prefs: SharedPreferences, legacyKey: String): String {
        val endpoint = prefs.getString("stt_endpoint", "").orEmpty()
        val model = prefs.getString("stt_model", "").orEmpty()
        val provider = providerForApiKey(legacyKey)
        return when {
            provider == PROVIDER_TOGETHER -> if (model == TranscriberClient.TOGETHER_WHISPER_MODEL) "together_whisper" else "together_parakeet"
            provider == PROVIDER_GROQ -> if (model == "whisper-large-v3") "groq_whisper_large" else "groq_whisper_turbo"
            provider == PROVIDER_OPENAI -> "openai_whisper"
            endpoint.startsWith("https://api.together.ai/") || endpoint.startsWith("https://api.together.xyz/") -> if (model == TranscriberClient.TOGETHER_WHISPER_MODEL) "together_whisper" else "together_parakeet"
            endpoint.startsWith("https://api.groq.com/") -> if (model == "whisper-large-v3") "groq_whisper_large" else "groq_whisper_turbo"
            endpoint.startsWith("https://api.openai.com/") -> "openai_whisper"
            else -> STT_CUSTOM
        }
    }

    private fun inferChatPreset(prefs: SharedPreferences, legacyKey: String): String {
        val endpoint = prefs.getString("chat_endpoint", "").orEmpty()
        val model = prefs.getString("chat_model", "").orEmpty()
        val provider = providerForApiKey(legacyKey)
        return when {
            provider == PROVIDER_TOGETHER -> "together_llama_70b"
            provider == PROVIDER_GROQ -> when (model) {
                "openai/gpt-oss-20b" -> "groq_gpt_oss_20b"
                "llama-3.3-70b-versatile" -> "groq_llama_70b"
                else -> "groq_llama_fast"
            }
            provider == PROVIDER_OPENAI -> "openai_gpt_4o_mini"
            endpoint.startsWith("https://api.together.ai/") || endpoint.startsWith("https://api.together.xyz/") -> "together_llama_70b"
            endpoint.startsWith("https://api.groq.com/") -> when (model) {
                "openai/gpt-oss-20b" -> "groq_gpt_oss_20b"
                "llama-3.3-70b-versatile" -> "groq_llama_70b"
                else -> "groq_llama_fast"
            }
            endpoint.startsWith("https://api.openai.com/") -> "openai_gpt_4o_mini"
            else -> CHAT_CUSTOM
        }
    }
}
