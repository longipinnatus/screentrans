package com.longipinnatus.screentrans

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.longipinnatus.screentrans.ui.theme.DebugPurple
import com.longipinnatus.screentrans.ui.theme.ErrorRed
import com.longipinnatus.screentrans.ui.theme.InfoBlue
import com.longipinnatus.screentrans.ui.theme.ScreenTransAITheme
import com.longipinnatus.screentrans.ui.theme.SuccessGreen
import com.longipinnatus.screentrans.ui.theme.VerboseGrey
import com.longipinnatus.screentrans.ui.theme.WarningOrange

class LogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScreenTransAITheme {
                LogScreen { finish() }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LogScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        var logs by remember { mutableStateOf(LogManager.getLogs()) }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(LogManager.exportLogsToString().toByteArray())
                    }
                    Toast.makeText(context, "Logs exported successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("LogActivity", "Failed to export logs", e)
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        DisposableEffect(Unit) {
            LogManager.setUpdateListener {
                logs = LogManager.getLogs()
            }
            onDispose {
                LogManager.setUpdateListener(null)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Logs") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
                                exportLauncher.launch("ScreenTransAI_Logs_$timestamp.log")
                            },
                            enabled = logs.isNotEmpty()
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Export Logs")
                        }
                        IconButton(onClick = { LogManager.clear() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                        }
                    }
                )
            }
        ) { innerPadding ->
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No logs yet", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { item ->
                        LogItemRow(item)
                    }
                }
            }
        }
    }

    @Composable
    fun LogItemRow(item: LogItem) {
        val tagColor = when (item.type) {
            LogType.REQUEST -> InfoBlue
            LogType.RESPONSE -> SuccessGreen
            LogType.ERROR -> ErrorRed
            LogType.WARNING -> WarningOrange
            LogType.INFO -> InfoBlue
            LogType.DEBUG -> DebugPurple
            LogType.VERBOSE -> VerboseGrey
        }

        Card(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = tagColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.type.label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = item.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )

                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                item.entries.forEach { entry ->
                    Spacer(modifier = Modifier.height(6.dp))
                    LogDataBox(label = entry.label, text = entry.value)
                }
            }
        }
    }

    @Composable
    fun LogDataBox(label: String, text: String) {
        val context = LocalContext.current
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("log_data", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}
