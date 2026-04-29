package com.longipinnatus.screentrans

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.annotation.Keep
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.nio.FloatBuffer
import java.util.BitSet
import java.util.Collections
import java.util.LinkedList
import java.util.Queue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Keep
data class TextBlock(
    /** The recognized text from the OCR process. */
    val text: String,
    /** The overall bounding rectangle of the text block. */
    val bounds: Rect,
    /** The bounding rectangle of the first line in this block. */
    val firstLineBounds: Rect,
    /** The bounding rectangle of the last line in this block. */
    val lastLineBounds: Rect,
    /** The translated text, if translation has been performed. */
    var translatedText: String? = null,
    /** Indicates whether the text is oriented vertically. */
    val isVertical: Boolean = false,
    /** Estimated foreground (text) color. */
    var textColor: Int? = null,
    /** Estimated background color. */
    var backgroundColor: Int? = null,
    /** Weight of the color (usually based on text length) to help dominant color selection during merging. */
    var colorWeight: Int = 0,
    /** Number of physical lines this block contains. */
    var lineCount: Int = 1,
) {
    fun copy(): TextBlock {
        return TextBlock(
            text = text,
            bounds = Rect(bounds),
            firstLineBounds = Rect(firstLineBounds),
            lastLineBounds = Rect(lastLineBounds),
            translatedText = translatedText,
            isVertical = isVertical,
            textColor = textColor,
            backgroundColor = backgroundColor,
            colorWeight = colorWeight,
            lineCount = lineCount
        )
    }
}

object OcrEngine {
    private const val TAG = "OcrEngine"
    private var ortEnv: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var currentDetModel: String? = null
    private var currentRecModel: String? = null
    private var alphabet: List<String> = emptyList()

    fun init(context: Context, settings: AppSettings.SettingsData) {
        if ((ortEnv != null) && 
            (currentDetModel == settings.detModelType) && 
            (currentRecModel == settings.recModelType)) {
            return
        }
        
        try {
            if (ortEnv == null) {
                ortEnv = OrtEnvironment.getEnvironment()
            }
            
            // Release existing sessions if we are re-initializing
            detSession?.close()
            recSession?.close()

            Log.i(TAG, "Initializing OcrEngine...")
            
            var detModelPath = if (settings.detModelType == AppSettings.MODEL_TYPE_CUSTOM && settings.detCustomModelPath.isNotEmpty()) {
                settings.detCustomModelPath
            } else {
                getModelPath(context, if (settings.detModelType == AppSettings.MODEL_TYPE_CUSTOM) "det_custom.onnx" else "det_mobile.onnx")
            }

            // Fallback if custom model is missing
            if (settings.detModelType == AppSettings.MODEL_TYPE_CUSTOM && (detModelPath.isEmpty() || !java.io.File(detModelPath).exists())) {
                Log.w(TAG, "Custom detection model not found, falling back to mobile")
                detModelPath = getModelPath(context, "det_mobile.onnx")
            }

            var recModelPath = if (settings.recModelType == AppSettings.MODEL_TYPE_CUSTOM && settings.recCustomModelPath.isNotEmpty()) {
                settings.recCustomModelPath
            } else {
                getModelPath(context, if (settings.recModelType == AppSettings.MODEL_TYPE_CUSTOM) "rec_custom.onnx" else "rec_mobile.onnx")
            }

            if (settings.recModelType == AppSettings.MODEL_TYPE_CUSTOM && (recModelPath.isEmpty() || !java.io.File(recModelPath).exists())) {
                Log.w(TAG, "Custom recognition model not found, falling back to mobile")
                recModelPath = getModelPath(context, "rec_mobile.onnx")
            }

            if (detModelPath.isEmpty() || recModelPath.isEmpty()) {
                Log.e(TAG, "Required models not found. Det: $detModelPath, Rec: $recModelPath")
                return
            }

            Log.i(TAG, "Detection model path: $detModelPath")
            Log.i(TAG, "Recognition model path: $recModelPath")

            val sessionOptions = OrtSession.SessionOptions()
            Log.i(TAG, "Using CPU for inference.")
            
            detSession = ortEnv?.createSession(detModelPath, sessionOptions)
            recSession = ortEnv?.createSession(recModelPath, sessionOptions)
            
            currentDetModel = settings.detModelType
            currentRecModel = settings.recModelType

            LogManager.log(LogType.INFO, TAG, listOf(
                LogEntry("Status", "OcrEngine initialized"),
                LogEntry("DetModel", detModelPath.substringAfterLast("/")),
                LogEntry("RecModel", recModelPath.substringAfterLast("/"))
            ))

            alphabet = context.assets.open("dict.txt").bufferedReader().useLines { lines ->
                val list = mutableListOf<String>()
                list.add("blank")
                lines.forEach { list.add(it) }
                list.add(" ")
                list
            }
            Log.d(TAG, "OcrEngine initialized.")
        } catch (e: Exception) {
            LogManager.logException(TAG, e, "Initialization failed")
        }
    }

    fun release() {
        try {
            detSession?.close()
            detSession = null
            recSession?.close()
            recSession = null
            ortEnv?.close()
            ortEnv = null
        } catch (e: Exception) {
            Log.e(TAG, "Release failed", e)
        }
    }

    fun detect(bitmap: Bitmap, settings: AppSettings.SettingsData): List<TextBlock> {
        val env = ortEnv ?: run {
            LogManager.logSimple(LogType.ERROR, TAG, "detect: ortEnv is null")
            return emptyList()
        }
        val det = detSession ?: run {
            LogManager.logSimple(LogType.ERROR, TAG, "detect: detSession is null")
            return emptyList()
        }
        
        Log.d(TAG, "detect: input bitmap size=${bitmap.width}x${bitmap.height}")
        
        // 1. Matches DetResizeForTest: resize_long: 960 in YAML
        val ratio = 960f / max(bitmap.width, bitmap.height).coerceAtLeast(1)
        val detWidth = max(32, ((bitmap.width * ratio / 32.0).roundToInt() * 32))
        val detHeight = max(32, ((bitmap.height * ratio / 32.0).roundToInt() * 32))
        
        Log.d(TAG, "detect: detSize=${detWidth}x${detHeight}, ratio=$ratio")

        if (detWidth <= 0 || detHeight <= 0) return emptyList()

        val scaledBitmap = bitmap.scale(detWidth, detHeight)
        val rawBlocks = mutableListOf<TextBlock>()

        try {
            val imgData = FloatBuffer.allocate(1 * 3 * detHeight * detWidth)
            val pixels = IntArray(detWidth * detHeight)
            scaledBitmap.getPixels(pixels, 0, detWidth, 0, 0, detWidth, detHeight)

            // 2. Matches img_mode: BGR and NormalizeImage in YAML
            // PaddleOCR applies mean/std to B, G, R sequentially in BGR mode
            val means = floatArrayOf(0.485f, 0.456f, 0.406f)
            val stds = floatArrayOf(0.229f, 0.224f, 0.225f)
            for (c in 0..2) {
                for (i in 0 until detHeight) {
                    for (j in 0 until detWidth) {
                        val pix = pixels[i * detWidth + j]
                        val v = when (c) {
                            0 -> pix and 0xFF              // Blue
                            1 -> (pix shr 8) and 0xFF      // Green
                            else -> (pix shr 16) and 0xFF // Red
                        }
                        imgData.put((v / 255.0f - means[c]) / stds[c])
                    }
                }
            }
            imgData.rewind()

            val inputName = det.inputNames.iterator().next()
            OnnxTensor.createTensor(env, imgData, longArrayOf(1, 3, detHeight.toLong(), detWidth.toLong())).use { inputTensor ->
                det.run(Collections.singletonMap(inputName, inputTensor)).use { results ->
                    val outputTensor = results[0] as OnnxTensor
                    val shape = outputTensor.info.shape
                    val outH = shape[2].toInt()
                    val outW = shape[3].toInt()
                    val probMap = FloatArray(outH * outW)
                    outputTensor.floatBuffer.get(probMap)

                    val scaleX = bitmap.width.toFloat() / outW
                    val scaleY = bitmap.height.toFloat() / outH
                    Log.d(TAG, "detect: output shape=${outW}x${outH}, scale=${scaleX}x${scaleY}")
                    val boxes = extractBoxes(probMap, outH, outW, scaleX, scaleY, settings)

                    for (box in boxes) {
                        val isVert = when (settings.textOrientation) {
                            AppSettings.TEXT_ORIENTATION_VERTICAL -> true
                            AppSettings.TEXT_ORIENTATION_HORIZONTAL -> false
                            else -> box.height() > box.width() * 1.5
                        }
                        val cropped = cropAndRescale(bitmap, box)
                        val colors = extractColors(cropped)
                        val finalInput = if (isVert) {
                            val rotated = rotateBitmap(cropped)
                            if (rotated !== cropped && cropped !== bitmap) cropped.recycle()
                            rotated
                        } else {
                            cropped
                        }

                        val text = recognize(finalInput).trim()
                        
                        if (finalInput !== bitmap) finalInput.recycle()

                        if (text.isNotEmpty()) {
                            rawBlocks.add(
                                TextBlock(
                                    text = text, 
                                    bounds = box, 
                                    firstLineBounds = box, 
                                    lastLineBounds = box, 
                                    isVertical = isVert,
                                    textColor = colors.first,
                                    backgroundColor = colors.second,
                                    colorWeight = text.length
                                )
                            )
                        }
                    }
                }
            }
        } finally {
            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
        }
        return rawBlocks
    }



    private fun recognize(bitmap: Bitmap): String {
        val env = ortEnv ?: return ""
        val rec = recSession ?: return ""
        val targetH = 48
        val ratio = targetH.toFloat() / bitmap.height
        val targetW = max(targetH, (bitmap.width * ratio).toInt())
        val scaled = bitmap.scale(targetW, targetH)
        
        try {
            val imgData = FloatBuffer.allocate(1 * 3 * targetH * targetW)
            val pixels = IntArray(targetW * targetH)
            scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            for (c in 0..2) {
                for (i in 0 until targetH) {
                    for (j in 0 until targetW) {
                        val pix = pixels[i * targetW + j]
                        val v = when (c) {
                            0 -> pix and 0xFF              // Blue (Matches BGR)
                            1 -> (pix shr 8) and 0xFF      // Green
                            else -> (pix shr 16) and 0xFF  // Red
                        }
                        imgData.put((v / 255.0f - 0.5f) / 0.5f)
                    }
                }
            }
            imgData.rewind()

            val inputName = rec.inputNames.iterator().next()
            OnnxTensor.createTensor(env, imgData, longArrayOf(1, 3, targetH.toLong(), targetW.toLong())).use { inputTensor ->
                rec.run(Collections.singletonMap(inputName, inputTensor)).use { results ->
                    val outputTensor = results.asSequence()
                        .map { it.value as OnnxTensor }
                        .firstOrNull { it.info.shape.size == 3 }
                        ?: (results[0] as OnnxTensor)

                    val recShape = outputTensor.info.shape
                    val seqLen = recShape[1].toInt()
                    val dictSize = recShape[2].toInt()
                    val outputArr = FloatArray(seqLen * dictSize)
                    outputTensor.floatBuffer.get(outputArr)
                    val sb = StringBuilder()
                    var lastIdx = -1
                    for (i in 0 until seqLen) {
                        var maxVal = -100f; var maxIdx = -1
                        for (j in 0 until dictSize) {
                            val v = outputArr[i * dictSize + j]
                            if (v > maxVal) { maxVal = v; maxIdx = j }
                        }
                        if (maxIdx > 0 && maxIdx < alphabet.size && maxIdx != lastIdx) {
                            sb.append(alphabet[maxIdx])
                        }
                        lastIdx = maxIdx
                    }
                    return sb.toString()
                }
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun extractBoxes(probMap: FloatArray, h: Int, w: Int, scaleX: Float, scaleY: Float, settings: AppSettings.SettingsData): List<Rect> {
        val visited = BitSet(h * w)
        val boxes = mutableListOf<Rect>()
        for (i in 0 until h) {
            for (j in 0 until w) {
                val idx = i * w + j
                if (probMap[idx] > settings.pixelThresh && !visited.get(idx)) {
                    var minI = i; var maxI = i
                    var minJ = j; var maxJ = j
                    var sumScore = 0f; var count = 0
                    val q: Queue<Int> = LinkedList()
                    q.add(idx)
                    visited.set(idx)
                    while (q.isNotEmpty()) {
                        val curr = q.remove()
                        count++
                        val ci = curr / w; val cj = curr % w
                        sumScore += probMap[curr]
                        minI = min(minI, ci); maxI = max(maxI, ci)
                        minJ = min(minJ, cj); maxJ = max(maxJ, cj)
                        val neighbors = arrayOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
                        for (d in neighbors) {
                            val ni = ci + d.first; val nj = cj + d.second
                            if (ni in 0 until h && nj in 0 until w) {
                                val nIdx = ni * w + nj
                                if (probMap[nIdx] > settings.pixelThresh && !visited.get(nIdx)) {
                                    visited.set(nIdx); q.add(nIdx)
                                }
                            }
                        }
                    }
                    val avgScore = sumScore / count
                    if (avgScore > settings.boxThresh && count > 10) {
                        val boxW = (maxJ - minJ).toFloat()
                        val boxH = (maxI - minI).toFloat()
                        val offset = (boxW * boxH * settings.unclipRatio) / (2 * (boxW + boxH))
                        
                        val left = ((minJ - offset) * scaleX).toInt()
                        val top = ((minI - offset) * scaleY).toInt()
                        val right = ((maxJ + offset) * scaleX).toInt()
                        val bottom = ((maxI + offset) * scaleY).toInt()
                        
                        if (left < 0 || top < 0 || right > (w * scaleX) || bottom > (h * scaleY)) {
                            Log.w(TAG, "extractBoxes: Box potentially out of bounds: ($left, $top, $right, $bottom) for bitmap size ${w * scaleX}x${h * scaleY}")
                            Log.d(TAG, "extractBoxes: raw minJ=$minJ, minI=$minI, maxJ=$maxJ, maxI=$maxI, offset=$offset")
                        }

                        boxes.add(Rect(left, top, right, bottom))
                    }
                }
            }
        }
        return boxes
    }

    private fun cropAndRescale(original: Bitmap, rect: Rect): Bitmap {
        val x = max(0, rect.left); val y = max(0, rect.top)
        val width = min(rect.width(), original.width - x)
        val height = min(rect.height(), original.height - y)
        if (width <= 0 || height <= 0) return createBitmap(1, 1)
        return Bitmap.createBitmap(original, x, y, width, height)
    }

    private fun extractColors(bitmap: Bitmap): Pair<Int, Int> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return Pair(Color.WHITE, Color.BLACK)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Helper to quantize color to group similar shades (bits reduction)
        fun quantize(color: Int): Int {
            val r = Color.red(color) and 0xF0
            val g = Color.green(color) and 0xF0
            val b = Color.blue(color) and 0xF0
            return Color.rgb(r, g, b)
        }

        // 1. Estimate background color from the edges
        val edgeColors = mutableListOf<Int>()
        for (i in 0 until width) {
            edgeColors.add(pixels[i]) // Top
            edgeColors.add(pixels[(height - 1) * width + i]) // Bottom
        }
        for (i in 0 until height) {
            edgeColors.add(pixels[i * width]) // Left
            edgeColors.add(pixels[i * width + width - 1]) // Right
        }
        
        // Use quantized mode for background to handle noise/gradients
        val quantizedBg = edgeColors.groupBy { quantize(it) }.maxByOrNull { it.value.size }?.key ?: Color.BLACK
        val bgColor = edgeColors.filter { quantize(it) == quantizedBg }
            .groupBy { it }.maxByOrNull { it.value.size }?.key ?: quantizedBg
        
        Log.d(TAG, "extractColors: BG Detected: ${Integer.toHexString(bgColor)} (Quantized: ${Integer.toHexString(quantizedBg)})")

        // 2. Estimate text color: look for the most prominent color different from background
        val bgR = Color.red(bgColor); val bgG = Color.green(bgColor); val bgB = Color.blue(bgColor)
        val foregroundPixels = mutableListOf<Int>()
        
        for (pixel in pixels) {
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            val dist = abs(r - bgR) + abs(g - bgG) + abs(b - bgB)
            if (dist > 120) { // Threshold for being "foreground"
                foregroundPixels.add(pixel)
            }
        }
        
        Log.d(TAG, "extractColors: Foreground pixel count: ${foregroundPixels.size}/${pixels.size}")

        val textColor = if (foregroundPixels.isNotEmpty()) {
            // Group foreground pixels into quantized buckets
            val buckets = foregroundPixels.groupBy { quantize(it) }
            // Get top 3 most frequent color buckets to avoid being skewed by outliers
            val topBuckets = buckets.entries.sortedByDescending { it.value.size }.take(3)
            
            // From these frequent buckets, pick the one with maximum contrast to background
            val bestQuantizedFg = topBuckets.maxByOrNull { (qColor, _) ->
                val r = Color.red(qColor); val g = Color.green(qColor); val b = Color.blue(qColor)
                abs(r - bgR) + abs(g - bgG) + abs(b - bgB)
            }?.key ?: Color.WHITE

            val finalFg = foregroundPixels.filter { quantize(it) == bestQuantizedFg }
                .groupBy { it }.maxByOrNull { it.value.size }?.key ?: bestQuantizedFg
            
            Log.d(TAG, "extractColors: Text Detected: ${Integer.toHexString(finalFg)} (Best contrast among top buckets)")
            finalFg
        } else {
            val fallback = if (bgR + bgG + bgB > 382) Color.BLACK else Color.WHITE
            Log.d(TAG, "extractColors: No foreground found, using fallback: ${Integer.toHexString(fallback)}")
            fallback
        }
        
        return Pair(textColor, bgColor)
    }

    private fun getModelPath(context: Context, modelFile: String): String {
        val file = java.io.File(context.cacheDir, modelFile)
        if (file.exists()) {
            return file.absolutePath
        }
        
        return try {
            context.assets.open(modelFile).use { input ->
                java.io.FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Model $modelFile not found in assets or cache: $e")
            ""
        }
    }

    private fun rotateBitmap(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(-90f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
