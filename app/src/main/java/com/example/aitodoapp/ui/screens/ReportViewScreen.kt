package com.example.aitodoapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.Priority
import com.example.aitodoapp.model.ReportEntry

/**
 * 全屏播报查看页。覆盖整个屏幕，可上下滑动查看全文 + 历史翻页。
 */
@Composable
fun ReportViewScreen(
    reports: List<ReportEntry>,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ──── 顶部导航栏 ────
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("←", fontSize = 20.sp, modifier = Modifier.clickable(onClick = onDismiss))
            Spacer(Modifier.width(12.dp))
            Text("📋 播报记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${selected + 1}/${reports.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无播报记录", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val report = reports.getOrNull(selected) ?: return

            // ──── 正文区域（可滚动） ────
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // 标题行
                Text(
                    report.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (report.isMorning) Color(0xFFFF8F00) else Color(0xFF5C6BC0)
                )
                Text(
                    report.date,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(16.dp))

                // 渲染正文
                renderReportContent(report.content)
            }

            // ──── 底部翻页 ────
            if (reports.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (selected < reports.size - 1) selected++ },
                        enabled = selected < reports.size - 1
                    ) { Text("← 更早", fontSize = 14.sp) }
                    Text(
                        report.title + " · " + report.date,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { if (selected > 0) selected-- },
                        enabled = selected > 0
                    ) { Text("更新 →", fontSize = 14.sp) }
                }
            }
        }
    }
}

/** 渲染播报正文，智能识别任务行、section、优先级 */
@Composable
private fun renderReportContent(text: String) {
    val lines = text.lines()
    for ((i, line) in lines.withIndex()) {
        val trimmed = line.trim()
        when {
            // 空行
            trimmed.isEmpty() -> Spacer(Modifier.height(8.dp))

            // 分隔线 ---
            trimmed.matches(Regex("^-{3,}$")) -> {
                HorizontalDivider(
                    Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }

            // 任务行：以 - 开头，包含 | 分隔符（AI 生成的格式）
            trimmed.matches(Regex("^-\\s.*\\|.*")) -> {
                renderTaskLine(trimmed)
            }

            // 列表项：以 - 或 * 开头（但不含 | ）
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val content = trimmed.substring(1).trim()
                Row(Modifier.padding(start = 4.dp, top = 3.dp, bottom = 3.dp)) {
                    Text("•  ", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                    Text(
                        content.replace("**", ""),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
            }

            // 引用块 >
            trimmed.startsWith("> ") -> {
                val content = trimmed.substring(1).trim()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        content.replace("**", ""),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }

            // Section header：以常见 emoji 开头
            trimmed.matches(Regex("^[📋🔥💡🌟💬🌅🌙📌⏰✅📊💌📝💪🎯⚡].*")) -> {
                Text(
                    trimmed.replace("**", ""),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
            }

            // 普通文本
            else -> {
                Text(
                    trimmed.replace("**", ""),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

/** 解析 AI 生成的任务行：- 标题 | 截止: X | 优先级: PX | 标签: X */
@Composable
private fun renderTaskLine(line: String) {
    // 去掉开头的 "- "
    val content = line.substring(1).trim()
    val parts = content.split("|").map { it.trim() }
    if (parts.isEmpty()) return

    val title = parts[0].replace("**", "")
    // 解析 deadline, priority, tags
    var deadlineText = ""
    var priority: Priority? = null
    var tagsText = ""

    for (part in parts.drop(1)) {
        when {
            part.startsWith("截止", ignoreCase = true) || part.startsWith("deadline", ignoreCase = true) -> {
                deadlineText = part.substringAfter(":").trim().removePrefix(" ").removePrefix(":")
            }
            part.startsWith("优先级", ignoreCase = true) || part.startsWith("priority", ignoreCase = true) -> {
                val raw = part.substringAfter(":").trim()
                try {
                    priority = Priority.valueOf(raw.trim().take(2))
                } catch (_: Exception) {
                    // Keep null if parse fails
                }
            }
            part.startsWith("标签", ignoreCase = true) || part.startsWith("tag", ignoreCase = true) -> {
                tagsText = part.substringAfter(":").trim()
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 优先级 emoji
            if (priority != null) {
                Text(priority!!.emoji, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Row {
            if (deadlineText.isNotBlank()) {
                Text("📅 $deadlineText", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tagsText.isNotBlank()) {
                if (deadlineText.isNotBlank()) Text("  ", fontSize = 13.sp)
                Text("🏷 $tagsText", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
