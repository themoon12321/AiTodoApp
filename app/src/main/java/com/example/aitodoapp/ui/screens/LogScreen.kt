package com.example.aitodoapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.data.ActionLog
import com.example.aitodoapp.data.ActionLogRepository
import com.example.aitodoapp.data.LogType

/**
 * 操作日志页。时间轴倒序（最新在上），点击单条查看详情。
 * 来源用颜色区分：AI(蓝) / MANUAL(默认) / SYSTEM(灰)。
 */
@Composable
fun LogScreen(onDismiss: () -> Unit) {
    var logs by remember { mutableStateOf(ActionLogRepository.load()) }
    var selectedLog by remember { mutableStateOf<ActionLog?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 顶部栏
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("操作日志", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${logs.size}条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showClearConfirm = true }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss) { Text("关闭") }
        }

        if (logs.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无日志记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(logs, key = { it.id }) { log ->
                    LogItem(log) { selectedLog = log }
                    HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }

    // 详情弹窗
    selectedLog?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLog = null },
            title = { Text("${LogType.emoji(log.type)} ${log.time}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("来源：${log.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("摘要", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                    if (log.detail.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("详情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(log.detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedLog = null }) { Text("关闭") } }
        )
    }

    // 清空确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空所有日志？", fontWeight = FontWeight.Bold) },
            text = { Text("将删除全部 ${logs.size} 条日志记录，不可恢复。") },
            confirmButton = {
                Button(onClick = {
                    ActionLogRepository.clearAll()
                    logs = emptyList()
                    showClearConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun LogItem(log: ActionLog, onClick: () -> Unit) {
    val sourceColor = when (log.source) {
        "AI" -> MaterialTheme.colorScheme.primary
        "SYSTEM" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(LogType.emoji(log.type), fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(log.summary, style = MaterialTheme.typography.bodyMedium, color = sourceColor, maxLines = 2)
                Text(log.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            if (log.detail.isNotBlank()) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}
