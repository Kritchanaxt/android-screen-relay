package com.example.android_screen_relay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    val logs = LogRepository.logs
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when logs change
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { LogRepository.clearLogs() }) {
                        Icon(Icons.Filled.Delete, "Clear", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FA)) // Light theme for readability
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Description, 
                            null, 
                            tint = Color.LightGray, 
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No logs available yet", color = Color.Gray)
                        Text(
                            "Start broadcasting to see activity", 
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(logs) { log ->
                        LogCard(log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogCard(log: LogRepository.LogEntry) {
    val (icon, color, bg) = when (log.type) {
        LogRepository.LogType.INFO -> Triple(Icons.Filled.Info, Color(0xFF2196F3), Color(0xFFE3F2FD))
        LogRepository.LogType.ERROR -> Triple(Icons.Filled.Error, Color(0xFFF44336), Color(0xFFFFEBEE))
        LogRepository.LogType.OUTGOING -> Triple(Icons.Filled.ArrowUpward, Color(0xFF4CAF50), Color(0xFFE8F5E9))
        LogRepository.LogType.INCOMING -> Triple(Icons.Filled.ArrowDownward, Color(0xFFFF9800), Color(0xFFFFF3E0))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(bg, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                 Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = LogRepository.getFormattedTimestamp(log.timestamp),
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = log.message,
                    color = Color(0xFF333333),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 10
                )
            }
        }
    }
}
