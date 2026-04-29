package com.longipinnatus.screentrans

import androidx.annotation.Keep

object AppSettings {
    // Default Values
    const val DEFAULT_OCR_ONLY = false
    const val DEFAULT_REGION_MODE = true
    const val DEFAULT_SHOW_BOXES = false
    const val DEFAULT_SHOW_RAW_BOXES = false
    const val DEFAULT_MERGE_TEXT_BOXES = true
    const val DEFAULT_ENTIRE_SCREEN = true
    const val DEFAULT_AUTO_HIDE = false
    const val DEFAULT_ENABLE_STREAMING = false
    
    const val AUTO_HIDE_MODE_FIXED = 0
    const val AUTO_HIDE_MODE_DYNAMIC = 1
    const val DEFAULT_AUTO_HIDE_MODE = AUTO_HIDE_MODE_FIXED
    const val DEFAULT_DISPLAY_DURATION_SEC = 5L
    const val DEFAULT_DURATION_PER_WORD_MS = 100L

    const val COPY_MODE_ORIGINAL = 0
    const val COPY_MODE_TRANSLATED = 1
    const val COPY_MODE_BOTH = 2
    const val DEFAULT_COPY_MODE = COPY_MODE_ORIGINAL
    const val DEFAULT_AUTO_COPY_TO_CLIPBOARD = false

    const val DEFAULT_OVERLAY_MASK_RATIO = 1.0f
    const val DEFAULT_FLOATING_BALL_ALPHA = 0.8f
    const val DEFAULT_ADAPTIVE_COLORS = true

    const val DEFAULT_OVERLAY_FONT_NAME = "Default"
    const val DEFAULT_OVERLAY_FONT_PATH = ""
    const val DEFAULT_OVERLAY_FONT_BOLD = false
    const val DEFAULT_OVERLAY_FONT_ITALIC = false
    const val DEFAULT_OVERLAY_IMPORTED_FONT_NAME = ""
    const val DEFAULT_OVERLAY_IMPORTED_FONT_PATH = ""

    const val DEFAULT_PIXEL_THRESH = 0.3f
    const val DEFAULT_BOX_THRESH = 0.5f
    const val DEFAULT_UNCLIP_RATIO = 1.5f

    const val MODEL_TYPE_MOBILE = "mobile"
    const val MODEL_TYPE_CUSTOM = "custom"
    const val DEFAULT_DET_MODEL = MODEL_TYPE_MOBILE
    const val DEFAULT_REC_MODEL = MODEL_TYPE_MOBILE
    const val DEFAULT_DET_CUSTOM_MODEL_PATH = ""
    const val DEFAULT_REC_CUSTOM_MODEL_PATH = ""

    const val TEXT_ORIENTATION_AUTO = 0
    const val TEXT_ORIENTATION_HORIZONTAL = 1
    const val TEXT_ORIENTATION_VERTICAL = 2
    const val DEFAULT_TEXT_ORIENTATION = TEXT_ORIENTATION_AUTO

    const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_API_KEY = ""
    const val DEFAULT_MODEL = "deepseek-v4-flash"
    const val DEFAULT_FORCE_JSON_RESPONSE = true
    const val DEFAULT_EXTRA_PARAMS = "{\"thinking\": {\"type\": \"disabled\"}}"
    const val DEFAULT_TARGET_LANGUAGE = "简体中文"
    const val DEFAULT_BACKGROUND_INFO = ""

    const val DEFAULT_PRICE_INPUT_CACHED = 0.02
    const val DEFAULT_PRICE_INPUT_UNCACHED = 1.0
    const val DEFAULT_PRICE_OUTPUT = 2.0
    const val DEFAULT_CURRENCY_SYMBOL = "¥"

    const val APP_LANGUAGE_DEFAULT = ""
    const val APP_LANGUAGE_ZH = "zh"
    const val APP_LANGUAGE_EN = "en"
    const val DEFAULT_APP_LANGUAGE = APP_LANGUAGE_DEFAULT

    val CURRENCY_SYMBOLS = listOf("¥", "$", "€")

    val COMMON_LANGUAGES = listOf(
        "简体中文", "繁體中文", "English", "日本語", "한국어", 
        "Français", "Deutsch", "Español", "Русский", "Italiano",
        "Tiếng Việt", "ไทย", "Melayu"
    )

    val DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
        |# Role
        |你是一个专业的结构化翻译引擎。
        |
        |# Task
        |将输入的 JSON 对象数组从原语言翻译为 {targetLanguage}。
        |
        |# Constraints
        |1. 结构对等：输出必须是一个纯净的 JSON 数组，且长度与输入数组严格一致。禁止包含任何前导说明、Markdown代码块、后续解释。
        |2. 显式索引：每个对象包含 `id` 和 `text`。翻译时必须保持 `id` 不变，仅翻译 `text` 内容。
        |3. 上下文一致性：片段之间存在语境关联，请根据片段前后的信息确保用语统一、语意连贯。
        |4. 禁止破坏结构：包括但不限于合并片段、改变 `id` 顺序、漏译。
        |5. 转义符号处理：翻译时必须保留原文本中的 `\n` 换行符。
        |
        |# Example
        |Input: [{"id": 0, "text": "The protocol"}, {"id": 1, "text": "is strictly enforced."}]
        |Output: [{"id": 0, "text": "该协议"}, {"id": 1, "text": "是被严格执行的。"}]
    """.trimMargin()

    @Keep
    data class FilterRule(
        val id: String = java.util.UUID.randomUUID().toString(),
        val enabled: Boolean = true,
        val minWidth: Int = 0,
        val minHeight: Int = 0,
        val regex: String = "",
        val applyToRaw: Boolean = true,
        val applyToMerged: Boolean = true
    )

    @Keep
    data class SettingsData(
        val ocrOnly: Boolean = DEFAULT_OCR_ONLY,
        val regionMode: Boolean = DEFAULT_REGION_MODE,
        val showBoxes: Boolean = DEFAULT_SHOW_BOXES,
        val showRawBoxes: Boolean = DEFAULT_SHOW_RAW_BOXES,
        val mergeTextBoxes: Boolean = DEFAULT_MERGE_TEXT_BOXES,
        val entireScreen: Boolean = DEFAULT_ENTIRE_SCREEN,
        val autoHide: Boolean = DEFAULT_AUTO_HIDE,
        val autoHideMode: Int = DEFAULT_AUTO_HIDE_MODE,
        val displayDurationSec: Long = DEFAULT_DISPLAY_DURATION_SEC,
        val durationPerWordMs: Long = DEFAULT_DURATION_PER_WORD_MS,
        val overlayMaskRatio: Float = DEFAULT_OVERLAY_MASK_RATIO,
        val floatingBallAlpha: Float = DEFAULT_FLOATING_BALL_ALPHA,
        val overlayFontName: String = DEFAULT_OVERLAY_FONT_NAME,
        val overlayFontPath: String = DEFAULT_OVERLAY_FONT_PATH,
        val overlayFontBold: Boolean = DEFAULT_OVERLAY_FONT_BOLD,
        val overlayFontItalic: Boolean = DEFAULT_OVERLAY_FONT_ITALIC,
        val overlayImportedFontName: String = DEFAULT_OVERLAY_IMPORTED_FONT_NAME,
        val overlayImportedFontPath: String = DEFAULT_OVERLAY_IMPORTED_FONT_PATH,
        val autoCopyToClipboard: Boolean = DEFAULT_AUTO_COPY_TO_CLIPBOARD,
        val copyMode: Int = DEFAULT_COPY_MODE,
        val pixelThresh: Float = DEFAULT_PIXEL_THRESH,
        val boxThresh: Float = DEFAULT_BOX_THRESH,
        val unclipRatio: Float = DEFAULT_UNCLIP_RATIO,
        val detModelType: String = DEFAULT_DET_MODEL,
        val recModelType: String = DEFAULT_REC_MODEL,
        val detCustomModelPath: String = DEFAULT_DET_CUSTOM_MODEL_PATH,
        val recCustomModelPath: String = DEFAULT_REC_CUSTOM_MODEL_PATH,
        val textOrientation: Int = DEFAULT_TEXT_ORIENTATION,
        val baseUrl: String = DEFAULT_BASE_URL,
        val apiKey: String = DEFAULT_API_KEY,
        val targetLanguage: String = DEFAULT_TARGET_LANGUAGE,
        val backgroundInfo: String = DEFAULT_BACKGROUND_INFO,
        val systemPrompt: String = DEFAULT_SYSTEM_PROMPT_TEMPLATE,
        val model: String = DEFAULT_MODEL,
        val forceJsonResponse: Boolean = DEFAULT_FORCE_JSON_RESPONSE,
        val extraParams: String = DEFAULT_EXTRA_PARAMS,
        val enableStreaming: Boolean = DEFAULT_ENABLE_STREAMING,
        val adaptiveColors: Boolean = DEFAULT_ADAPTIVE_COLORS,
        val appLanguage: String = DEFAULT_APP_LANGUAGE,
        val priceInputCached: Double = DEFAULT_PRICE_INPUT_CACHED,
        val priceInputUncached: Double = DEFAULT_PRICE_INPUT_UNCACHED,
        val priceOutput: Double = DEFAULT_PRICE_OUTPUT,
        val currencySymbol: String = DEFAULT_CURRENCY_SYMBOL,
        val filterRules: List<FilterRule> = emptyList()
    )
}
