package com.longipinnatus.screentrans

import android.graphics.Rect
import android.util.Log
import com.google.gson.GsonBuilder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OcrResult(
    val mergedBlocks: List<TextBlock>,
    val rawBlocks: List<TextBlock>
)

object OcrPostProcessor {
    private const val TAG = "OcrPostProcessor"
    private val gson = GsonBuilder().create()

    // Thresholds for merging
    private const val SAME_AXIS_OVERLAP_RATIO = 0.6f
    private const val CROSS_AXIS_GAP_RATIO = 1.2f
    private const val STACK_OVERLAP_RATIO = 0.5f
    private const val STACK_MAX_GAP_RATIO = 1.3f
    private const val HARD_BREAK_RATIO = 3.0f      // If line ends earlier than this * font size, it's a hard break
    private const val INDENT_RATIO = 2.5f          // Max indentation (relative to font size) to still merge as same block

    fun processRawBlocks(rawBlocks: List<TextBlock>, settings: AppSettings.SettingsData): OcrResult {
        val ignoredRaw = mutableListOf<TextBlock>()
        val filteredRaw = rawBlocks.filter { block ->
            if (shouldIgnore(block.text, block.bounds.width(), block.bounds.height(), isMerged = false, settings)) {
                ignoredRaw.add(block)
                false
            } else {
                true
            }
        }

        // Deep copy: ensure filteredMerged and filteredRaw do not share any TextBlock instances
        // Otherwise, if no merge occurs, mergeBlocks might return original instances from filteredRaw
        val merged = if (settings.mergeTextBoxes) {
            mergeBlocks(filteredRaw).map { it.copy() }
        } else {
            filteredRaw.map { it.copy() }
        }

        val ignoredMerged = mutableListOf<TextBlock>()
        val filteredMerged = mutableListOf<TextBlock>()
        for (block in merged) {
            if (shouldIgnore(block.text, block.bounds.width(), block.bounds.height(), isMerged = true, settings)) {
                ignoredMerged.add(block)
            } else {
                filteredMerged.add(block)
            }
        }

        logResults(ignoredRaw, filteredRaw, ignoredMerged, filteredMerged, settings)

        return OcrResult(filteredMerged, filteredRaw)
    }

    private fun logResults(
        ignoredRaw: List<TextBlock>,
        filteredRaw: List<TextBlock>,
        ignoredMerged: List<TextBlock>,
        filteredMerged: List<TextBlock>,
        settings: AppSettings.SettingsData
    ) {
        val logEntries = mutableListOf<LogEntry>()
        if (ignoredRaw.isNotEmpty()) logEntries.add(LogEntry("Ignored Raw Blocks", gson.toJson(ignoredRaw)))
        if (filteredRaw.isNotEmpty()) logEntries.add(LogEntry("Raw OCR Blocks", gson.toJson(filteredRaw)))
        if (settings.mergeTextBoxes) {
            if (ignoredMerged.isNotEmpty()) logEntries.add(LogEntry("Ignored Merged Blocks", gson.toJson(ignoredMerged)))
            if (filteredMerged.isNotEmpty()) logEntries.add(LogEntry("Merged OCR Blocks", gson.toJson(filteredMerged)))
        }
        if (logEntries.isNotEmpty()) {
            LogManager.log(LogType.DEBUG, TAG, logEntries)
        }
    }

    private fun shouldIgnore(text: String, width: Int, height: Int, isMerged: Boolean, settings: AppSettings.SettingsData): Boolean {
        Log.d(TAG, "Checking Text: $text, Merged: $isMerged")
        for (rule in settings.filterRules) {
            if (!rule.enabled) continue
            if (isMerged && !rule.applyToMerged) continue
            if (!isMerged && !rule.applyToRaw) continue

            val sizeMatch = (rule.minWidth !in 1..width) &&
                          (rule.minHeight !in 1..height)
            val textMatch = if (rule.regex.isEmpty()) {
                true 
            } else {
                try {
                    Regex(rule.regex, RegexOption.IGNORE_CASE).containsMatchIn(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid regex in rule: ${rule.regex}", e)
                    false
                }
            }

            if (sizeMatch && textMatch) return true
        }
        return false
    }

    private fun mergeBlocks(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.size < 2) return blocks
        
        val sorted = blocks.sortedWith { b1, b2 ->
            if (b1.isVertical != b2.isVertical) {
                b1.isVertical.compareTo(b2.isVertical)
            } else if (b1.isVertical) {
                if (abs(b1.bounds.right - b2.bounds.right) < (b1.bounds.width() + b2.bounds.width()) / 4) {
                    b1.bounds.top.compareTo(b2.bounds.top)
                } else {
                    b2.bounds.right.compareTo(b1.bounds.right)
                }
            } else {
                if (abs(b1.bounds.top - b2.bounds.top) < (b1.bounds.height() + b2.bounds.height()) / 4) {
                    b1.bounds.left.compareTo(b2.bounds.left)
                } else {
                    b1.bounds.top.compareTo(b2.bounds.top)
                }
            }
        }

        val result = sorted.toMutableList()
        var changed: Boolean
        do {
            changed = false
            var i = 0
            while (i < result.size) {
                var j = i + 1
                while (j < result.size) {
                    val b1 = result[i]
                    val b2 = result[j]
                    if (shouldMerge(b1, b2)) {
                        result[i] = merge(b1, b2)
                        result.removeAt(j)
                        changed = true
                        continue
                    }
                    j++
                }
                i++
            }
        } while (changed)
        
        return result
    }

    private fun shouldMerge(a: TextBlock, b: TextBlock): Boolean {
        if (a.isVertical != b.isVertical) return false
        val r1 = a.bounds
        val r2 = b.bounds
        val avgColW = (a.firstLineBounds.width() + b.firstLineBounds.width()) / 2f
        val avgRowH = (a.firstLineBounds.height() + b.firstLineBounds.height()) / 2f

        if (a.isVertical) {
            // Same column (stacked vertically)
            val hOverlap = max(0, min(r1.right, r2.right) - max(r1.left, r2.left))
            val vGap = if (r1.top < r2.top) r2.top - r1.bottom else r1.top - r2.bottom
            if (hOverlap > avgColW * SAME_AXIS_OVERLAP_RATIO && vGap < avgColW * STACK_MAX_GAP_RATIO) return true

            // Adjacent columns (merging right-to-left)
            val vOverlap = max(0, min(r1.bottom, r2.bottom) - max(r1.top, r2.top))
            val hGap = if (r1.left < r2.left) r2.left - r1.right else r1.left - r2.right
            // For manga, adjacent columns must have significant vertical overlap and small horizontal gap
            if (vOverlap > min(r1.height(), r2.height()) * STACK_OVERLAP_RATIO && hGap < avgColW * STACK_MAX_GAP_RATIO) return true
        } else {
            // Same row (side-by-side)
            val vOverlap = max(0, min(r1.bottom, r2.bottom) - max(r1.top, r2.top))
            val hGap = if (r1.left < r2.left) r2.left - r1.right else r1.left - r2.right
            if (vOverlap > avgRowH * SAME_AXIS_OVERLAP_RATIO && hGap < avgRowH * CROSS_AXIS_GAP_RATIO) return true

            // Different rows (stacked horizontally)
            val hOverlap = max(0, min(r1.right, r2.right) - max(r1.left, r2.left))
            val vGap = if (r1.top < r2.top) r2.top - r1.bottom else r1.top - r2.bottom
            if (hOverlap > min(r1.width(), r2.width()) * STACK_OVERLAP_RATIO && vGap < avgRowH * STACK_MAX_GAP_RATIO) return true
        }
        return false
    }

    private fun merge(a: TextBlock, b: TextBlock): TextBlock {
        val newBounds = Rect(a.bounds)
        newBounds.union(b.bounds)
        val text: String
        val firstLineBounds: Rect
        val lastLineBounds: Rect

        if (a.isVertical) {
            val (right, left) = if (a.bounds.left > b.bounds.left) a to b else b to a
            // Use font size from the first/last lines, which are the most reliable proxies for a single column width
            val avgW = (a.firstLineBounds.width() + b.firstLineBounds.width()) / 2f
            val hOverlap = max(0, min(a.bounds.right, b.bounds.right) - max(a.bounds.left, b.bounds.left))

            if (hOverlap > avgW * SAME_AXIS_OVERLAP_RATIO) {
                // Same column (vertical stack)
                val (upper, lower) = if (a.bounds.top < b.bounds.top) a to b else b to a
                val sep = if (isCJK(upper.text) && isCJK(lower.text)) "" else " "
                text = upper.text + sep + lower.text
                // Preserve the original line height by keeping top-most and bottom-most line bounds
                firstLineBounds = upper.firstLineBounds
                lastLineBounds = lower.lastLineBounds
            } else {
                // Adjacent columns (Right to Left)
                val blockBottom = newBounds.bottom
                // If the previous column's last line is significantly shorter than the block bottom, it's a hard break
                val isHardBreak = right.lastLineBounds.bottom < blockBottom - avgW * HARD_BREAK_RATIO
                val isIndent = left.firstLineBounds.top > right.firstLineBounds.top + avgW * INDENT_RATIO

                val sep = if (isHardBreak || isIndent) "\n" else (if (isCJK(right.text) && isCJK(left.text)) "\n" else " ")
                text = right.text + sep + left.text
                // When merging columns, the "first line" (column) is the rightmost one
                // and the "last line" (column) is the leftmost one.
                firstLineBounds = right.firstLineBounds
                lastLineBounds = left.lastLineBounds
            }
        } else {
            val (upper, lower) = if (a.bounds.top < b.bounds.top) a to b else b to a
            val avgH = (a.firstLineBounds.height() + b.firstLineBounds.height()) / 2f
            val vOverlap = max(0, min(a.bounds.bottom, b.bounds.bottom) - max(a.bounds.top, b.bounds.top))

            if (vOverlap > avgH * SAME_AXIS_OVERLAP_RATIO) {
                // Same row (horizontal merge)
                val (l, r) = if (a.bounds.left < b.bounds.left) a to b else b to a
                val sep = if (isCJK(l.text) && isCJK(r.text)) "" else " "
                text = l.text + sep + r.text
                firstLineBounds = Rect(l.firstLineBounds).apply { union(r.firstLineBounds) }
                lastLineBounds = Rect(l.lastLineBounds).apply { union(r.lastLineBounds) }
            } else {
                // Different rows (stacked)
                val blockRight = newBounds.right
                val isHardBreak = upper.lastLineBounds.right < blockRight - avgH * HARD_BREAK_RATIO
                val isIndent = lower.firstLineBounds.left > upper.firstLineBounds.left + avgH * INDENT_RATIO

                val vGap = lower.bounds.top - upper.bounds.bottom
                // If the gap is large enough to fit another line, it's an "empty line"
                val isDoubleBreak = vGap > avgH * 1.5f

                Log.d(TAG, """
                    Text1: "${a.text}"\n\n
                    Text2: "${b.text}"\n\n
                    isDoubleBreak=$isDoubleBreak, vGap=$vGap, avgH=$avgH, avgH*1.5=${avgH * 1.5f}\n
                    isHardBreak=$isHardBreak, isIndent=$isIndent\n
                """.trimIndent())

                val sep = when {
                    isDoubleBreak -> "\n\n"
                    isHardBreak || isIndent -> "\n"
                    isCJK(upper.text) && isCJK(lower.text) -> ""
                    else -> " "
                }
                text = upper.text + sep + lower.text
                firstLineBounds = upper.firstLineBounds
                lastLineBounds = lower.lastLineBounds
            }
        }
        
        // Boyer-Moore Voting algorithm logic for dominant color selection
        val (finalColor, finalBg, finalWeight) = if (a.textColor == b.textColor) {
            // Same color: boost the confidence
            Triple(a.textColor, a.backgroundColor, a.colorWeight + b.colorWeight)
        } else {
            // Different colors: they cancel each other out
            if (a.colorWeight > b.colorWeight) {
                Triple(a.textColor, a.backgroundColor, a.colorWeight - b.colorWeight)
            } else if (b.colorWeight > a.colorWeight) {
                Triple(b.textColor, b.backgroundColor, b.colorWeight - a.colorWeight)
            } else {
                // Perfect tie: weight becomes 0, next merge will decide the new candidate
                Triple(a.textColor, a.backgroundColor, 0)
            }
        }
        
        Log.d(TAG, "merge: '${a.text.take(5)}...' (${Integer.toHexString(a.textColor ?: 0)}, w=${a.colorWeight}) + " +
                "'${b.text.take(5)}...' (${Integer.toHexString(b.textColor ?: 0)}, w=${b.colorWeight}) -> " +
                "Result: ${Integer.toHexString(finalColor ?: 0)}, w=$finalWeight")

        val newLineCount = if (a.isVertical) {
            val hOverlap = max(0, min(a.bounds.right, b.bounds.right) - max(a.bounds.left, b.bounds.left))
            val avgW = (a.firstLineBounds.width() + b.firstLineBounds.width()) / 2f
            if (hOverlap > avgW * SAME_AXIS_OVERLAP_RATIO) a.lineCount.coerceAtLeast(b.lineCount) else a.lineCount + b.lineCount
        } else {
            val vOverlap = max(0, min(a.bounds.bottom, b.bounds.bottom) - max(a.bounds.top, b.bounds.top))
            val avgH = (a.firstLineBounds.height() + b.firstLineBounds.height()) / 2f
            if (vOverlap > avgH * SAME_AXIS_OVERLAP_RATIO) a.lineCount.coerceAtLeast(b.lineCount) else a.lineCount + b.lineCount
        }

        return TextBlock(
            text, 
            newBounds, 
            firstLineBounds, 
            lastLineBounds, 
            isVertical = a.isVertical,
            textColor = finalColor,
            backgroundColor = finalBg,
            colorWeight = finalWeight,
            lineCount = newLineCount
        )
    }

    private fun isCJK(text: String): Boolean {
        return text.any { c ->
            val code = c.code
            code in 0x4E00..0x9FFF || 
            code in 0x3040..0x30FF || 
            code in 0xAC00..0xD7AF || 
            code in 0x3000..0x303F || 
            code in 0xFF00..0xFFEF    
        }
    }
}
