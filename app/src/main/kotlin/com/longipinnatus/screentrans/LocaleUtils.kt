package com.longipinnatus.screentrans

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleUtils {
    fun applyLanguage(languageCode: String) {
        val appLocale: LocaleListCompat = if (languageCode.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        
        // Only apply if it's different from current
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != appLocale.toLanguageTags()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
    }
}
