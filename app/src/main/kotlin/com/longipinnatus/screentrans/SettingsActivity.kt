package com.longipinnatus.screentrans

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.longipinnatus.screentrans.ui.theme.ScreenTransTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferenceManager = PreferenceManager(this)

        setContent {
            ScreenTransTheme {
                SettingsScreen(
                    onSave = { finish() },
                    onBack = { finish() }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(onSave: () -> Unit, onBack: () -> Unit) {
        val savedSettings by preferenceManager.settingsData.collectAsStateWithLifecycle()
        val currentSettings = remember { mutableStateOf(AppSettings.SettingsData()) }
        
        // Update local state when saved settings load initially
        val initialized = remember { mutableStateOf(false) }
        LaunchedEffect(savedSettings) {
            savedSettings?.let {
                if (!initialized.value) {
                    currentSettings.value = it
                    initialized.value = true
                }
            }
        }

        if (!initialized.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 64.dp))
            }
        } else {
            SettingsContent(
                currentSettings = currentSettings.value,
                savedSettings = savedSettings,
                onUpdate = { currentSettings.value = it },
                onSave = onSave,
                onBack = onBack
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsContent(
        currentSettings: AppSettings.SettingsData,
        savedSettings: AppSettings.SettingsData?,
        onUpdate: (AppSettings.SettingsData) -> Unit,
        onSave: () -> Unit,
        onBack: () -> Unit
    ) {
        val hasChanges = currentSettings != savedSettings
        val showExitDialog = remember { mutableStateOf(false) }

        BackHandler(enabled = hasChanges) {
            showExitDialog.value = true
        }

        if (showExitDialog.value) {
            AlertDialog(
                onDismissRequest = { showExitDialog.value = false },
                title = { Text(stringResource(R.string.unsaved_changes)) },
                text = { Text(stringResource(R.string.unsaved_changes_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        lifecycleScope.launch {
                            saveSettings(currentSettings)
                            onSave()
                        }
                    }) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onBack()
                    }) {
                        Text(stringResource(R.string.discard))
                    }
                }
            )
        }

        val tabs = listOf(
            stringResource(R.string.app),
            stringResource(R.string.ocr),
            stringResource(R.string.overlay),
            stringResource(R.string.llm)
        )
        val pagerState = rememberPagerState { tabs.size }
        val scope = rememberCoroutineScope()
        val showRuleEditor = remember { mutableStateOf(false) }
        val showSystemFontPicker = remember { mutableStateOf(false) }
        val showModelPicker = remember { mutableStateOf(false) }
        val showLanguagePicker = remember { mutableStateOf(false) }
        var editingRule by remember { mutableStateOf<AppSettings.FilterRule?>(null) }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.settings)) },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (hasChanges) {
                                    showExitDialog.value = true
                                } else {
                                    onBack()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    )
                    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title) }
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onUpdate(AppSettings.SettingsData())
                                Toast.makeText(this@SettingsActivity, getString(R.string.defaults_restored), Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(stringResource(R.string.reset))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                lifecycleScope.launch {
                                    saveSettings(currentSettings)
                                    onSave()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                beyondViewportPageCount = tabs.size
            ) { page ->
                when (page) {
                    0 -> AppPage(
                        currentSettings = currentSettings,
                        onUpdate = onUpdate
                    )
                    1 -> OcrPage(
                        currentSettings = currentSettings,
                        onUpdate = onUpdate,
                        onEditRule = {
                            editingRule = it
                            showRuleEditor.value = true
                        },
                        filesDir = filesDir,
                        contentResolver = contentResolver
                    )
                    2 -> OverlayPage(
                        currentSettings = currentSettings,
                        onUpdate = onUpdate,
                        onShowFontPicker = { showSystemFontPicker.value = true },
                        filesDir = filesDir,
                        contentResolver = contentResolver
                    )
                    3 -> LlmPage(
                        currentSettings = currentSettings,
                        onUpdate = onUpdate,
                        onShowModelPicker = { showModelPicker.value = true },
                        onShowLanguagePicker = { showLanguagePicker.value = true }
                    )
                }
            }
        }

        editingRule?.let { rule ->
            if (showRuleEditor.value) {
                RuleEditorDialog(
                    rule = rule,
                    onDismiss = { showRuleEditor.value = false },
                    onSave = { updatedRule ->
                        val newRules = if (currentSettings.filterRules.any { it.id == updatedRule.id }) {
                            currentSettings.filterRules.map { if (it.id == updatedRule.id) updatedRule else it }
                        } else {
                            currentSettings.filterRules + updatedRule
                        }
                        onUpdate(currentSettings.copy(filterRules = newRules))
                        showRuleEditor.value = false
                    }
                )
            }
        }

        if (showSystemFontPicker.value) {
            SystemFontPicker(
                onDismiss = { showSystemFontPicker.value = false },
                onSelectDefault = {
                    onUpdate(currentSettings.copy(overlayFontName = "Default", overlayFontPath = ""))
                    showSystemFontPicker.value = false
                },
                onSelectFont = { font ->
                    onUpdate(currentSettings.copy(overlayFontName = "System", overlayFontPath = font.path))
                    showSystemFontPicker.value = false
                }
            )
        }

        if (showModelPicker.value) {
            ModelSelectionDialog(
                baseUrl = currentSettings.baseUrl,
                apiKey = currentSettings.apiKey,
                currentModel = currentSettings.model,
                onDismiss = { showModelPicker.value = false },
                onSelect = { 
                    onUpdate(currentSettings.copy(model = it))
                    showModelPicker.value = false
                }
            )
        }

        if (showLanguagePicker.value) {
            LanguageSelectionDialog(
                currentLanguage = currentSettings.targetLanguage,
                onDismiss = { showLanguagePicker.value = false },
                onSelect = {
                    onUpdate(currentSettings.copy(targetLanguage = it))
                    showLanguagePicker.value = false
                }
            )
        }
    }

    @Composable
    fun LanguageSelectionDialog(
        currentLanguage: String,
        onDismiss: () -> Unit,
        onSelect: (String) -> Unit
    ) {
        var manualLanguage by remember { mutableStateOf(currentLanguage) }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.select_target_lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = manualLanguage,
                        onValueChange = { manualLanguage = it },
                        label = { Text(stringResource(R.string.lang_manual)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    HorizontalDivider()
                    
                    Text(stringResource(R.string.common_langs), style = MaterialTheme.typography.labelMedium)
                    
                    Box(modifier = Modifier.height(250.dp).fillMaxWidth()) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            AppSettings.COMMON_LANGUAGES.forEach { lang ->
                                TextButton(
                                    onClick = { manualLanguage = lang },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        lang, 
                                        modifier = Modifier.fillMaxWidth(),
                                        color = if (manualLanguage == lang) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSelect(manualLanguage) }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    @Composable
        fun ModelSelectionDialog(
        baseUrl: String,
        apiKey: String,
        currentModel: String,
        onDismiss: () -> Unit,
        onSelect: (String) -> Unit
    ) {
        var isLoading by remember { mutableStateOf(false) }
        var models by remember { mutableStateOf<List<String>>(emptyList()) }
        var error by remember { mutableStateOf<String?>(null) }
        var manualModel by remember { mutableStateOf(currentModel) }
        val noModelsFoundMsg = stringResource(R.string.no_models_found)

        LaunchedEffect(Unit) {
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                isLoading = true
                try {
                    val result = withContext(Dispatchers.IO) {
                        TranslationEngine.fetchModels(baseUrl, apiKey)
                    }
                    models = result
                    if (result.isEmpty()) error = noModelsFoundMsg
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    isLoading = false
                }
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.select_model)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = manualModel,
                        onValueChange = { manualModel = it },
                        label = { Text(stringResource(R.string.model_manual)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    HorizontalDivider()
                    
                    Text(stringResource(R.string.available_models), style = MaterialTheme.typography.labelMedium)
                    
                    Box(modifier = Modifier.height(250.dp).fillMaxWidth()) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                        } else {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                models.forEach { model ->
                                    TextButton(
                                        onClick = { manualModel = model },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            model, 
                                            modifier = Modifier.fillMaxWidth(),
                                            color = if (manualModel == model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSelect(manualModel) }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    @Composable
    fun SystemFontPicker(onDismiss: () -> Unit, onSelectDefault: () -> Unit, onSelectFont: (FontUtils.FontInfo) -> Unit) {
        val allFonts = remember { FontUtils.getSystemFonts() }
        var searchQuery by remember { mutableStateOf("") }
        val filteredFonts = remember(searchQuery) {
            if (searchQuery.isBlank()) allFonts
            else allFonts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true) }
        }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text(stringResource(R.string.select_font))
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_fonts)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                                }
                            }
                        }
                    )
                }
            },
            text = {
                Box(modifier = Modifier.height(400.dp)) {
                    val listState = rememberScrollState()
                    Column(modifier = Modifier.verticalScroll(listState)) {
                        // System Default Option (only show if not searching or if it matches "Default")
                        if (searchQuery.isBlank() || stringResource(R.string.language_system_default).contains(searchQuery, ignoreCase = true)) {
                            TextButton(
                                onClick = onSelectDefault,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                    Text(stringResource(R.string.language_system_default), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.font_default_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        filteredFonts.forEach { font ->
                            TextButton(
                                onClick = { onSelectFont(font) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                    Text(font.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(font.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        
                        if (filteredFonts.isEmpty() && searchQuery.isNotBlank() && !stringResource(R.string.language_system_default).contains(searchQuery, ignoreCase = true)) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_fonts_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    @Composable
    fun RuleEditorDialog(rule: AppSettings.FilterRule, onDismiss: () -> Unit, onSave: (AppSettings.FilterRule) -> Unit) {
        var minWidth by remember { mutableStateOf(if (rule.minWidth > 0) rule.minWidth.toString() else "") }
        var minHeight by remember { mutableStateOf(if (rule.minHeight > 0) rule.minHeight.toString() else "") }
        var regex by remember { mutableStateOf(rule.regex) }
        var applyToRaw by remember { mutableStateOf(rule.applyToRaw) }
        var applyToMerged by remember { mutableStateOf(rule.applyToMerged) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (rule.id.isEmpty()) stringResource(R.string.add_rule) else stringResource(R.string.edit_rule)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.filter_dialog_desc), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(value = minWidth, onValueChange = { minWidth = it }, label = { Text(stringResource(R.string.width_lt)) }, modifier = Modifier.weight(1f))
                        TextField(value = minHeight, onValueChange = { minHeight = it }, label = { Text(stringResource(R.string.height_lt)) }, modifier = Modifier.weight(1f))
                    }
                    TextField(value = regex, onValueChange = { regex = it }, label = { Text(stringResource(R.string.regex_pattern)) }, modifier = Modifier.fillMaxWidth())
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.apply_to), style = MaterialTheme.typography.labelMedium)
                    RowSwitch(stringResource(R.string.raw_boxes), applyToRaw) { applyToRaw = it }
                    RowSwitch(stringResource(R.string.merged_boxes), applyToMerged) { applyToMerged = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(rule.copy(
                        minWidth = minWidth.toIntOrNull() ?: 0,
                        minHeight = minHeight.toIntOrNull() ?: 0,
                        regex = regex,
                        applyToRaw = applyToRaw,
                        applyToMerged = applyToMerged
                    ))
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    @Composable
    fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    fun RowSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float = 0.05f, onValueChange: (Float) -> Unit) {
        val showDialog = remember { mutableStateOf(false) }
        val locale = LocalConfiguration.current.locales[0]

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog.value = true }
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = String.format(locale, "%.2f", value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showDialog.value) {
            var tempValue by remember { mutableFloatStateOf(value) }
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text(label) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = String.format(locale, "%.2f", tempValue),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Slider(
                            value = tempValue,
                            onValueChange = { newValue ->
                                tempValue = if (step > 0) (newValue / step).roundToInt() * step else newValue
                            },
                            valueRange = range,
                            steps = if (step > 0) ((range.endInclusive - range.start) / step).roundToInt() - 1 else 0
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onValueChange(tempValue)
                        showDialog.value = false
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog.value = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    private suspend fun saveSettings(data: AppSettings.SettingsData) {
        preferenceManager.updateSettings(data)
        withContext(Dispatchers.Main) {
            LocaleUtils.applyLanguage(data.appLanguage)
            Toast.makeText(this@SettingsActivity, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun AppLanguageSelectionDialog(
        currentLanguageCode: String,
        onDismiss: () -> Unit,
        onSelect: (String) -> Unit
    ) {
        val languages = remember {
            listOf(
                AppSettings.APP_LANGUAGE_DEFAULT to R.string.language_system_default,
                AppSettings.APP_LANGUAGE_ZH to R.string.language_zh,
                AppSettings.APP_LANGUAGE_EN to R.string.language_en
            )
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.app_language)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    languages.forEach { (code, labelRes) ->
                        val label = stringResource(labelRes)
                        TextButton(
                            onClick = { onSelect(code) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                label,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (currentLanguageCode == code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    @Composable
    fun AppPage(
        currentSettings: AppSettings.SettingsData,
        onUpdate: (AppSettings.SettingsData) -> Unit
    ) {
        val showAppLanguagePicker = remember { mutableStateOf(false) }
        val languages = remember {
            listOf(
                AppSettings.APP_LANGUAGE_DEFAULT to R.string.language_system_default,
                AppSettings.APP_LANGUAGE_ZH to R.string.language_zh,
                AppSettings.APP_LANGUAGE_EN to R.string.language_en
            )
        }
        val currentLangLabel = stringResource(languages.find { it.first == currentSettings.appLanguage }?.second ?: R.string.language_system_default)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.app_settings_title), style = MaterialTheme.typography.titleMedium)

            // Language Selection (Dialog Style)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.app_language), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showAppLanguagePicker.value = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currentLangLabel, overflow = TextOverflow.Ellipsis, maxLines = 1)
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (showAppLanguagePicker.value) {
                AppLanguageSelectionDialog(
                    currentLanguageCode = currentSettings.appLanguage,
                    onDismiss = { showAppLanguagePicker.value = false },
                    onSelect = {
                        onUpdate(currentSettings.copy(appLanguage = it))
                        showAppLanguagePicker.value = false
                    }
                )
            }

            RowSwitch(stringResource(R.string.ocr_only), currentSettings.ocrOnly) {
                onUpdate(currentSettings.copy(
                    ocrOnly = it,
                    copyMode = if (it) AppSettings.COPY_MODE_ORIGINAL else currentSettings.copyMode
                ))
            }
            RowSwitch(stringResource(R.string.region_mode), currentSettings.regionMode) { onUpdate(currentSettings.copy(regionMode = it)) }
            RowSwitch(stringResource(R.string.entire_screen), currentSettings.entireScreen) { onUpdate(currentSettings.copy(entireScreen = it)) }
            RowSwitch(stringResource(R.string.auto_copy), currentSettings.autoCopyToClipboard) { onUpdate(currentSettings.copy(autoCopyToClipboard = it)) }
            if (currentSettings.autoCopyToClipboard) {
                Text(stringResource(R.string.copy_content), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val options = if (currentSettings.ocrOnly) {
                        listOf(AppSettings.COPY_MODE_ORIGINAL to R.string.copy_original)
                    } else {
                        listOf(
                            AppSettings.COPY_MODE_ORIGINAL to R.string.copy_original,
                            AppSettings.COPY_MODE_TRANSLATED to R.string.copy_translation,
                            AppSettings.COPY_MODE_BOTH to R.string.copy_both
                        )
                    }
                    options.forEach { (mode, labelRes) ->
                        val label = stringResource(labelRes)
                        val selected = currentSettings.copyMode == mode
                        if (selected) {
                            Button(onClick = { onUpdate(currentSettings.copy(copyMode = mode)) }, modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                            }
                        } else {
                            OutlinedButton(onClick = { onUpdate(currentSettings.copy(copyMode = mode)) }, modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun OcrPage(
        currentSettings: AppSettings.SettingsData, 
        onUpdate: (AppSettings.SettingsData) -> Unit,
        onEditRule: (AppSettings.FilterRule) -> Unit,
        filesDir: java.io.File,
        contentResolver: android.content.ContentResolver
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.ocr_model), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.det_model), style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val options = listOf(
                    AppSettings.MODEL_TYPE_MOBILE to R.string.model_mobile,
                    AppSettings.MODEL_TYPE_CUSTOM to R.string.model_custom
                )
                options.forEach { (type, labelRes) ->
                    val selected = currentSettings.detModelType == type
                    if (selected) {
                        Button(onClick = { onUpdate(currentSettings.copy(detModelType = type)) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(labelRes))
                        }
                    } else {
                        OutlinedButton(onClick = { onUpdate(currentSettings.copy(detModelType = type)) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
            }

            if (currentSettings.detModelType == AppSettings.MODEL_TYPE_CUSTOM) {
                ModelFilePicker(
                    label = stringResource(R.string.custom_det_model),
                    currentPath = currentSettings.detCustomModelPath,
                    filesDir = filesDir,
                    contentResolver = contentResolver,
                    onUpdatePath = { onUpdate(currentSettings.copy(detCustomModelPath = it)) }
                )
                if (currentSettings.detCustomModelPath.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_custom_model),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(stringResource(R.string.rec_model), style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val options = listOf(
                    AppSettings.MODEL_TYPE_MOBILE to R.string.model_mobile,
                    AppSettings.MODEL_TYPE_CUSTOM to R.string.model_custom
                )
                options.forEach { (type, labelRes) ->
                    val selected = currentSettings.recModelType == type
                    if (selected) {
                        Button(onClick = { onUpdate(currentSettings.copy(recModelType = type)) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(labelRes))
                        }
                    } else {
                        OutlinedButton(onClick = { onUpdate(currentSettings.copy(recModelType = type)) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
            }

            if (currentSettings.recModelType == AppSettings.MODEL_TYPE_CUSTOM) {
                ModelFilePicker(
                    label = stringResource(R.string.custom_rec_model),
                    currentPath = currentSettings.recCustomModelPath,
                    filesDir = filesDir,
                    contentResolver = contentResolver,
                    onUpdatePath = { onUpdate(currentSettings.copy(recCustomModelPath = it)) }
                )
                if (currentSettings.recCustomModelPath.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_custom_model),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.ocr_filter_rules), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onEditRule(AppSettings.FilterRule()) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule))
                }
            }

            currentSettings.filterRules.forEach { rule ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (rule.enabled) 1f else 0.5f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rule.enabled, onCheckedChange = { checked ->
                                onUpdate(currentSettings.copy(filterRules = currentSettings.filterRules.map { if (it.id == rule.id) it.copy(enabled = checked) else it }))
                            })
                            Column(modifier = Modifier.weight(1f)) {
                                val conditions = mutableListOf<String>()
                                if (rule.minWidth > 0 && rule.minHeight > 0) conditions.add("Size < ${rule.minWidth}px * ${rule.minHeight}px")
                                else if (rule.minWidth > 0) conditions.add("Width < ${rule.minWidth}px")
                                else if (rule.minHeight > 0) conditions.add("Height < ${rule.minHeight}px")
                                if (rule.regex.isNotEmpty()) conditions.add("Regex: ${rule.regex}")
                                
                                Text(text = if (conditions.isEmpty()) stringResource(R.string.no_conditions) else conditions.joinToString(" AND "), style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${stringResource(R.string.apply_to)} ${listOfNotNull(if (rule.applyToRaw) stringResource(R.string.box_raw) else null, if (rule.applyToMerged) stringResource(R.string.box_merged) else null).joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onEditRule(rule) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_rule))
                            }
                            IconButton(onClick = {
                                onUpdate(currentSettings.copy(filterRules = currentSettings.filterRules.filter { it.id != rule.id }))
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_rule))
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.ocr_advanced), style = MaterialTheme.typography.titleMedium)
            RowSwitch(stringResource(R.string.merge_boxes), currentSettings.mergeTextBoxes) { onUpdate(currentSettings.copy(mergeTextBoxes = it)) }
            Text(stringResource(R.string.text_orientation), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val options = listOf(
                    AppSettings.TEXT_ORIENTATION_AUTO to R.string.orient_auto,
                    AppSettings.TEXT_ORIENTATION_HORIZONTAL to R.string.orient_horiz,
                    AppSettings.TEXT_ORIENTATION_VERTICAL to R.string.orient_vert
                )
                options.forEach { (type, labelRes) ->
                    val selected = currentSettings.textOrientation == type
                    val contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    if (selected) {
                        Button(onClick = { onUpdate(currentSettings.copy(textOrientation = type)) }, modifier = Modifier.weight(1f), contentPadding = contentPadding) {
                            Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        }
                    } else {
                        OutlinedButton(onClick = { onUpdate(currentSettings.copy(textOrientation = type)) }, modifier = Modifier.weight(1f), contentPadding = contentPadding) {
                            Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            RowSlider(stringResource(R.string.pixel_thresh), currentSettings.pixelThresh, 0.0f..1.0f) { onUpdate(currentSettings.copy(pixelThresh = it)) }
            RowSlider(stringResource(R.string.box_thresh), currentSettings.boxThresh, 0.0f..1.0f) { onUpdate(currentSettings.copy(boxThresh = it)) }
            RowSlider(stringResource(R.string.unclip_ratio), currentSettings.unclipRatio, 1.0f..3.0f) { onUpdate(currentSettings.copy(unclipRatio = it)) }
        }
    }

    @Composable
    fun OverlayPage(
        currentSettings: AppSettings.SettingsData, 
        onUpdate: (AppSettings.SettingsData) -> Unit,
        onShowFontPicker: () -> Unit,
        filesDir: java.io.File,
        contentResolver: android.content.ContentResolver
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.overlay_settings), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.overlay_font), style = MaterialTheme.typography.labelLarge)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        RowSwitch(stringResource(R.string.font_bold), currentSettings.overlayFontBold) { onUpdate(currentSettings.copy(overlayFontBold = it)) }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        RowSwitch(stringResource(R.string.font_italic), currentSettings.overlayFontItalic) { onUpdate(currentSettings.copy(overlayFontItalic = it)) }
                    }
                }

                val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        try {
                            val cursor = contentResolver.query(it, null, null, null, null)
                            val displayName = cursor?.use { c ->
                                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1 && c.moveToFirst()) c.getString(nameIndex) else null
                            } ?: "custom_font.ttf"

                            val inputStream = contentResolver.openInputStream(it)
                            val file = java.io.File(filesDir, displayName)
                            inputStream?.use { input ->
                                file.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            onUpdate(currentSettings.copy(
                                overlayFontName = "Custom",
                                overlayFontPath = file.absolutePath,
                                overlayImportedFontName = displayName,
                                overlayImportedFontPath = file.absolutePath
                            ))
                        } catch (e: Exception) {
                            Log.e("SettingsActivity", "Failed to load font", e)
                            Toast.makeText(this@SettingsActivity, "Failed to load font: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val isSystemActive = currentSettings.overlayFontName == "Default" || currentSettings.overlayFontName == "System"
                val isCustomActive = currentSettings.overlayFontName == "Custom"

                // System Font Selection Section
                if (isSystemActive) {
                    val displayName = if (currentSettings.overlayFontName == "Default") stringResource(R.string.language_system_default)
                    else java.io.File(currentSettings.overlayFontPath).name
                    Button(
                        onClick = onShowFontPicker,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FontDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${stringResource(R.string.system_fonts)}: $displayName", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    }
                } else {
                    OutlinedButton(
                        onClick = onShowFontPicker,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FontDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.system_fonts), maxLines = 1, softWrap = false)
                    }
                }

                // Imported Font Section
                if (currentSettings.overlayImportedFontPath.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isCustomActive) {
                            Button(
                                onClick = { picker.launch("font/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(currentSettings.overlayImportedFontName, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { 
                                    onUpdate(currentSettings.copy(
                                        overlayFontName = "Custom",
                                        overlayFontPath = currentSettings.overlayImportedFontPath
                                    ))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(currentSettings.overlayImportedFontName, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                            }
                        }
                        
                        IconButton(onClick = {
                            val isCurrentlyUsing = currentSettings.overlayFontPath == currentSettings.overlayImportedFontPath
                            onUpdate(currentSettings.copy(
                                overlayImportedFontName = "",
                                overlayImportedFontPath = "",
                                overlayFontName = if (isCurrentlyUsing) "Default" else currentSettings.overlayFontName,
                                overlayFontPath = if (isCurrentlyUsing) "" else currentSettings.overlayFontPath
                            ))
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_rule), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { picker.launch("font/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_font), maxLines = 1, softWrap = false)
                    }
                }
            }

            RowSwitch(stringResource(R.string.adaptive_colors), currentSettings.adaptiveColors) { onUpdate(currentSettings.copy(adaptiveColors = it)) }
            RowSwitch(stringResource(R.string.auto_hide), currentSettings.autoHide) { onUpdate(currentSettings.copy(autoHide = it)) }

            if (currentSettings.autoHide) {
                Text(stringResource(R.string.auto_hide_mode), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val modes = listOf(
                        AppSettings.AUTO_HIDE_MODE_FIXED to R.string.hide_fixed,
                        AppSettings.AUTO_HIDE_MODE_DYNAMIC to R.string.hide_dynamic
                    )
                    modes.forEach { (mode, labelRes) ->
                        val selected = currentSettings.autoHideMode == mode
                        if (selected) {
                            Button(onClick = { onUpdate(currentSettings.copy(autoHideMode = mode)) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                            }
                        } else {
                            OutlinedButton(onClick = { onUpdate(currentSettings.copy(autoHideMode = mode)) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                if (currentSettings.autoHideMode == AppSettings.AUTO_HIDE_MODE_FIXED) {
                    TextField(
                        value = currentSettings.displayDurationSec.toString(),
                        onValueChange = { onUpdate(currentSettings.copy(displayDurationSec = it.toLongOrNull() ?: 0L)) },
                        label = { Text(stringResource(R.string.display_duration)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextField(
                        value = currentSettings.durationPerWordMs.toString(),
                        onValueChange = { onUpdate(currentSettings.copy(durationPerWordMs = it.toLongOrNull() ?: 0L)) },
                        label = { Text(stringResource(R.string.duration_per_word)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            RowSlider(stringResource(R.string.overlay_opacity), currentSettings.overlayMaskRatio, 0.1f..1.0f) { onUpdate(currentSettings.copy(overlayMaskRatio = it)) }
            RowSlider(stringResource(R.string.ball_opacity), currentSettings.floatingBallAlpha, 0.1f..1.0f) { onUpdate(currentSettings.copy(floatingBallAlpha = it)) }

            RowSwitch(stringResource(R.string.show_raw_boxes), currentSettings.showRawBoxes) { onUpdate(currentSettings.copy(showRawBoxes = it)) }
            RowSwitch(stringResource(R.string.show_merged_boxes), currentSettings.showBoxes) { onUpdate(currentSettings.copy(showBoxes = it)) }
        }
    }

    @Composable
    fun LlmPage(
        currentSettings: AppSettings.SettingsData, 
        onUpdate: (AppSettings.SettingsData) -> Unit,
        onShowModelPicker: () -> Unit,
        onShowLanguagePicker: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.translation_settings), style = MaterialTheme.typography.titleMedium)
            
            TextField(value = currentSettings.baseUrl, onValueChange = { onUpdate(currentSettings.copy(baseUrl = it)) }, label = { Text(stringResource(R.string.base_url)) }, modifier = Modifier.fillMaxWidth())
            TextField(value = currentSettings.apiKey, onValueChange = { onUpdate(currentSettings.copy(apiKey = it)) }, label = { Text(stringResource(R.string.api_key)) }, modifier = Modifier.fillMaxWidth())
            
            // Model Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.model_name), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onShowModelPicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currentSettings.model, overflow = TextOverflow.Ellipsis, maxLines = 1)
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Target Language Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.target_language), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onShowLanguagePicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currentSettings.targetLanguage, overflow = TextOverflow.Ellipsis, maxLines = 1)
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            TextField(value = currentSettings.systemPrompt, onValueChange = { onUpdate(currentSettings.copy(systemPrompt = it)) }, label = { Text(stringResource(R.string.system_prompt)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            TextField(value = currentSettings.backgroundInfo, onValueChange = { onUpdate(currentSettings.copy(backgroundInfo = it)) }, label = { Text(stringResource(R.string.background_info)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            RowSwitch(stringResource(R.string.enable_streaming), currentSettings.enableStreaming) { onUpdate(currentSettings.copy(enableStreaming = it)) }
            RowSwitch(stringResource(R.string.force_json), currentSettings.forceJsonResponse) { onUpdate(currentSettings.copy(forceJsonResponse = it)) }

            TextField(
                value = currentSettings.extraParams,
                onValueChange = { onUpdate(currentSettings.copy(extraParams = it)) },
                label = { Text(stringResource(R.string.custom_params)) }, 
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            HorizontalDivider()
            Text(stringResource(R.string.pricing_title), style = MaterialTheme.typography.titleMedium)

            Text(stringResource(R.string.currency_symbol), style = MaterialTheme.typography.labelLarge)
            val presets = AppSettings.CURRENCY_SYMBOLS
            val isCustom = currentSettings.currencySymbol !in presets
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { symbol ->
                    val selected = currentSettings.currencySymbol == symbol
                    if (selected) {
                        Button(onClick = { onUpdate(currentSettings.copy(currencySymbol = symbol)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                            Text(symbol)
                        }
                    } else {
                        OutlinedButton(onClick = { onUpdate(currentSettings.copy(currencySymbol = symbol)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                            Text(symbol)
                        }
                    }
                }
                
                // Custom Option
                if (isCustom) {
                    Button(onClick = { }, modifier = Modifier.weight(1.5f), contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.currency_custom))
                    }
                } else {
                    OutlinedButton(onClick = { onUpdate(currentSettings.copy(currencySymbol = "")) }, modifier = Modifier.weight(1.5f), contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.currency_custom))
                    }
                }
            }
            
            if (isCustom) {
                TextField(
                    value = currentSettings.currencySymbol,
                    onValueChange = { onUpdate(currentSettings.copy(currencySymbol = it)) },
                    label = { Text(stringResource(R.string.currency_symbol)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            
            PriceTextField(
                label = stringResource(R.string.input_cached),
                value = currentSettings.priceInputCached,
                currencySymbol = currentSettings.currencySymbol,
                onValueChange = { onUpdate(currentSettings.copy(priceInputCached = it)) }
            )
            
            PriceTextField(
                label = stringResource(R.string.input_uncached),
                value = currentSettings.priceInputUncached,
                currencySymbol = currentSettings.currencySymbol,
                onValueChange = { onUpdate(currentSettings.copy(priceInputUncached = it)) }
            )
            
            PriceTextField(
                label = stringResource(R.string.output_price),
                value = currentSettings.priceOutput,
                currencySymbol = currentSettings.currencySymbol,
                onValueChange = { onUpdate(currentSettings.copy(priceOutput = it)) }
            )
        }
    }

    @Composable
    fun PriceTextField(
        label: String,
        value: Double,
        currencySymbol: String,
        onValueChange: (Double) -> Unit
    ) {
        var textValue by remember { mutableStateOf(if (value == 0.0) "" else value.toString()) }
        
        // Update local state if external value changes (e.g. Reset)
        LaunchedEffect(value) {
            val currentDouble = textValue.toDoubleOrNull() ?: 0.0
            if (currentDouble != value) {
                textValue = if (value == 0.0) "" else value.toString()
            }
        }

        TextField(
            value = textValue,
            onValueChange = { newValue ->
                // Allow numbers, one decimal point, and empty string
                if (newValue.isEmpty() || newValue.matches(Regex("""^\d*\.?\d*$"""))) {
                    textValue = newValue
                    onValueChange(newValue.toDoubleOrNull() ?: 0.0)
                }
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("$currencySymbol ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }

    @Composable
    fun ModelFilePicker(
        label: String,
        currentPath: String,
        filesDir: java.io.File,
        contentResolver: android.content.ContentResolver,
        onUpdatePath: (String) -> Unit
    ) {
        val picker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    val cursor = contentResolver.query(it, null, null, null, null)
                    val displayName = cursor?.use { c ->
                        val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && c.moveToFirst()) c.getString(nameIndex) else null
                    } ?: "model.onnx"
                    
                    val modelsDir = java.io.File(filesDir, "models")
                    if (!modelsDir.exists()) modelsDir.mkdirs()
                    
                    val inputStream = contentResolver.openInputStream(it)
                    val file = java.io.File(modelsDir, displayName)
                    inputStream?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    onUpdatePath(file.absolutePath)
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "Failed to load model", e)
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            if (currentPath.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch("*/*") }, modifier = Modifier.weight(1f)) {
                        Text(java.io.File(currentPath).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onUpdatePath("") }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                OutlinedButton(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_onnx))
                }
            }
        }
    }
}
