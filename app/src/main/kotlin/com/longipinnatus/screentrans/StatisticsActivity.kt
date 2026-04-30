package com.longipinnatus.screentrans

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.longipinnatus.screentrans.ui.theme.ScreenTransTheme
import kotlinx.coroutines.launch

class StatisticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenTransTheme {
                StatisticsScreen { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    // Refresh stats when the screen is shown or resumed
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                TokenStatsManager.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val preferenceManager = remember(context) { PreferenceManager(context) }
    val settings by preferenceManager.settingsData.collectAsStateWithLifecycle()

    val ranges = TokenStatsManager.StatsRange.entries.toTypedArray()
    val scope = rememberCoroutineScope()

    // Default to Session (index 0) and enable paging
    val pagerState = rememberPagerState(initialPage = 0) { ranges.size }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.usage_statistics)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { TokenStatsManager.clearAll() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset_stats))
                        }
                    }
                )
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    ranges.forEachIndexed { index, range ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(
                                    text = when (range) {
                                        TokenStatsManager.StatsRange.SESSION -> stringResource(R.string.stats_range_session)
                                        TokenStatsManager.StatsRange.TODAY -> stringResource(R.string.stats_range_today)
                                        TokenStatsManager.StatsRange.THIS_MONTH -> stringResource(R.string.stats_range_month)
                                        TokenStatsManager.StatsRange.ALL_TIME -> stringResource(R.string.stats_range_all)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
                val currentStats = when (ranges[page]) {
                    TokenStatsManager.StatsRange.SESSION -> TokenStatsManager.sessionStats
                    TokenStatsManager.StatsRange.TODAY -> TokenStatsManager.todayStats
                    TokenStatsManager.StatsRange.THIS_MONTH -> TokenStatsManager.monthStats
                    TokenStatsManager.StatsRange.ALL_TIME -> TokenStatsManager.allTimeStats
                }
                
                StatsContent(stats = currentStats, settings = settings)
            }
    }
}

@Composable
fun StatsContent(stats: TokenStats, settings: AppSettings.SettingsData?) {
    val cost = if (settings != null) {
        val inputCachedCost = (stats.cacheHitTokens.toDouble() / 1_000_000.0) * settings.priceInputCached
        val inputUncachedCost = (stats.cacheMissTokens.toDouble() / 1_000_000.0) * settings.priceInputUncached
        val outputCost = (stats.completionTokens.toDouble() / 1_000_000.0) * settings.priceOutput
        inputCachedCost + inputUncachedCost + outputCost
    } else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (cost > 0) {
            StatCard(
                title = stringResource(R.string.estimated_cost),
                value = String.format(java.util.Locale.US, "%s %.4f", settings?.currencySymbol ?: "¥", cost),
                color = androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
            )
        }

        StatCard(
            title = stringResource(R.string.total_tokens),
            value = stats.totalTokens.toString(),
            color = MaterialTheme.colorScheme.primary
        )

        StatCard(
            title = stringResource(R.string.prompt_tokens),
            value = stats.promptTokens.toString(),
            color = MaterialTheme.colorScheme.tertiary,
            subItems = listOf(
                stringResource(R.string.cache_hit) to stats.cacheHitTokens.toString(),
                stringResource(R.string.cache_miss) to stats.cacheMissTokens.toString()
            )
        )

        StatCard(
            title = stringResource(R.string.completion_tokens),
            value = stats.completionTokens.toString(),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    subItems: List<Pair<String, String>> = emptyList()
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            if (subItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                subItems.forEach { (label, subValue) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodySmall)
                        Text(text = subValue, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
