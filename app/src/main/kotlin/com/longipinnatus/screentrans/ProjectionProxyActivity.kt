package com.longipinnatus.screentrans

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A transparent activity used to request MediaProjection permission.
 * This allows the app to request screen recording without navigating away from the current screen.
 */
class ProjectionProxyActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        LogManager.log(LogType.DEBUG, TAG, listOf(
            LogEntry("Result", "Launcher callback received"),
            LogEntry("ResultCode", result.resultCode.toString()),
            LogEntry("HasData", (result.data != null).toString())
        ))
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, FloatingService::class.java).apply {
                action = FloatingService.ACTION_START
                putExtra(FloatingService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(intent)
        }
        finish()
        // No animation to make it feel instant
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        super.onCreate(savedInstanceState)
        LogManager.logSimple(LogType.DEBUG, TAG, "onCreate")

        // Allow showing over lockscreen to handle "recover from screen off" better
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val preferenceManager = PreferenceManager(this)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                LogManager.logSimple(LogType.DEBUG, TAG, "Fetching settings...")
                // Get settings to check if we should use entire screen config (Android 14+)
                val settings = withContext(Dispatchers.IO) {
                    preferenceManager.settingsData.first() ?: AppSettings.SettingsData()
                }
                
                LogManager.logSimple(LogType.DEBUG, TAG, "Preparing capture intent (entireScreen=${settings.entireScreen})")
                val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && settings.entireScreen) {
                    val config = MediaProjectionConfig.createConfigForDefaultDisplay()
                    mediaProjectionManager.createScreenCaptureIntent(config)
                } else {
                    mediaProjectionManager.createScreenCaptureIntent()
                }
                
                LogManager.logSimple(LogType.DEBUG, TAG, "Launching screenCaptureLauncher")
                screenCaptureLauncher.launch(captureIntent)
            } catch (e: Exception) {
                LogManager.logException(TAG, e, "Failed to launch screen capture intent")
                finish()
            }
        }
    }

    companion object {
        private val TAG = ProjectionProxyActivity::class.java.simpleName
    }
}
