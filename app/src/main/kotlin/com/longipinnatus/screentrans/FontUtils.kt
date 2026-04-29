package com.longipinnatus.screentrans

import android.graphics.fonts.SystemFonts
import android.os.Build
import java.io.File

object FontUtils {
    data class FontInfo(val name: String, val path: String)

    fun getSystemFonts(): List<FontInfo> {
        val fonts = mutableListOf<FontInfo>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                SystemFonts.getAvailableFonts().forEach { font ->
                    val file = font.file
                    if (file != null) {
                        // Use filename as a simple name, or we could try to get the family name
                        // but that requires more complex parsing.
                        fonts.add(FontInfo(file.name, file.absolutePath))
                    }
                }
            } catch (e: Exception) {
                LogManager.logSimple(LogType.ERROR, "FontUtils", "Failed to get system fonts: ${e.message}")
            }
        }
        
        if (fonts.isEmpty()) {
            val systemFontDir = File("/system/fonts")
            if (systemFontDir.exists() && systemFontDir.isDirectory) {
                systemFontDir.listFiles()?.forEach { file ->
                    if (file.extension.lowercase() in listOf("ttf", "otf")) {
                        fonts.add(FontInfo(file.name, file.absolutePath))
                    }
                }
            }
        }
        
        // Filter out duplicates by path and sort by name
        return fonts.distinctBy { it.path }.sortedBy { it.name }
    }
}
