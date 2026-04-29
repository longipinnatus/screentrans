package com.longipinnatus.screentrans

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.longipinnatus.screentrans.ui.theme.ScreenTransAITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class FloatingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "ScreenTransService"

        private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(value = false)
        val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunning
    }

    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplayCreated = false
    private var floatingView: View? = null
    
    // Lifecycle components for ComposeView in Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val isProcessing = AtomicBoolean(false)
    private var ballAlpha by mutableFloatStateOf(AppSettings.DEFAULT_FLOATING_BALL_ALPHA)
    private lateinit var preferenceManager: PreferenceManager
    private var currentSettings = AppSettings.SettingsData()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()

        preferenceManager = PreferenceManager(this)
        serviceScope.launch {
            preferenceManager.settingsData.collect { settings ->
                if (settings == null) return@collect
                currentSettings = settings
                ballAlpha = settings.floatingBallAlpha
                
                // Re-init OCR engine if model settings change
                serviceScope.launch(Dispatchers.IO) {
                    OcrEngine.init(applicationContext, settings)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                
                if (resultData != null) {
                    LogManager.logSimple(LogType.INFO, TAG, "Service starting with MediaProjection")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID, 
                            createNotification(), 
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, createNotification())
                    }
                    
                    try {
                        setupMediaProjection(resultCode, resultData)
                        showFloatingBall()
                    } catch (e: Exception) {
                        LogManager.logException(TAG, e, "Setup failed")
                        stopSelf()
                    }
                } else {
                    LogManager.logSimple(LogType.WARNING, TAG, "Service start requested but resultData is null")
                    stopSelf()
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVirtualDisplay()
    }

    private fun setupMediaProjection(resultCode: Int, resultData: Intent) {
        if (mediaProjection != null) return
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
            virtualDisplayCreated = false
            LogManager.logSimple(LogType.DEBUG, TAG, "MediaProjection initialized")
        } catch (e: Exception) {
            LogManager.logException(TAG, e, "Failed to get MediaProjection")
            return
        }
        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopMediaProjection()
                }
            }, 
            null
        )
        updateVirtualDisplay()
    }

    private fun stopMediaProjection() {
        LogManager.logSimple(LogType.INFO, TAG, "stopMediaProjection called")
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        virtualDisplayCreated = false
    }

    private fun updateVirtualDisplay() {
        val proj = mediaProjection ?: return
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        
        if ((imageReader?.width == width) && (imageReader?.height == height) && (virtualDisplay != null)) return

        try {
            if (virtualDisplay == null) {
                if (virtualDisplayCreated) return
                virtualDisplayCreated = true
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                virtualDisplay = proj.createVirtualDisplay(
                    "ScreenCapture", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface, null, null,
                )
            } else {
                val oldReader = imageReader
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                virtualDisplay?.surface = imageReader?.surface
                virtualDisplay?.resize(width, height, density)
                oldReader?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update virtual display", e)
        }
    }

    private fun setBallVisibility(visible: Boolean) {
        floatingView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showFloatingBall() {
        if (floatingView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
            title = "FloatingBall"
        }

        val composeView = ComposeView(this).apply {
            setContent {
                ScreenTransAITheme {
                    FloatingBall(
                        onDrag = { dx, dy ->
                            params.x += dx
                            params.y += dy
                            windowManager.updateViewLayout(this@apply, params)
                        },
                        onClick = {
                            if (!isProcessing.get()) {
                                if (currentSettings.regionMode) {
                                    setBallVisibility(false)
                                    RegionSelectionView(
                                        this@FloatingService,
                                        isFullWidthMode = false,
                                        onRegionSelected = { rect ->
                                            captureAndTranslate(rect)
                                        },
                                        onCancel = {
                                            setBallVisibility(true)
                                        }
                                    ).show()
                                } else {
                                    captureAndTranslate()
                                }
                            }
                        },
                    ) {
                        if (!isProcessing.get()) {
                            setBallVisibility(false)
                            RegionSelectionView(
                                this@FloatingService,
                                isFullWidthMode = true,
                                onRegionSelected = { rect ->
                                    captureAndTranslate(rect)
                                },
                                onCancel = {
                                    setBallVisibility(true)
                                }
                            ).show()
                        }
                    }
                }
            }
        }

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        floatingView = composeView
        windowManager.addView(floatingView, params)
    }

    @Composable
    fun FloatingBall(onDrag: (Int, Int) -> Unit, onClick: () -> Unit, onDoubleClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .padding(8.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = ballAlpha))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onDoubleTap = { onDoubleClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "Translate",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    private fun captureAndTranslate(region: Rect? = null) {
        isProcessing.set(true)
        serviceScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    setBallVisibility(false)
                }
                
                // Wait for the floating ball to be hidden in the UI
                kotlinx.coroutines.delay(50)

                withContext(Dispatchers.Main) {
                    updateVirtualDisplay()
                }
                
                if ((imageReader == null) || (mediaProjection == null)) {
                    val msg = "MediaProjection or ImageReader is null (reader=${imageReader!=null}, proj=${mediaProjection!=null}). Launching ProjectionProxyActivity."
                    LogManager.logSimple(LogType.INFO, TAG, msg)
                    withContext(Dispatchers.Main) {
                        setBallVisibility(true)
                        val intent = Intent(this@FloatingService, ProjectionProxyActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        
                        // Use PendingIntent to bypass Background Activity Launch (BAL) restrictions on Android 14+
                        // For Target SDK 35+, we must opt in specifically as creator and then as sender
                        
                        // 1. Options for Creator: ONLY set CreatorBackgroundActivityStartMode
                        val creatorOptions = ActivityOptions.makeBasic()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            @Suppress("DEPRECATION")
                            creatorOptions.pendingIntentCreatorBackgroundActivityStartMode =
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }

                        val pendingIntent = PendingIntent.getActivity(
                            this@FloatingService,
                            0,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                            creatorOptions.toBundle()
                        )
                        
                        // 2. Options for Sender: ONLY set BackgroundActivityStartMode
                        val senderOptions = ActivityOptions.makeBasic()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            @Suppress("DEPRECATION")
                            senderOptions.pendingIntentBackgroundActivityStartMode =
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }

                        try {
                            LogManager.logSimple(LogType.DEBUG, TAG, "Starting ProjectionProxyActivity via PendingIntent (Dual BAL opt-in)")
                            pendingIntent.send(this@FloatingService, 0, null, null, null, null, senderOptions.toBundle())
                        } catch (e: Exception) {
                            LogManager.logException(TAG, e, "Failed to launch via PendingIntent, falling back to startActivity")
                            startActivity(intent)
                        }
                    }
                    isProcessing.set(false)
                    return@launch
                }

                var bitmap = ScreenCaptureHelper.capture(imageReader)

                withContext(Dispatchers.Main) {
                    setBallVisibility(true)
                }

                if (bitmap != null) {
                    if (region != null) {
                        val cropX = maxOf(0, region.left)
                        val cropY = maxOf(0, region.top)
                        val cropWidth = minOf(region.width(), bitmap.width - cropX)
                        val cropHeight = minOf(region.height(), bitmap.height - cropY)
                        
                        if (cropWidth > 0 && cropHeight > 0) {
                            val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
                            bitmap.recycle()
                            bitmap = croppedBitmap
                        }
                    }

                    val ocrResult = OcrPostProcessor.processRawBlocks(OcrEngine.detect(bitmap, currentSettings), currentSettings)

                    if (region != null) {
                        Log.d(TAG, "captureAndTranslate: Applying region offset: ${region.left}, ${region.top}")
                        ocrResult.mergedBlocks.forEach { it.bounds.offset(region.left, region.top) }
                        ocrResult.rawBlocks.forEach { it.bounds.offset(region.left, region.top) }
                    }

                    if (ocrResult.mergedBlocks.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            // Ensure translatedText is not null before showing (placeholder)
                            ocrResult.mergedBlocks.forEach { if (it.translatedText == null) it.translatedText = it.text }
                            OverlayManager.show(this@FloatingService, ocrResult, currentSettings)
                        }

                        if (!currentSettings.ocrOnly) {
                            // Update progress (handles streaming and non-streaming translation via onUpdate)
                            TranslationEngine.translate(ocrResult.mergedBlocks, currentSettings) { streamingIncrementalLen ->
                                serviceScope.launch(Dispatchers.Main) {
                                    OverlayManager.update(ocrResult, currentSettings, streamingIncrementalLen)
                                }
                            }
                        }
                        
                        // Final update with clipboard and auto-hide trigger
                        withContext(Dispatchers.Main) {
                            OverlayManager.finish(this@FloatingService, ocrResult, currentSettings)
                        }
                    } else {
                        Log.d(TAG, "No text to show")
                    }
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                LogManager.logException(TAG, e, "Error in captureAndTranslate")
                withContext(Dispatchers.Main) {
                    setBallVisibility(true)
                }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID, "Screen Translation Service", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Translation Active")
            .setContentText("Tap the floating ball to translate")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        _isRunning.value = false
        stopMediaProjection()
        serviceScope.cancel()
        OverlayManager.dismiss(OverlayManager.DismissReason.SERVICE_DESTROYED)
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove floating view", e)
            }
        }
        OcrEngine.release()
    }
}
