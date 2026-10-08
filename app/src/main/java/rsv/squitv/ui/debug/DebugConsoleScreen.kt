package rsv.squitv.ui.debug

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import rsv.squitv.core.debug.DebugEvent
import rsv.squitv.core.debug.DebugLogger
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var events by remember { mutableStateOf(DebugLogger.getRecentEvents()) }
    var filterLevel by remember { mutableStateOf("ALL") }
    var filterCategory by remember { mutableStateOf("ALL") }
    var searchText by remember { mutableStateOf("") }

    val snapshot = remember(events) { DebugLogger.takeSnapshot("CONSOLE_INSPECT") }

    LaunchedEffect(Unit) {
        while (true) {
            events = DebugLogger.getRecentEvents()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val filteredEvents = remember(events, filterLevel, filterCategory, searchText) {
        events.filter { event ->
            (filterLevel == "ALL" || event.level == filterLevel) &&
            (filterCategory == "ALL" || event.category == filterCategory) &&
            (searchText.isBlank() || event.event.contains(searchText, ignoreCase = true) ||
             (event.context?.toString() ?: "").contains(searchText, ignoreCase = true) ||
             (event.error ?: "").contains(searchText, ignoreCase = true))
        }.reversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SquiTV Diagnóstico & Telemetria", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Session: ${DebugLogger.sessionId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        DebugLogger.takeSnapshot("USER_MANUAL_SNAPSHOT")
                        events = DebugLogger.getRecentEvents()
                    }) {
                        Text("Snapshot")
                    }
                    TextButton(onClick = {
                        DebugLogger.clearBuffer()
                        events = emptyList()
                    }) {
                        Text("Limpar")
                    }
                    TextButton(onClick = {
                        val logFile = DebugLogger.getLogFile()
                        if (logFile != null && logFile.exists()) {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    logFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Exportar Logs Debug"))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Erro ao exportar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Text("Exportar Logs")
                    }
                    TextButton(onClick = onNavigateBack) {
                        Text("Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Live Status Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ESTADO DO SISTEMA EM TEMPO REAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Activity: ${snapshot.currentActivity ?: "None"} | Rota: ${snapshot.currentRoute ?: "None"}", fontSize = 11.sp)
                    Text("Canal Ativo: ${snapshot.selectedChannelId ?: "Nenhum"} | Troca ID: ${snapshot.activeChannelSwitchId ?: "Nenhuma"}", fontSize = 11.sp)
                    Text("Player Instance: ${snapshot.playerInstanceId ?: "Nenhuma"} | Estado: ${snapshot.activePlayerState ?: "Nenhum"}", fontSize = 11.sp)
                    Text("Operações Ativas: ${snapshot.activeOperationsCount} | Memória Usada: ${snapshot.memoryUsedMb}MB / ${snapshot.maxMemoryMb}MB", fontSize = 11.sp)
                }
            }

            // Search Bar & Filter Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("Pesquisar evento...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // Log Feed
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredEvents) { e ->
                    LogItemRow(e)
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(e: DebugEvent) {
    val levelColor = when (e.level) {
        "TRACE" -> Color.Gray
        "DEBUG" -> Color(0xFF81D4FA)
        "INFO" -> Color(0xFFA5D6A7)
        "WARN" -> Color(0xFFFFE082)
        "ERROR", "FATAL" -> Color(0xFFEF9A9A)
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "[${e.level}][${e.category}] ${e.event}",
                color = levelColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = e.timestamp.takeLast(12),
                color = Color.LightGray,
                fontSize = 10.sp
            )
        }

        val details = StringBuilder()
        if (e.channelSwitchId != null) details.append("cs=${e.channelSwitchId} ")
        if (e.operationId != null) details.append("op=${e.operationId} ")
        if (e.channelId != null) details.append("channel=${e.channelId} ")
        if (e.durationMs != null) details.append("dur=${e.durationMs}ms ")
        if (e.result != null) details.append("res=${e.result} ")

        if (details.isNotEmpty()) {
            Text(
                text = details.toString(),
                color = Color(0xFFBBDEFB),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (e.context != null && e.context.isNotEmpty()) {
            Text(
                text = "ctx: ${e.context}",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (e.error != null) {
            Text(
                text = "ERROR: ${e.error}",
                color = Color(0xFFFF8A80),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
