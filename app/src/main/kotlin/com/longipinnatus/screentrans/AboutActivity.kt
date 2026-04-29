package com.longipinnatus.screentrans

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.longipinnatus.screentrans.ui.theme.ScreenTransAITheme

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenTransAITheme {
                AboutScreen {
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (e: Exception) {
            LogManager.logSimple(LogType.ERROR, "AboutActivity", "Failed to get version name: ${e.message}")
            "1.0"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B3146))
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "ScreenTrans AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Created by Raphanus, 2026",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Licensed under GNU GPL v3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "This project is developed with the assistance of AI.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Open Source Licenses",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LicenseSection(
                category = "OCR Engine & Models",
                items = listOf(
                    LicenseItem(
                        name = "PaddleOCR",
                        url = "https://github.com/PaddlePaddle/PaddleOCR",
                        license = "Apache License 2.0",
                        description = "Ultra-lightweight OCR system and pre-trained models."
                    ),
                    LicenseItem(
                        name = "ONNX Runtime",
                        url = "https://github.com/microsoft/onnxruntime",
                        license = "MIT License",
                        description = "Cross-platform machine learning model accelerator."
                    )
                )
            )

            LicenseSection(
                category = "Android Libraries",
                items = listOf(
                    LicenseItem(
                        name = "Android Jetpack (Compose, DataStore, etc.)",
                        url = "https://developer.android.com/jetpack",
                        license = "Apache License 2.0",
                        description = "Modern Android development components."
                    ),
                    LicenseItem(
                        name = "OkHttp",
                        url = "https://github.com/square/okhttp",
                        license = "Apache License 2.0",
                        description = "An HTTP client for Android and Java applications."
                    ),
                    LicenseItem(
                        name = "Gson",
                        url = "https://github.com/google/gson",
                        license = "Apache License 2.0",
                        description = "A Java library that can be used to convert Java Objects into their JSON representation."
                    ),
                    LicenseItem(
                        name = "Kotlin & Coroutines",
                        url = "https://github.com/JetBrains/kotlin",
                        license = "Apache License 2.0",
                        description = "The Kotlin Programming Language and its coroutine support."
                    )
                )
            )

            LicenseSection(
                category = "Design",
                items = listOf(
                    LicenseItem(
                        name = "Material Design 3",
                        url = "https://m3.material.io/",
                        license = "Apache License 2.0",
                        description = "Google's latest design system."
                    )
                )
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Made with ❤️ by Raphanus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LicenseSection(category: String, items: List<LicenseItem>) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = category,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        items.forEach { item ->
            LicenseItemUI(item)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
    }
}

data class LicenseItem(
    val name: String,
    val url: String,
    val license: String,
    val description: String
)

@Composable
fun LicenseItemUI(item: LicenseItem) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.license,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Visit Project",
                style = MaterialTheme.typography.labelSmall.copy(
                    textDecoration = TextDecoration.Underline
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, item.url.toUri())
                    context.startActivity(intent)
                }
            )
        }
    }
}
