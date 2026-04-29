package com.longipinnatus.screentrans

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        val OCR_ONLY = booleanPreferencesKey("ocr_only")
        val REGION_MODE = booleanPreferencesKey("region_mode")
        val SHOW_BOXES = booleanPreferencesKey("show_boxes")
        val SHOW_RAW_BOXES = booleanPreferencesKey("show_raw_boxes")
        val MERGE_TEXT_BOXES = booleanPreferencesKey("merge_text_boxes")
        val ENTIRE_SCREEN = booleanPreferencesKey("entire_screen")
        val AUTO_HIDE = booleanPreferencesKey("auto_hide")
        val AUTO_HIDE_MODE = intPreferencesKey("auto_hide_mode")
        val DISPLAY_DURATION = longPreferencesKey("display_duration")
        val DURATION_PER_WORD = longPreferencesKey("duration_per_word")
        val OVERLAY_MASK_RATIO = floatPreferencesKey("overlay_mask_ratio")
        val FLOATING_BALL_ALPHA = floatPreferencesKey("floating_ball_alpha")
        val OVERLAY_FONT_NAME = stringPreferencesKey("overlay_font_name")
        val OVERLAY_FONT_PATH = stringPreferencesKey("overlay_font_path")
        val OVERLAY_FONT_BOLD = booleanPreferencesKey("overlay_font_bold")
        val OVERLAY_FONT_ITALIC = booleanPreferencesKey("overlay_font_italic")
        val OVERLAY_IMPORTED_FONT_NAME = stringPreferencesKey("overlay_imported_font_name")
        val OVERLAY_IMPORTED_FONT_PATH = stringPreferencesKey("overlay_imported_font_path")
        val AUTO_COPY_TO_CLIPBOARD = booleanPreferencesKey("auto_copy_to_clipboard")
        val COPY_MODE = intPreferencesKey("copy_mode")
        val PIXEL_THRESH = floatPreferencesKey("pixel_thresh")
        val BOX_THRESH = floatPreferencesKey("box_thresh")
        val UNCLIP_RATIO = floatPreferencesKey("unclip_ratio")
        val DET_MODEL_TYPE = stringPreferencesKey("det_model_type")
        val REC_MODEL_TYPE = stringPreferencesKey("rec_model_type")
        val DET_CUSTOM_MODEL_PATH = stringPreferencesKey("det_custom_model_path")
        val REC_CUSTOM_MODEL_PATH = stringPreferencesKey("rec_custom_model_path")
        val TEXT_ORIENTATION = intPreferencesKey("text_orientation")
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val TARGET_LANGUAGE = stringPreferencesKey("target")
        val BACKGROUND_INFO = stringPreferencesKey("background")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val MODEL = stringPreferencesKey("model")
        val FORCE_JSON_RESPONSE = booleanPreferencesKey("force_json_response")
        val CUSTOM_PARAMS = stringPreferencesKey("custom_params")
        val ENABLE_STREAMING = booleanPreferencesKey("enable_streaming")
        val ADAPTIVE_COLORS = booleanPreferencesKey("adaptive_colors")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val PRICE_INPUT_CACHED = doublePreferencesKey("price_input_cached")
        val PRICE_INPUT_UNCACHED = doublePreferencesKey("price_input_uncached")
        val PRICE_OUTPUT = doublePreferencesKey("price_output")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val FILTER_RULES = stringPreferencesKey("filter_rules")
    }

    val settingsData: StateFlow<AppSettings.SettingsData?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppSettings.SettingsData(
                ocrOnly = prefs[OCR_ONLY] ?: AppSettings.DEFAULT_OCR_ONLY,
                regionMode = prefs[REGION_MODE] ?: AppSettings.DEFAULT_REGION_MODE,
                showBoxes = prefs[SHOW_BOXES] ?: AppSettings.DEFAULT_SHOW_BOXES,
                showRawBoxes = prefs[SHOW_RAW_BOXES] ?: AppSettings.DEFAULT_SHOW_RAW_BOXES,
                mergeTextBoxes = prefs[MERGE_TEXT_BOXES] ?: AppSettings.DEFAULT_MERGE_TEXT_BOXES,
                entireScreen = prefs[ENTIRE_SCREEN] ?: AppSettings.DEFAULT_ENTIRE_SCREEN,
                autoHide = prefs[AUTO_HIDE] ?: AppSettings.DEFAULT_AUTO_HIDE,
                autoHideMode = prefs[AUTO_HIDE_MODE] ?: AppSettings.DEFAULT_AUTO_HIDE_MODE,
                displayDurationSec = prefs[DISPLAY_DURATION] ?: AppSettings.DEFAULT_DISPLAY_DURATION_SEC,
                durationPerWordMs = prefs[DURATION_PER_WORD] ?: AppSettings.DEFAULT_DURATION_PER_WORD_MS,
                overlayMaskRatio = prefs[OVERLAY_MASK_RATIO] ?: AppSettings.DEFAULT_OVERLAY_MASK_RATIO,
                floatingBallAlpha = prefs[FLOATING_BALL_ALPHA] ?: AppSettings.DEFAULT_FLOATING_BALL_ALPHA,
                overlayFontName = prefs[OVERLAY_FONT_NAME] ?: AppSettings.DEFAULT_OVERLAY_FONT_NAME,
                overlayFontPath = prefs[OVERLAY_FONT_PATH] ?: AppSettings.DEFAULT_OVERLAY_FONT_PATH,
                overlayFontBold = prefs[OVERLAY_FONT_BOLD] ?: AppSettings.DEFAULT_OVERLAY_FONT_BOLD,
                overlayFontItalic = prefs[OVERLAY_FONT_ITALIC] ?: AppSettings.DEFAULT_OVERLAY_FONT_ITALIC,
                overlayImportedFontName = prefs[OVERLAY_IMPORTED_FONT_NAME] ?: AppSettings.DEFAULT_OVERLAY_IMPORTED_FONT_NAME,
                overlayImportedFontPath = prefs[OVERLAY_IMPORTED_FONT_PATH] ?: AppSettings.DEFAULT_OVERLAY_IMPORTED_FONT_PATH,
                autoCopyToClipboard = prefs[AUTO_COPY_TO_CLIPBOARD] ?: AppSettings.DEFAULT_AUTO_COPY_TO_CLIPBOARD,
                copyMode = prefs[COPY_MODE] ?: AppSettings.DEFAULT_COPY_MODE,
                pixelThresh = prefs[PIXEL_THRESH] ?: AppSettings.DEFAULT_PIXEL_THRESH,
                boxThresh = prefs[BOX_THRESH] ?: AppSettings.DEFAULT_BOX_THRESH,
                unclipRatio = prefs[UNCLIP_RATIO] ?: AppSettings.DEFAULT_UNCLIP_RATIO,
                detModelType = prefs[DET_MODEL_TYPE] ?: AppSettings.DEFAULT_DET_MODEL,
                recModelType = prefs[REC_MODEL_TYPE] ?: AppSettings.DEFAULT_REC_MODEL,
                detCustomModelPath = prefs[DET_CUSTOM_MODEL_PATH] ?: AppSettings.DEFAULT_DET_CUSTOM_MODEL_PATH,
                recCustomModelPath = prefs[REC_CUSTOM_MODEL_PATH] ?: AppSettings.DEFAULT_REC_CUSTOM_MODEL_PATH,
                textOrientation = prefs[TEXT_ORIENTATION] ?: AppSettings.DEFAULT_TEXT_ORIENTATION,
                baseUrl = prefs[BASE_URL] ?: AppSettings.DEFAULT_BASE_URL,
                apiKey = prefs[API_KEY] ?: AppSettings.DEFAULT_API_KEY,
                targetLanguage = prefs[TARGET_LANGUAGE] ?: AppSettings.DEFAULT_TARGET_LANGUAGE,
                backgroundInfo = prefs[BACKGROUND_INFO] ?: AppSettings.DEFAULT_BACKGROUND_INFO,
                systemPrompt = prefs[SYSTEM_PROMPT] ?: AppSettings.DEFAULT_SYSTEM_PROMPT_TEMPLATE,
                model = prefs[MODEL] ?: AppSettings.DEFAULT_MODEL,
                forceJsonResponse = prefs[FORCE_JSON_RESPONSE] ?: AppSettings.DEFAULT_FORCE_JSON_RESPONSE,
                extraParams = prefs[CUSTOM_PARAMS] ?: AppSettings.DEFAULT_EXTRA_PARAMS,
                enableStreaming = prefs[ENABLE_STREAMING] ?: AppSettings.DEFAULT_ENABLE_STREAMING,
                adaptiveColors = prefs[ADAPTIVE_COLORS] ?: AppSettings.DEFAULT_ADAPTIVE_COLORS,
                appLanguage = prefs[APP_LANGUAGE] ?: AppSettings.DEFAULT_APP_LANGUAGE,
                priceInputCached = prefs[PRICE_INPUT_CACHED] ?: AppSettings.DEFAULT_PRICE_INPUT_CACHED,
                priceInputUncached = prefs[PRICE_INPUT_UNCACHED] ?: AppSettings.DEFAULT_PRICE_INPUT_UNCACHED,
                priceOutput = prefs[PRICE_OUTPUT] ?: AppSettings.DEFAULT_PRICE_OUTPUT,
                currencySymbol = prefs[CURRENCY_SYMBOL] ?: AppSettings.DEFAULT_CURRENCY_SYMBOL,
                filterRules = parseFilterRules(prefs[FILTER_RULES])
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )


    private fun parseFilterRules(json: String?): List<AppSettings.FilterRule> {
        if (json == null) return emptyList()
        return try {
            val type = object : TypeToken<List<AppSettings.FilterRule>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e("PreferenceManager", "Failed to parse filter rules", e)
            emptyList()
        }
    }

    suspend fun updateSettings(data: AppSettings.SettingsData) {
        context.dataStore.edit { prefs ->
            prefs[OCR_ONLY] = data.ocrOnly
            prefs[REGION_MODE] = data.regionMode
            prefs[SHOW_BOXES] = data.showBoxes
            prefs[SHOW_RAW_BOXES] = data.showRawBoxes
            prefs[MERGE_TEXT_BOXES] = data.mergeTextBoxes
            prefs[ENTIRE_SCREEN] = data.entireScreen
            prefs[AUTO_HIDE] = data.autoHide
            prefs[AUTO_HIDE_MODE] = data.autoHideMode
            prefs[DISPLAY_DURATION] = data.displayDurationSec
            prefs[DURATION_PER_WORD] = data.durationPerWordMs
            prefs[OVERLAY_MASK_RATIO] = data.overlayMaskRatio
            prefs[FLOATING_BALL_ALPHA] = data.floatingBallAlpha
            prefs[OVERLAY_FONT_NAME] = data.overlayFontName
            prefs[OVERLAY_FONT_PATH] = data.overlayFontPath
            prefs[OVERLAY_FONT_BOLD] = data.overlayFontBold
            prefs[OVERLAY_FONT_ITALIC] = data.overlayFontItalic
            prefs[OVERLAY_IMPORTED_FONT_NAME] = data.overlayImportedFontName
            prefs[OVERLAY_IMPORTED_FONT_PATH] = data.overlayImportedFontPath
            prefs[AUTO_COPY_TO_CLIPBOARD] = data.autoCopyToClipboard
            prefs[COPY_MODE] = data.copyMode
            prefs[PIXEL_THRESH] = data.pixelThresh
            prefs[BOX_THRESH] = data.boxThresh
            prefs[UNCLIP_RATIO] = data.unclipRatio
            prefs[DET_MODEL_TYPE] = data.detModelType
            prefs[REC_MODEL_TYPE] = data.recModelType
            prefs[DET_CUSTOM_MODEL_PATH] = data.detCustomModelPath
            prefs[REC_CUSTOM_MODEL_PATH] = data.recCustomModelPath
            prefs[TEXT_ORIENTATION] = data.textOrientation
            prefs[BASE_URL] = data.baseUrl
            prefs[API_KEY] = data.apiKey
            prefs[TARGET_LANGUAGE] = data.targetLanguage
            prefs[BACKGROUND_INFO] = data.backgroundInfo
            prefs[SYSTEM_PROMPT] = data.systemPrompt
            prefs[MODEL] = data.model
            prefs[FORCE_JSON_RESPONSE] = data.forceJsonResponse
            prefs[CUSTOM_PARAMS] = data.extraParams
            prefs[ENABLE_STREAMING] = data.enableStreaming
            prefs[ADAPTIVE_COLORS] = data.adaptiveColors
            prefs[APP_LANGUAGE] = data.appLanguage
            prefs[PRICE_INPUT_CACHED] = data.priceInputCached
            prefs[PRICE_INPUT_UNCACHED] = data.priceInputUncached
            prefs[PRICE_OUTPUT] = data.priceOutput
            prefs[CURRENCY_SYMBOL] = data.currencySymbol
            prefs[FILTER_RULES] = gson.toJson(data.filterRules)
        }
    }

}
