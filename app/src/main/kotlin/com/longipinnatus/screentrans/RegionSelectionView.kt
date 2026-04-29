package com.longipinnatus.screentrans

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.toColorInt

class RegionSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val isFullWidthMode: Boolean = false,
    private val onRegionSelected: ((Rect) -> Unit)? = null,
    private val onCancel: (() -> Unit)? = null
) : View(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        color = "#80000000".toColorInt() // 50% semi-transparent black
    }

    private val locationOnScreen = IntArray(2)
    private val selectionRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isDrawing) {
            // Initial state: just draw the dimmed background
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            return
        }

        val rect = getSelectedRect()
        
        // Draw dimmed background in 4 pieces to "cut out" the center selected region
        // Top part
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, bgPaint)
        // Bottom part
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), bgPaint)
        // Left part (middle height)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, bgPaint)
        // Right part (middle height)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, bgPaint)

        // Draw the red selection border
        canvas.drawRect(rect, paint)
    }

    private fun getSelectedRect(): RectF {
        selectionRect.set(
            minOf(startX, currentX),
            minOf(startY, currentY),
            maxOf(startX, currentX),
            maxOf(startY, currentY)
        )
        return selectionRect
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Get the view's current location on screen to handle any system-level offsets
        getLocationOnScreen(locationOnScreen)
        
        // Convert raw screen touch coordinates to local view coordinates for drawing
        val localX = event.rawX - locationOnScreen[0]
        val localY = event.rawY - locationOnScreen[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isFullWidthMode) {
                    startX = 0f
                    currentX = width.toFloat()
                } else {
                    startX = localX
                    currentX = localX
                }
                startY = localY
                currentY = localY
                isDrawing = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isFullWidthMode) {
                    currentX = localX
                }
                currentY = localY
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                val rectF = getSelectedRect()
                
                // Log the selection area
                LogManager.logSimple(
                    LogType.INFO, 
                    "RegionSelectionView",
                    "Region selected: ${rectF.width().toInt()}x${rectF.height().toInt()} at (${rectF.left.toInt()}, ${rectF.top.toInt()})"
                )

                // Stop drawing and remove from window immediately to avoid being captured in the following screenshot
                isDrawing = false
                visibility = GONE
                removeFromWindow()
                
                if (rectF.width() > 10 && rectF.height() > 10) {
                    // Convert local rect back to absolute screen coordinates for cropping the screenshot
                    val screenRect = Rect(
                        (rectF.left + locationOnScreen[0]).toInt(),
                        (rectF.top + locationOnScreen[1]).toInt(),
                        (rectF.right + locationOnScreen[0]).toInt(),
                        (rectF.bottom + locationOnScreen[1]).toInt()
                    )
                    // Slightly delay the callback to ensure the system has handled the view removal
                    post {
                        onRegionSelected?.invoke(screenRect)
                    }
                } else {
                    onCancel?.invoke()
                }
            }
        }
        return true
    }

    private fun removeFromWindow() {
        val wm = context.getSystemService(WindowManager::class.java)
        try {
            wm?.removeView(this)
        } catch (e: Exception) {
            Log.e("RegionSelectionView", "Failed to remove view from window", e)
        }
    }

    fun show() {
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        // Handle notches/cutouts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        params.gravity = Gravity.TOP or Gravity.START
        wm.addView(this, params)
    }
}
