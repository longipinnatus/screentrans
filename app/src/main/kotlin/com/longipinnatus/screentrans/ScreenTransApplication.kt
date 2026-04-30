package com.longipinnatus.screentrans

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class ScreenTransApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. Initialize LogManager and TokenStatsManager
        LogManager.init(this)
        TokenStatsManager.init(this)
        LogManager.logSimple(LogType.INFO, TAG, "App started")

        // 2. Apply saved language
        val preferenceManager = PreferenceManager(this)
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            val data = preferenceManager.settingsData.first { it != null }
            data?.let { LocaleUtils.applyLanguage(it.appLanguage) }
            
            // Continue to collect changes
            preferenceManager.settingsData.collect { newData ->
                newData?.let { LocaleUtils.applyLanguage(it.appLanguage) }
            }
        }

        // 3. Setup Global Crash Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                LogManager.logException(TAG, throwable, "FATAL EXCEPTION in thread ${thread.name}")
                Log.e(TAG, "FATAL EXCEPTION in thread ${thread.name}", throwable)
            } catch (_: Exception) {
                // Fallback if logging fails
            } finally {
                // Pass to system handler (shows the "App has stopped" dialog)
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable)
                } else {
                    exitProcess(2)
                }
            }
        }
    }

    companion object {
        private val TAG = ScreenTransApplication::class.java.simpleName
    }
}
