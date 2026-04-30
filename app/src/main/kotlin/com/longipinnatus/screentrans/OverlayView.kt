package com.longipinnatus.screentrans

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.graphics.withTranslation
import kotlin.math.max

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private var mergedBlocks: List<TextBlock> = emptyList(),
    private var rawBlocks: List<TextBlock> = emptyList(),
    private val settings: AppSettings.SettingsData = AppSettings.SettingsData(),
) : View(context, attrs, defStyleAttr) {

    private val locationOnScreen = IntArray(2)
    private val debugRect = RectF()
    private var autoHideRunnable: Runnable? = null
    private var streamingDeadline: Long = 0L

    private val textPaint = TextPaint().apply {
        color = Color.WHITE
        isAntiAlias = true
        
        val baseTypeface = if (settings.overlayFontPath.isNotEmpty()) {
            try {
                Typeface.createFromFile(settings.overlayFontPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load custom font from ${settings.overlayFontPath}", e)
                Typeface.DEFAULT
            }
        } else {
            Typeface.DEFAULT
        }
        
        val style = when {
            settings.overlayFontBold && settings.overlayFontItalic -> Typeface.BOLD_ITALIC
            settings.overlayFontBold -> Typeface.BOLD
            settings.overlayFontItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        
        typeface = Typeface.create(baseTypeface, style)
    }

    private val bgPaint = Paint().apply {
        val alpha = (settings.overlayMaskRatio * 255).toInt()
        color = Color.argb(alpha, 0, 0, 0)
    }

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val rawBoxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun updateData(newMergedBlocks: List<TextBlock>, newRawBlocks: List<TextBlock>) {
        this.mergedBlocks = newMergedBlocks.toList()
        this.rawBlocks = newRawBlocks.toList()
        invalidate()
    }

    fun startAutoHideTimer() {
        if (!settings.autoHide) return

        // Cancel/reset old tasks (defensive, ensuring no double scheduling)
        cancelPendingHide()

        val delay = calculateDelay()
        scheduleHideTask(delay)
    }

    fun updateStreamingDeadline(incrementalTextLength: Int) {
        if (incrementalTextLength <= 0) return
        
        val currentTime = System.currentTimeMillis()
        streamingDeadline = max(streamingDeadline, currentTime) + (incrementalTextLength.toLong() * settings.durationPerWordMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Get the current view's absolute position offset on the screen
        getLocationOnScreen(locationOnScreen)
        
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        if (mergedBlocks.isEmpty()) {
            Log.d(TAG, "onDraw: mergedBlocks is empty")
        }

        mergedBlocks.forEach { block ->
            val text = block.translatedText
            if (text == null) {
                Log.d(TAG, "onDraw: block text is null, bounds=${block.bounds}")
                return@forEach
            }
            val bounds = block.bounds

            // Check if original coordinates are out of screen bounds
            if (bounds.left < 0 || bounds.top < 0 || bounds.right > screenWidth || bounds.bottom > screenHeight) {
                Log.e(TAG, "ERROR: Block bounds out of screen! bounds=$bounds, screen=${screenWidth}x$screenHeight")
            }

            // Convert original screen coordinates to local coordinates relative to the current View
            val localLeft = bounds.left.toFloat() - locationOnScreen[0]
            val localTop = bounds.top.toFloat() - locationOnScreen[1]

            if (block.isVertical) {
                drawVerticalBlock(canvas, block, text, localLeft, localTop)
            } else {
                drawHorizontalBlock(canvas, block, text, localLeft, localTop)
            }

            // Draw detection boxes in debug mode
            if (settings.showBoxes) {
                debugRect.set(
                    bounds.left.toFloat() - locationOnScreen[0],
                    bounds.top.toFloat() - locationOnScreen[1],
                    bounds.right.toFloat() - locationOnScreen[0],
                    bounds.bottom.toFloat() - locationOnScreen[1]
                )
                canvas.drawRect(debugRect, boxPaint)
            }
        }

        // Draw unmerged detection boxes in debug mode
        if (settings.showRawBoxes) {
            rawBlocks.forEach { block ->

                debugRect.set(
                    block.bounds.left.toFloat() - locationOnScreen[0],
                    block.bounds.top.toFloat() - locationOnScreen[1],
                    block.bounds.right.toFloat() - locationOnScreen[0],
                    block.bounds.bottom.toFloat() - locationOnScreen[1]
                )
                canvas.drawRect(debugRect, rawBoxPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPendingHide()
    }

    private fun drawHorizontalBlock(canvas: Canvas, block: TextBlock, text: String, left: Float, top: Float) {
        val bounds = block.bounds
        val targetWidth = bounds.width().toFloat()
        val targetHeight = bounds.height().toFloat()

        // Apply adaptive colors
        val originalTextColor = textPaint.color
        val originalBgColor = bgPaint.color
        if (settings.adaptiveColors) {
            block.textColor?.let { textPaint.color = it }
            block.backgroundColor?.let {
                val alpha = (settings.overlayMaskRatio * 255).toInt()
                bgPaint.color = Color.argb(alpha, Color.red(it), Color.green(it), Color.blue(it))
            }
        }
        
        // Prioritize original line height for font size
        val originalLineHeight = block.firstLineBounds.height().toFloat()
        textPaint.textSize = originalLineHeight.coerceIn(12f, 120f)
        
        var layout = createStaticLayout(text, targetWidth.toInt())
        
        // Scaling strategy: fill the original area as much as possible while maintaining layout consistency
        if (layout.height > targetHeight * 1.1f) {
            while (layout.height > targetHeight * 1.1f && textPaint.textSize > 12f) {
                textPaint.textSize -= 0.5f
                layout = createStaticLayout(text, targetWidth.toInt())
            }
        } else if (layout.lineCount == 1 && layout.height < targetHeight * 0.8f) {
            // If only one line and there's plenty of height, scale up to fill width or height
            while (layout.height < targetHeight * 0.9f && getMaxLineWidth(layout) < targetWidth * 0.9f && textPaint.textSize < originalLineHeight * 1.2f) {
                textPaint.textSize += 0.5f
                layout = createStaticLayout(text, targetWidth.toInt())
            }
        }

        val actualTextWidth = getMaxLineWidth(layout)
        val finalWidth = max(targetWidth, actualTextWidth)
        val finalHeight = max(targetHeight, layout.height.toFloat())

        val drawRect = RectF(left, top, left + finalWidth, top + finalHeight)

        // Check if the drawing area exceeds the current view bounds (screen bounds)
        if (drawRect.left < 0 || drawRect.top < 0 || drawRect.right > width || drawRect.bottom > height) {
            Log.e(TAG, "ERROR: Horizontal block drawn out of screen! rect=$drawRect, viewSize=${width}x${height}")
        }

        // Use a rectangular box for a "textbox" feel and better coverage of original text
        canvas.drawRect(drawRect, bgPaint)

        canvas.withTranslation(drawRect.left, drawRect.top) {
            layout.draw(this)
        }

        // Restore original colors
        textPaint.color = originalTextColor
        bgPaint.color = originalBgColor
    }

    private fun drawVerticalBlock(canvas: Canvas, block: TextBlock, text: String, left: Float, top: Float) {
        val bounds = block.bounds
        val targetWidth = bounds.width().toFloat()
        val targetHeight = bounds.height().toFloat()

        // Apply adaptive colors
        val originalTextColor = textPaint.color
        val originalBgColor = bgPaint.color
        if (settings.adaptiveColors) {
            block.textColor?.let { textPaint.color = it }
            block.backgroundColor?.let {
                val alpha = (settings.overlayMaskRatio * 255).toInt()
                bgPaint.color = Color.argb(alpha, Color.red(it), Color.green(it), Color.blue(it))
            }
        }
        
        val originalColWidth = block.firstLineBounds.width().toFloat()
        var bestTextSize = originalColWidth.coerceIn(12f, 120f)
        
        fun layoutVertical(size: Float): List<List<String>> {
            textPaint.textSize = size
            val paragraphs = text.split("\n")
            val allCols = mutableListOf<List<String>>()
            val charHeight = size * 1.1f
            val maxCharsPerCol = (targetHeight / charHeight).toInt().coerceAtLeast(1)
            
            paragraphs.forEach { para ->
                if (para.isEmpty()) {
                    allCols.add(emptyList())
                    return@forEach
                }
                var i = 0
                while (i < para.length) {
                    val end = minOf(i + maxCharsPerCol, para.length)
                    allCols.add(para.substring(i, end).map { it.toString() })
                    i = end
                }
            }
            return allCols
        }

        var columnGroups = layoutVertical(bestTextSize)
        val colWidth = bestTextSize * 1.2f

        // Adjust vertical layout to match the original width
        if (columnGroups.size * colWidth > targetWidth * 1.1f) {
            while (columnGroups.size * (bestTextSize * 1.2f) > targetWidth * 1.1f && bestTextSize > 12f) {
                bestTextSize -= 0.5f
                columnGroups = layoutVertical(bestTextSize)
            }
        }

        val finalColWidth = bestTextSize * 1.2f
        val totalColsWidth = columnGroups.size * finalColWidth
        val finalWidth = max(targetWidth, totalColsWidth)

        val drawRect = RectF(left, top, left + finalWidth, top + targetHeight)

        // Check if the drawing area exceeds the current view bounds (screen bounds)
        if (drawRect.left < 0 || drawRect.top < 0 || drawRect.right > width || drawRect.bottom > height) {
            Log.e(TAG, "ERROR: Vertical block drawn out of screen! rect=$drawRect, viewSize=${width}x${height}")
        }

        canvas.drawRect(drawRect, bgPaint)

        columnGroups.forEachIndexed { colIdx, chars ->
            // Vertical text is usually right-to-left
            val colX = drawRect.right - (colIdx + 1) * finalColWidth
            var currentY = drawRect.top

            chars.forEach { charStr ->
                val charW = textPaint.measureText(charStr)
                canvas.drawText(charStr, colX + (finalColWidth - charW) / 2, currentY + bestTextSize, textPaint)
                currentY += bestTextSize * 1.1f
            }
        }

        // Restore original colors
        textPaint.color = originalTextColor
        bgPaint.color = originalBgColor
    }

    private fun getMaxLineWidth(layout: StaticLayout): Float {
        var maxW = 0f
        for (i in 0 until layout.lineCount) {
            val w = layout.getLineWidth(i)
            if (w > maxW) maxW = w
        }
        return maxW
    }

    private fun createStaticLayout(text: String, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, max(10, width))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .build()
    }


    private fun calculateDelay(): Long {
        if (settings.autoHideMode == AppSettings.AUTO_HIDE_MODE_FIXED) {
            return settings.displayDurationSec * 1000L
        }

        val currentTime = System.currentTimeMillis()
        
        // Explicit check: whether in "streaming translation" mode that requires cumulative budget
        val isStreamingFlow = settings.enableStreaming && !settings.ocrOnly

        val deadline = if (isStreamingFlow && streamingDeadline > 0) {
            // Already have a streaming cumulative budget, use that timestamp
            streamingDeadline
        } else {
            // Non-streaming (one-shot display) or OCR-only mode: calculate deadline based on total text length
            val totalLength = mergedBlocks.sumOf { it.translatedText?.length ?: 0 }
            currentTime + (totalLength * settings.durationPerWordMs)
        }

        return max(0L, deadline - currentTime)
    }

    private fun cancelPendingHide() {
        autoHideRunnable?.let { removeCallbacks(it) }
        autoHideRunnable = null
    }

    private fun scheduleHideTask(delay: Long) {
        val runnable = Runnable {
            OverlayManager.dismiss(OverlayManager.DismissReason.TIMER)
        }
        autoHideRunnable = runnable
        postDelayed(runnable, delay)
    }

    companion object {
        private val TAG = OverlayView::class.java.simpleName
    }
}
