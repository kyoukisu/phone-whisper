package com.kafkasl.phonewhisper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusSubtitle: TextView
    private lateinit var audioRowSub: TextView
    private lateinit var accRowSub: TextView
    private lateinit var sttPresetRowSub: TextView
    private lateinit var chatPresetRowSub: TextView
    private lateinit var groqKeyRowSub: TextView
    private lateinit var togetherKeyRowSub: TextView
    private lateinit var openAiKeyRowSub: TextView
    private lateinit var customKeyRowSub: TextView
    private lateinit var sttEndpointRowSub: TextView
    private lateinit var sttModelRowSub: TextView
    private lateinit var languageRowSub: TextView
    private lateinit var chatEndpointRowSub: TextView
    private lateinit var chatModelRowSub: TextView
    private lateinit var promptRowSub: TextView
    private lateinit var cleanupMinSecondsRowSub: TextView
    private lateinit var promptRow: LinearLayout
    private lateinit var modelContainer: LinearLayout
    private lateinit var promptContainer: LinearLayout

    private val modelRows = mutableMapOf<String, ModelRowViews>()
    private val promptRows = mutableMapOf<String, PromptRowViews>()

    private data class ModelRowViews(
        val radio: MaterialRadioButton,
        val progress: LinearProgressIndicator,
        val subtitle: TextView,
        val dlBtn: MaterialButton
    )

    private data class PromptRowViews(
        val radio: MaterialRadioButton,
        val subtitle: TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = vertical(0, 0)

        // Top large header (like "Connected devices")
        val header = TextView(this).apply {
            text = "Phone Whisper"
            textSize = 32f
            setPadding(dp(24), dp(64), dp(24), dp(24))
        }
        root.addView(header)

        // Status row
        val statusRow = settingsRow("Status", "Checking...")
        statusSubtitle = statusRow.findViewWithTag("subtitle")
        root.addView(statusRow)

        // --- Setup Section ---
        root.addView(sectionHeader("Setup"))
        
        val audioRow = settingsRow("Audio permission", "Checking...") {
            if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        }
        audioRowSub = audioRow.findViewWithTag("subtitle")
        root.addView(audioRow)

        val accRow = settingsRow("Accessibility service", "Checking...") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        accRowSub = accRow.findViewWithTag("subtitle")
        root.addView(accRow)

        // --- Engine Section ---
        
        val isCloud = !prefs().getBoolean("use_local", true)
        
        val cloudSwitch = MaterialSwitch(this).apply {
            isChecked = isCloud
            isClickable = false
        }
        val cloudRow = settingsRow("Use cloud transcription", "Requires Groq/OpenAI-compatible API key", cloudSwitch) {
            val newCloud = !cloudSwitch.isChecked
            prefs().edit().putBoolean("use_local", !newCloud).apply()
            cloudSwitch.isChecked = newCloud
            refresh()
        }
        root.addView(cloudRow)

        // Local Models section
        modelContainer = vertical(0)
        modelContainer.addView(sectionHeader("Local models"))
        for (m in MODEL_CATALOG) modelContainer.addView(buildModelRow(m))
        root.addView(modelContainer)

        // --- Post-Processing Section ---
        root.addView(sectionHeader("Post-Processing"))
        
        val isPostProcessing = prefs().getBoolean("use_post_processing", false)
        val postProcessSwitch = MaterialSwitch(this).apply {
            isChecked = isPostProcessing
            isClickable = false
        }
        val postProcessRow = settingsRow("Cleanup transcript", "Uses configured chat API to fix grammar and punctuation", postProcessSwitch) {
            val newVal = !postProcessSwitch.isChecked
            prefs().edit().putBoolean("use_post_processing", newVal).apply()
            postProcessSwitch.isChecked = newVal
            refresh()
        }
        root.addView(postProcessRow)

        val cleanupMinSecondsRow = settingsRow("Cleanup minimum length", cleanupMinSecondsSummary()) {
            promptNumberSetting(
                title = "Cleanup minimum length",
                prefKey = "cleanup_min_seconds",
                defaultValue = 20,
                hint = "20"
            )
        }
        cleanupMinSecondsRowSub = cleanupMinSecondsRow.findViewWithTag("subtitle")
        root.addView(cleanupMinSecondsRow)

        promptContainer = vertical(0)
        for (preset in promptPresets()) promptContainer.addView(buildPromptRow(preset))
        root.addView(promptContainer)

        promptRow = settingsRow("Edit current prompt", currentPrompt()) { promptPostProcessing() }
        promptRowSub = promptRow.findViewWithTag("subtitle")
        promptRowSub.maxLines = 2
        promptRowSub.ellipsize = android.text.TextUtils.TruncateAt.END
        root.addView(promptRow)

        // --- Cloud Provider Section ---
        root.addView(sectionHeader("Provider presets"))

        val sttPresetRow = settingsRow("STT preset", sttPresetSummary()) { promptSttPreset() }
        sttPresetRowSub = sttPresetRow.findViewWithTag("subtitle")
        root.addView(sttPresetRow)

        val chatPresetRow = settingsRow("Cleanup preset", chatPresetSummary()) { promptChatPreset() }
        chatPresetRowSub = chatPresetRow.findViewWithTag("subtitle")
        root.addView(chatPresetRow)

        root.addView(sectionHeader("Provider API keys"))

        val groqKeyRow = settingsRow("Groq API key", "Tap to set") {
            promptProviderApiKey(CloudProfiles.PROVIDER_GROQ)
        }
        groqKeyRowSub = groqKeyRow.findViewWithTag("subtitle")
        root.addView(groqKeyRow)

        val togetherKeyRow = settingsRow("Together API key", "Tap to set") {
            promptProviderApiKey(CloudProfiles.PROVIDER_TOGETHER)
        }
        togetherKeyRowSub = togetherKeyRow.findViewWithTag("subtitle")
        root.addView(togetherKeyRow)

        val openAiKeyRow = settingsRow("OpenAI API key", "Tap to set") {
            promptProviderApiKey(CloudProfiles.PROVIDER_OPENAI)
        }
        openAiKeyRowSub = openAiKeyRow.findViewWithTag("subtitle")
        root.addView(openAiKeyRow)

        val customKeyRow = settingsRow("Custom API key", "Tap to set") {
            promptProviderApiKey(CloudProfiles.PROVIDER_CUSTOM)
        }
        customKeyRowSub = customKeyRow.findViewWithTag("subtitle")
        root.addView(customKeyRow)

        root.addView(sectionHeader("Advanced"))

        val sttEndpointRow = settingsRow("STT endpoint", sttEndpoint()) {
            prefs().edit().putString("stt_preset", CloudProfiles.STT_CUSTOM).apply()
            promptStringSetting(
                title = "STT endpoint",
                prefKey = "stt_endpoint",
                defaultValue = TranscriberClient.DEFAULT_ENDPOINT,
                hint = "https://api.together.ai/v1/audio/transcriptions"
            )
        }
        sttEndpointRowSub = sttEndpointRow.findViewWithTag("subtitle")
        root.addView(sttEndpointRow)

        val sttModelRow = settingsRow("STT model", sttModel()) {
            prefs().edit().putString("stt_preset", CloudProfiles.STT_CUSTOM).apply()
            promptStringSetting(
                title = "STT model",
                prefKey = "stt_model",
                defaultValue = TranscriberClient.DEFAULT_MODEL,
                hint = "nvidia/parakeet-tdt-0.6b-v3"
            )
        }
        sttModelRowSub = sttModelRow.findViewWithTag("subtitle")
        root.addView(sttModelRow)

        val languageRow = settingsRow("Language", languageSummary()) {
            prefs().edit().putString("stt_preset", CloudProfiles.STT_CUSTOM).apply()
            promptStringSetting(
                title = "Language code",
                prefKey = "stt_language",
                defaultValue = TranscriberClient.DEFAULT_LANGUAGE,
                hint = "ru"
            )
        }
        languageRowSub = languageRow.findViewWithTag("subtitle")
        root.addView(languageRow)

        val chatEndpointRow = settingsRow("Cleanup endpoint", chatEndpoint()) {
            prefs().edit().putString("chat_preset", CloudProfiles.CHAT_CUSTOM).apply()
            promptStringSetting(
                title = "Cleanup endpoint",
                prefKey = "chat_endpoint",
                defaultValue = PostProcessor.DEFAULT_ENDPOINT,
                hint = "https://api.together.ai/v1/chat/completions"
            )
        }
        chatEndpointRowSub = chatEndpointRow.findViewWithTag("subtitle")
        root.addView(chatEndpointRow)

        val chatModelRow = settingsRow("Cleanup model", chatModel()) {
            prefs().edit().putString("chat_preset", CloudProfiles.CHAT_CUSTOM).apply()
            promptStringSetting(
                title = "Cleanup model",
                prefKey = "chat_model",
                defaultValue = PostProcessor.DEFAULT_MODEL,
                hint = "meta-llama/Llama-3.3-70B-Instruct-Turbo"
            )
        }
        chatModelRowSub = chatModelRow.findViewWithTag("subtitle")
        root.addView(chatModelRow)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(attrColor(android.R.attr.colorBackground))
            addView(root)
        })

        if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(c: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r); refresh()
    }

    // --- Model Rows ---

    private fun buildModelRow(model: Model): View {
        val radio = MaterialRadioButton(this).apply {
            isClickable = false
            buttonTintList = ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorPrimary))
        }
        val dlBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            text = "↓"
            textSize = 18f
            setTextColor(attrColor(com.google.android.material.R.attr.colorPrimary))
        }
        
        val progress = LinearProgressIndicator(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LP_MATCH, dp(4)).apply {
                topMargin = dp(8)
            }
        }

        val rightContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dlBtn)
            addView(radio)
        }

        val row = settingsRow(
            if (model.recommended) model.name else model.name,
            "${model.quality} · ${model.sizeMb} MB",
            rightContainer
        ) {
            onModelAction(model)
        }
        
        val textContainer = row.getChildAt(0) as LinearLayout
        textContainer.addView(progress)
        
        modelRows[model.archive] = ModelRowViews(
            radio, progress, textContainer.findViewWithTag("subtitle"), dlBtn
        )
        refreshCard(model)
        
        return row
    }

    private fun onModelAction(model: Model) {
        val views = modelRows[model.archive] ?: return

        if (ModelDownloader.isInstalled(this, model)) {
            selectModel(model.archive)
            return
        }

        views.dlBtn.isEnabled = false
        views.progress.visibility = View.VISIBLE
        views.progress.isIndeterminate = false
        views.subtitle.text = "Starting download..."

        ModelDownloader.download(this, model) { state ->
            runOnUiThread {
                when (state) {
                    is DownloadState.Downloading -> {
                        views.progress.progress = (state.progress * 100).toInt()
                        views.subtitle.text = "Downloading: ${(state.progress * 100).toInt()}%"
                    }
                    is DownloadState.Extracting -> {
                        views.progress.isIndeterminate = true
                        views.subtitle.text = "Extracting..."
                    }
                    is DownloadState.Done -> {
                        views.progress.visibility = View.GONE
                        selectModel(model.archive)
                        toast("${model.name} ready!")
                    }
                    is DownloadState.Error -> {
                        views.progress.visibility = View.GONE
                        views.subtitle.text = "Error: ${state.message}"
                        views.dlBtn.isEnabled = true
                    }
                }
            }
        }
    }

    private fun selectModel(archive: String) {
        prefs().edit().putString("model_name", archive).apply()
        WhisperAccessibilityService.instance?.reloadModel()
        refreshAllCards(); refresh()
    }

    private fun refreshCard(model: Model) {
        val views = modelRows[model.archive] ?: return
        val active = prefs().getString("model_name", "") == model.archive
        val installed = ModelDownloader.isInstalled(this, model)
        
        views.radio.isChecked = active
        views.radio.visibility = if (installed) View.VISIBLE else View.GONE
        views.dlBtn.visibility = if (installed) View.GONE else View.VISIBLE
        
        if (views.progress.visibility == View.GONE) {
            views.subtitle.text = "${model.quality} · ${model.sizeMb} MB"
        }
    }

    private fun refreshAllCards() = MODEL_CATALOG.forEach { refreshCard(it) }

    // --- Prompt Rows ---

    private fun buildPromptRow(preset: PromptPreset): View {
        val radio = MaterialRadioButton(this).apply {
            isClickable = false
            buttonTintList = ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorPrimary))
        }

        val row = settingsRow(preset.title, preset.subtitle, radio) {
            selectPrompt(preset.key)
        }

        promptRows[preset.key] = PromptRowViews(radio, row.findViewWithTag("subtitle"))
        refreshPromptRow(preset)
        return row
    }

    private fun selectPrompt(key: String) {
        val prompt = when (key) {
            "custom" -> customPrompt()
            else -> promptPresets().firstOrNull { it.key == key }?.prompt
        } ?: return
        prefs().edit().putString("post_processing_prompt", prompt).apply()
        refreshPromptRows(); refresh()
    }

    private fun refreshPromptRow(preset: PromptPreset) {
        val views = promptRows[preset.key] ?: return
        val current = currentPrompt()
        val builtInPrompts = promptPresets().filter { it.key != "custom" }.map { it.prompt }.toSet()
        val active = when (preset.key) {
            "custom" -> current !in builtInPrompts
            else -> current == preset.prompt
        }
        views.radio.isChecked = active
        views.subtitle.text = if (preset.key == "custom") customPromptSummary() else preset.subtitle
    }

    private fun refreshPromptRows() = promptPresets().forEach { refreshPromptRow(it) }

    // --- State Updates ---

    private fun refresh() {
        CloudProfiles.migrateLegacy(prefs())

        val audio = hasPerm(Manifest.permission.RECORD_AUDIO)
        val acc = WhisperAccessibilityService.instance != null
        val useLocal = prefs().getBoolean("use_local", true)
        val usePostProcessing = prefs().getBoolean("use_post_processing", false)
        val hasSttKey = CloudProfiles.activeSttApiKey(prefs()).isNotBlank()
        val hasChatKey = CloudProfiles.activeChatApiKey(prefs()).isNotBlank()
        val hasModel = LocalTranscriber.availableModels(this).isNotEmpty()

        audioRowSub.text = if (audio) "Granted" else "Tap to grant permission"
        accRowSub.text = if (acc) "Enabled" else "Tap to enable in settings"

        modelContainer.visibility = if (useLocal) View.VISIBLE else View.GONE
        promptContainer.visibility = if (usePostProcessing) View.VISIBLE else View.GONE
        promptRow.visibility = if (usePostProcessing) View.VISIBLE else View.GONE
        cleanupMinSecondsRowSub.text = cleanupMinSecondsSummary()

        sttPresetRowSub.text = sttPresetSummary()
        chatPresetRowSub.text = chatPresetSummary()
        groqKeyRowSub.text = CloudProfiles.keySummary(prefs(), CloudProfiles.PROVIDER_GROQ)
        togetherKeyRowSub.text = CloudProfiles.keySummary(prefs(), CloudProfiles.PROVIDER_TOGETHER)
        openAiKeyRowSub.text = CloudProfiles.keySummary(prefs(), CloudProfiles.PROVIDER_OPENAI)
        customKeyRowSub.text = CloudProfiles.keySummary(prefs(), CloudProfiles.PROVIDER_CUSTOM)
        sttEndpointRowSub.text = sttEndpoint()
        sttModelRowSub.text = sttModel()
        languageRowSub.text = languageSummary()
        chatEndpointRowSub.text = chatEndpoint()
        chatModelRowSub.text = chatModel()

        val prompt = currentPrompt()
        promptRowSub.text = prompt

        val cur = prefs().getString("model_name", "") ?: ""
        if (cur.isBlank() || !File(filesDir, "models/$cur").exists()) {
            MODEL_CATALOG.firstOrNull { ModelDownloader.isInstalled(this, it) }
                ?.let { selectModel(it.archive) }
        }

        // Ready logic
        val localReady = useLocal && hasModel
        val cloudReady = !useLocal && hasSttKey
        val postReady = !usePostProcessing || hasChatKey
        val ready = audio && acc && (localReady || cloudReady) && postReady

        statusSubtitle.text = if (ready) "Ready — tap the overlay dot to dictate" else "Setup required"
        statusSubtitle.setTextColor(if (ready) attrColor(com.google.android.material.R.attr.colorPrimary) else attrColor(android.R.attr.textColorSecondary))
        
        refreshAllCards()
        refreshPromptRows()
    }

    private fun promptProviderApiKey(providerId: String) {
        val provider = CloudProfiles.provider(providerId)
        val input = EditText(this).apply {
            hint = provider.keyHint
            setText(CloudProfiles.apiKey(prefs(), providerId))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("${provider.title} API key")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Save") { _, _ ->
                prefs().edit()
                    .putString(CloudProfiles.apiKeyPref(providerId), input.text.toString().trim())
                    .apply()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptSttPreset() {
        val items = CloudProfiles.STT_PRESETS.map { "${it.title}\n${it.subtitle}" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("STT preset")
            .setItems(items) { _, which ->
                prefs().edit().putString("stt_preset", CloudProfiles.STT_PRESETS[which].key).apply()
                refresh()
            }
            .show()
    }

    private fun promptChatPreset() {
        val items = CloudProfiles.CHAT_PRESETS.map { "${it.title}\n${it.subtitle}" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Cleanup preset")
            .setItems(items) { _, which ->
                prefs().edit().putString("chat_preset", CloudProfiles.CHAT_PRESETS[which].key).apply()
                refresh()
            }
            .show()
    }

    private fun promptNumberSetting(
        title: String,
        prefKey: String,
        defaultValue: Int,
        hint: String
    ) {
        val input = EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((prefs().getInt(prefKey, defaultValue)).toString())
            setSelectAllOnFocus(true)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim().toIntOrNull() ?: defaultValue
                prefs().edit().putInt(prefKey, value.coerceAtLeast(0)).apply()
                refresh()
            }
            .setNeutralButton("Reset") { _, _ ->
                prefs().edit().putInt(prefKey, defaultValue).apply()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptStringSetting(
        title: String,
        prefKey: String,
        defaultValue: String,
        hint: String
    ) {
        val input = EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs().getString(prefKey, defaultValue) ?: defaultValue)
            setSelectAllOnFocus(true)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                prefs().edit().putString(prefKey, value.ifBlank { defaultValue }).apply()
                refresh()
            }
            .setNeutralButton("Reset") { _, _ ->
                prefs().edit().putString(prefKey, defaultValue).apply()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptPostProcessing() {
        val input = EditText(this).apply {
            hint = "Prompt"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setText(currentPrompt())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Edit current prompt")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                val finalPrompt = if (text.isBlank()) PostProcessor.DEFAULT_PROMPT else text
                prefs().edit()
                    .putString("custom_post_processing_prompt", finalPrompt)
                    .putString("post_processing_prompt", finalPrompt)
                    .apply()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- UI Helpers ---

    private fun settingsRow(title: String, subtitle: String, widget: View? = null, onClick: (() -> Unit)? = null): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            isClickable = onClick != null
            isFocusable = onClick != null
            if (onClick != null) {
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { onClick() }
            }
        }

        val textContainer = vertical(0).apply {
            layoutParams = LinearLayout.LayoutParams(0, LP_WRAP, 1f)
        }
        
        textContainer.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(attrColor(android.R.attr.textColorPrimary))
        })
        
        textContainer.addView(TextView(this).apply {
            tag = "subtitle"
            text = subtitle
            textSize = 14f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(2), 0, 0)
        })

        row.addView(textContainer)
        if (widget != null) row.addView(widget)

        return row
    }

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(attrColor(com.google.android.material.R.attr.colorPrimary)) // Neutral Android-like blue
        setPadding(dp(24), dp(24), dp(24), dp(8))
    }

    private fun vertical(padH: Int, padV: Int = padH) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padH, padV, padH, padV)
    }

    private fun currentPrompt() = prefs().getString("post_processing_prompt", PostProcessor.DEFAULT_PROMPT) ?: PostProcessor.DEFAULT_PROMPT
    private fun customPrompt() = prefs().getString("custom_post_processing_prompt", PostProcessor.DEFAULT_PROMPT) ?: PostProcessor.DEFAULT_PROMPT
    private fun sttPreset() = CloudProfiles.activeSttPreset(prefs())
    private fun chatPreset() = CloudProfiles.activeChatPreset(prefs())
    private fun sttPresetSummary() = "${sttPreset().title} · ${CloudProfiles.provider(sttPreset().providerId).title}"
    private fun chatPresetSummary() = "${chatPreset().title} · ${CloudProfiles.provider(chatPreset().providerId).title}"
    private fun sttEndpoint() = CloudProfiles.activeSttEndpoint(prefs())
    private fun sttModel() = CloudProfiles.activeSttModel(prefs())
    private fun sttLanguage() = CloudProfiles.activeSttLanguage(prefs())
    private fun chatEndpoint() = CloudProfiles.activeChatEndpoint(prefs())
    private fun chatModel() = CloudProfiles.activeChatModel(prefs())
    private fun languageSummary() = sttLanguage().ifBlank { "Auto" }
    private fun cleanupMinSeconds() = prefs().getInt("cleanup_min_seconds", 20)
    private fun cleanupMinSecondsSummary() = "Only if recording is longer than ${cleanupMinSeconds()}s"

    private fun customPromptSummary(): String {
        val prompt = customPrompt()
        return if (prompt == PostProcessor.DEFAULT_PROMPT) "Your edited prompt"
        else prompt.replace("\n", " ")
    }

    private data class PromptPreset(val key: String, val title: String, val subtitle: String, val prompt: String)

    private fun promptPresets() = listOf(
        PromptPreset(
            key = "dictation",
            title = "Dictation cleanup",
            subtitle = "Removes fillers/repeats, keeps meaning and tone",
            prompt = PostProcessor.DICTATION_PROMPT
        ),
        PromptPreset(
            key = "light",
            title = "Light cleanup",
            subtitle = "Minimal fixes for short messages",
            prompt = PostProcessor.LIGHT_PROMPT
        ),
        PromptPreset(
            key = "dev",
            title = "Dev cleanup",
            subtitle = "Best for coding, CLI, and project names",
            prompt = PostProcessor.DEV_PROMPT
        ),
        PromptPreset(
            key = "simple",
            title = "Simple cleanup",
            subtitle = "Grammar, punctuation, and light cleanup",
            prompt = PostProcessor.SIMPLE_PROMPT
        ),
        PromptPreset(
            key = "custom",
            title = "Custom",
            subtitle = customPromptSummary(),
            prompt = customPrompt()
        )
    )

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun attrColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }
    private fun prefs() = getSharedPreferences("phonewhisper", MODE_PRIVATE)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val LP_MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val LP_WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
