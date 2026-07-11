package com.example.aitodoapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
 * 操作日志页。按 traceId 分组展示 AI 对话链路，单条日志按时间倒序。
 * 点击单条查看详情，点击分组标题展开/折叠整组。
 */
@Composable
fun LogScreen(onDismiss: () -> Unit) {
    var logs by remember { mutableStateOf(ActionLogRepository.load()) }
    var selectedLog by remember { mutableStateOf<ActionLog?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 按 traceId 分组：有 traceId 的归入组，没有的单条展示
    val (grouped, standalone) = remember(logs) {
        val withTrace = logs.filter { it.traceId.isNotBlank() }
        val withoutTrace = logs.filter { it.traceId.isBlank() }
        val groups = withTrace.groupBy { it.traceId }.map { (traceId, entries) ->
            TraceGroup(traceId, entries.sortedByDescending { it.timestamp })
        }.sortedByDescending { it.latestTime }  // 最新组在最前
        // 单条的排序：在分组之后，但每个单条按时间插入
        val standalone = withoutTrace.sortedByDescending { it.timestamp }
        Pair(groups, standalone)
    }

    // 合并展示列表：分组穿插单条（按最新时间）
    val displayItems = remember(grouped, standalone) {
        mergeGroupedAndStandalone(grouped, standalone)
    }

    // 追踪各组的展开/折叠状态
    var expandedTraces by remember { mutableStateOf(setOf<String>()) }
    val toggleTrace: (String) -> Unit = { id ->
        expandedTraces = if (id in expandedTraces) expandedTraces - id else expandedTraces + id
    }

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
                items(displayItems, key = { it.key }) { item ->
                    when (item) {
                        is DisplayItem.Group -> {
                            val expanded = item.traceId in expandedTraces
                            TraceGroupHeader(
                                group = item.group,
                                expanded = expanded,
                                onToggle = { toggleTrace(item.traceId) }
                            )
                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    item.group.entries.forEach { log ->
                                        LogItem(log) { selectedLog = log }
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                        is DisplayItem.Single -> {
                            LogItem(item.log) { selectedLog = item.log }
                            HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
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
                    if (log.traceId.isNotBlank()) {
                        Text("追踪：${log.traceId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
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

// ===== 分组数据模型 =====

private data class TraceGroup(
    val traceId: String,
    val entries: List<ActionLog>
) {
    val latestTime get() = entries.firstOrNull()?.timestamp
}

private sealed class DisplayItem {
    abstract val key: String
    data class Group(val traceId: String, val group: TraceGroup) : DisplayItem() {
        override val key get() = "trace_$traceId"
    }
    data class Single(val log: ActionLog) : DisplayItem() {
        override val key get() = log.id
    }
}

/** 将分组和单条按最新时间交错合并 */
private fun mergeGroupedAndStandalone(
    groups: List<TraceGroup>,
    standalone: List<ActionLog>
): List<DisplayItem> {
    val result = mutableListOf<DisplayItem>()
    var gi = 0
    var si = 0
    while (gi < groups.size || si < standalone.size) {
        val gTime = groups.getOrNull(gi)?.latestTime
        val sTime = standalone.getOrNull(si)?.timestamp
        when {
            gTime != null && (sTime == null || gTime >= sTime) -> {
                result.add(DisplayItem.Group(groups[gi].traceId, groups[gi]))
                gi++
            }
            else -> {
                result.add(DisplayItem.Single(standalone[si]))
                si++
            }
        }
    }
    return result
}

// ===== UI 组件 =====

/** AI 对话分组标题行 */
@Composable
private fun TraceGroupHeader(
    group: TraceGroup,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val firstEntry = group.entries.firstOrNull()
    val timeStr = firstEntry?.time ?: ""
    val entryTypes = group.entries.map { it.type }.toSet()
    val hasInput = LogType.AI_INPUT in entryTypes
    val label = if (hasInput) "🤖 AI 对话" else "🔄 操作链"
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (expanded) "▼" else "▶", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("共${group.entries.size}条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 单条日志行 */
@Composable
private fun LogItem(log: ActionLog, onClick: () -> Unit) {
    val sourceColor = when (log.source) {
        "AI" -> MaterialTheme.colorScheme.primary
        "SYSTEM" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
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
