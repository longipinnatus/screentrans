package com.longipinnatus.screentrans

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Process
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.AppOpsManagerCompat

object OverlayManager {
    private val TAG = OverlayManager::class.java.simpleName
    private var currentOverlay: OverlayView? = null

    enum class DismissReason {
        USER_CLICK,
        TIMER,
        NEW_OVERLAY,
        SERVICE_DESTROYED,
        UNKNOWN
    }

    fun show(context: Context, result: OcrResult, settings: AppSettings.SettingsData) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        Log.d(TAG, "show: mergedBlocks size=${result.mergedBlocks.size}")

        dismiss(DismissReason.NEW_OVERLAY)

        val overlay = OverlayView(
            context = context,
            mergedBlocks = result.mergedBlocks,
            rawBlocks = result.rawBlocks,
            settings = settings,
        )
        
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "TranslationOverlay"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        overlay.setOnClickListener {
            dismiss(DismissReason.USER_CLICK)
        }

        try {
            windowManager.addView(overlay, params)
            currentOverlay = overlay
            LogManager.logSimple(LogType.DEBUG, TAG, "Overlay shown (blocks=${result.mergedBlocks.size})")
        } catch (e: Exception) {
            LogManager.logException(TAG, e, "Failed to add overlay view")
        }
    }

    fun update(result: OcrResult, settings: AppSettings.SettingsData, streamingIncrementalLen: Int) {
        currentOverlay?.let { overlay ->
            overlay.updateData(result.mergedBlocks, result.rawBlocks)

            if (settings.autoHide && (settings.autoHideMode == AppSettings.AUTO_HIDE_MODE_DYNAMIC) && (streamingIncrementalLen > 0)) {
                overlay.updateStreamingDeadline(streamingIncrementalLen)
            }
        }
    }

    fun finish(context: Context, result: OcrResult, settings: AppSettings.SettingsData) {
        currentOverlay?.let { overlay ->
            if (settings.autoHide) {
                overlay.startAutoHideTimer()
            }

            if (settings.autoCopyToClipboard) {
                copyToClipboard(context, result.mergedBlocks, settings)
            }
        }
    }

    fun dismiss(reason: DismissReason = DismissReason.UNKNOWN) {
        TranslationEngine.cancel()
        currentOverlay?.let { overlay ->
            val windowManager = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try {
                windowManager.removeView(overlay)
                LogManager.logSimple(LogType.DEBUG, TAG, "Overlay removed: reason=$reason")
            } catch (e: Exception) {
                Log.e(TAG, "Remove overlay view failed", e)
            } finally {
                currentOverlay = null
            }
        }
    }

    private fun copyToClipboard(context: Context, blocks: List<TextBlock>, settings: AppSettings.SettingsData) {
        if (blocks.isEmpty()) return
        
        if (!checkClipboardPermission(context)) {
            val message = "Unable to write to clipboard. Please enable \"Always allow writing to clipboard\" permission."
            LogManager.logSimple(LogType.WARNING, TAG, message)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            return
        }

        val textToCopy = if (settings.ocrOnly) {
            blocks.joinToString("\n") { it.text }
        } else {
            when (settings.copyMode) {
                AppSettings.COPY_MODE_TRANSLATED -> blocks.joinToString("\n") { it.translatedText ?: "" }
                AppSettings.COPY_MODE_BOTH -> blocks.joinToString("\n") { 
                    "${it.text}\n${it.translatedText ?: ""}"
                }
                else -> blocks.joinToString("\n") { it.text }
            }
        }

        if (textToCopy.isBlank()) return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OCR Result", textToCopy)
        try {
            clipboard.setPrimaryClip(clip)
            LogManager.logSimple(LogType.DEBUG, TAG, "Copied to clipboard: ${textToCopy.take(20).replace("\n", " ")}...")
        } catch (e: Exception) {
            LogManager.logException(TAG, e, "Clipboard copy failed")
        }
    }

    private fun checkClipboardPermission(context: Context): Boolean {
        val mode = AppOpsManagerCompat.noteOpNoThrow(
            context,
            "android:write_clipboard",
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManagerCompat.MODE_ALLOWED
    }
}
