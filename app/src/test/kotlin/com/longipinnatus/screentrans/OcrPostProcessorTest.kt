package com.longipinnatus.screentrans

import android.graphics.Rect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OcrPostProcessorTest {

    @Test
    fun testHorizontalEnglishMerge() {
        val raw = listOf(
            createBlock("Hello", 50, 50, 150, 90),
            createBlock("World", 160, 50, 260, 90)
        )
        runTest(raw, "ocr_horizontal_english.svg")
    }

    @Test
    fun testHorizontalCJKMerge() {
        val raw = listOf(
            createBlock("你好", 50, 100, 150, 140),
            createBlock("世界", 152, 100, 252, 140)
        )
        runTest(raw, "ocr_horizontal_cjk.svg")
    }

    @Test
    fun testVerticalMerge() {
        val raw = listOf(
            createBlock("竖", 400, 50, 450, 100, isVertical = true),
            createBlock("排", 400, 110, 450, 160, isVertical = true),
            createBlock("文", 400, 170, 450, 220, isVertical = true)
        )
        runTest(raw, "ocr_vertical.svg")
    }

    @Test
    fun testParagraphDetection() {
        val raw = listOf(
            createBlock("Line 1 of Para A", 50, 200, 300, 240),
            createBlock("Line 2 of Para A", 50, 250, 300, 290),
            createBlock("Start of Para B", 50, 380, 300, 420)
        )
        runTest(raw, "ocr_paragraphs.svg")
    }

    @Test
    fun testComplexLayout() {
        val raw = listOf(
            createBlock("Main Title", 200, 20, 500, 70),
            createBlock("Column 1 - Part 1", 50, 100, 250, 130),
            createBlock("Column 1 - Part 2", 50, 140, 250, 170),
            createBlock("Column 2 - Part 1", 300, 100, 500, 130),
            createBlock("Column 2 - Part 2", 300, 140, 500, 170),
            createBlock("Page 1", 250, 600, 300, 620)
        )
        runTest(raw, "ocr_complex.svg")
    }

    private fun runTest(raw: List<TextBlock>, fileName: String) {
        val result = OcrPostProcessor.processRawBlocks(raw, AppSettings.SettingsData())
        generateSvg(raw, result.mergedBlocks, fileName)
        println("Generated: ${File(fileName).absolutePath}")
    }

    private fun createBlock(text: String, l: Int, t: Int, r: Int, b: Int, isVertical: Boolean = false): TextBlock {
        val rect = Rect(l, t, r, b)
        return TextBlock(text, rect, rect, rect, isVertical = isVertical)
    }

    private fun generateSvg(raw: List<TextBlock>, merged: List<TextBlock>, fileName: String) {
        val sb = StringBuilder()
        val width = 1000
        val height = 800
        
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='$width' height='$height' viewBox='0 0 $width $height'>")
        
        // Background grid
        sb.append("<rect width='100%' height='100%' fill='#fdfdfd' />")
        for (i in 0..width step 100) sb.append("<line x1='$i' y1='0' x2='$i' y2='$height' stroke='#eeeeee' stroke-width='1' />")
        for (j in 0..height step 100) sb.append("<line x1='0' y1='$j' x2='$width' y2='$j' stroke='#eeeeee' stroke-width='1' />")

        // Raw blocks: gray dashed line
        raw.forEachIndexed { i, b ->
            val r = b.bounds
            sb.append("<rect x='${r.left}' y='${r.top}' width='${r.width()}' height='${r.height()}' ")
            sb.append("fill='rgba(200, 200, 200, 0.1)' stroke='#bbbbbb' stroke-width='1' stroke-dasharray='4,2' />")
            sb.append("<text x='${r.left}' y='${r.bottom + 8}' font-size='7' fill='#aaaaaa'>r$i</text>")
        }

        // After merging: red solid line
        merged.forEachIndexed { i, b ->
            val r = b.bounds
            sb.append("<rect x='${r.left}' y='${r.top}' width='${r.width()}' height='${r.height()}' ")
            sb.append("fill='none' stroke='red' stroke-width='2' />")
            
            // Indicator points for first and last lines
            sb.append("<circle cx='${b.firstLineBounds.centerX()}' cy='${b.firstLineBounds.centerY()}' r='3' fill='#FFD700' opacity='0.6' />")
            sb.append("<circle cx='${b.lastLineBounds.centerX()}' cy='${b.lastLineBounds.centerY()}' r='3' fill='#800080' opacity='0.6' />")

            val cleanText = b.text.replace("\n", "\\n").replace("<", "&lt;").replace(">", "&gt;")
            sb.append("<text x='${r.left}' y='${r.top - 4}' font-size='14' font-family='sans-serif' fill='blue' font-weight='bold'>")
            sb.append("#$i $cleanText")
            sb.append("</text>")
        }

        sb.append("</svg>")
        File(fileName).writeText(sb.toString())
    }
}
