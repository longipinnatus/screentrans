package com.longipinnatus.screentrans

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

@Keep
data class TokenStats(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    val cacheHitTokens: Long = 0,
    val cacheMissTokens: Long = 0,
) {
    operator fun plus(new: TokenStats) = TokenStats(
        promptTokens + new.promptTokens,
        completionTokens + new.completionTokens,
        totalTokens + new.totalTokens,
        cacheHitTokens + new.cacheHitTokens,
        cacheMissTokens + new.cacheMissTokens
    )
}

object TokenStatsManager {
    enum class StatsRange { SESSION, TODAY, THIS_MONTH, ALL_TIME }

    private var prefs: SharedPreferences? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    var sessionStats by mutableStateOf(TokenStats())
        private set
    var todayStats by mutableStateOf(TokenStats())
        private set
    var monthStats by mutableStateOf(TokenStats())
        private set
    var allTimeStats by mutableStateOf(TokenStats())
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("token_stats", Context.MODE_PRIVATE)
        refresh()
    }

    @Synchronized
    fun refresh() {
        val p = prefs ?: return
        
        allTimeStats = loadRangeStats("all")

        val now = Date()
        val currentDate = dateFormat.format(now)
        if (p.getString("today_date", "") == currentDate) {
            todayStats = loadRangeStats("today")
        } else {
            todayStats = TokenStats()
            p.edit { putString("today_date", currentDate) }
            saveRangeStats("today", todayStats)
        }

        val currentMonth = monthFormat.format(now)
        if (p.getString("month_date", "") == currentMonth) {
            monthStats = loadRangeStats("month")
        } else {
            monthStats = TokenStats()
            p.edit { putString("month_date", currentMonth) }
            saveRangeStats("month", monthStats)
        }
    }

    private fun loadRangeStats(prefix: String): TokenStats {
        val p = prefs ?: return TokenStats()
        return TokenStats(
            promptTokens = p.getLong("${prefix}_prompt", 0),
            completionTokens = p.getLong("${prefix}_completion", 0),
            totalTokens = p.getLong("${prefix}_total", 0),
            cacheHitTokens = p.getLong("${prefix}_cache_hit", 0),
            cacheMissTokens = p.getLong("${prefix}_cache_miss", 0)
        )
    }

    @Synchronized
    fun addUsage(usage: TranslationEngine.Usage) {
        val hit = (usage.promptCacheHitTokens 
            ?: usage.promptTokensDetails?.cachedTokens 
            ?: 0).toLong()
        
        val delta = TokenStats(
            promptTokens = usage.promptTokens.toLong(),
            completionTokens = usage.completionTokens.toLong(),
            totalTokens = usage.totalTokens.toLong(),
            cacheHitTokens = hit,
            cacheMissTokens = (usage.promptCacheMissTokens ?: (usage.promptTokens - hit.toInt())).toLong()
        )

        sessionStats += delta

        val p = prefs ?: return
        val now = Date()
        
        val currentDate = dateFormat.format(now)
        if (p.getString("today_date", "") != currentDate) {
            todayStats = TokenStats()
            p.edit { putString("today_date", currentDate) }
        }

        val currentMonth = monthFormat.format(now)
        if (p.getString("month_date", "") != currentMonth) {
            monthStats = TokenStats()
            p.edit { putString("month_date", currentMonth) }
        }

        todayStats += delta
        saveRangeStats("today", todayStats)

        monthStats += delta
        saveRangeStats("month", monthStats)

        allTimeStats += delta
        saveRangeStats("all", allTimeStats)
    }

    private fun saveRangeStats(prefix: String, stats: TokenStats) {
        prefs?.edit {
            putLong("${prefix}_prompt", stats.promptTokens)
            putLong("${prefix}_completion", stats.completionTokens)
            putLong("${prefix}_total", stats.totalTokens)
            putLong("${prefix}_cache_hit", stats.cacheHitTokens)
            putLong("${prefix}_cache_miss", stats.cacheMissTokens)
        }
    }

    @Synchronized
    fun clearAll() {
        sessionStats = TokenStats()
        todayStats = TokenStats()
        saveRangeStats("today", todayStats)
        monthStats = TokenStats()
        saveRangeStats("month", monthStats)
        allTimeStats = TokenStats()
        saveRangeStats("all", allTimeStats)
    }
}
