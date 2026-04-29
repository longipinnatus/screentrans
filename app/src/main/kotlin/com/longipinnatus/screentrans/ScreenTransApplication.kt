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
        LogManager.logSimple(LogType.INFO, "Application", "App started")

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
                // Record the crash details
                LogManager.logException("CRASH", throwable, "Uncaught exception in thread ${thread.name}")
                Log.e("ScreenTransAI", "FATAL EXCEPTION in thread ${thread.name}", throwable)
                
                // Allow some time for LogManager to write to disk (it uses a single thread executor)
                // In a real crash, the process might be dying, but we've pushed it to the executor.
                // Thread.sleep(500) 
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
}
