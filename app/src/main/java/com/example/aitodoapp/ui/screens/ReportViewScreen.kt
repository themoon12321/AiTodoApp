package com.example.aitodoapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.example.aitodoapp.model.ReportEntry

/**
 * 全屏播报查看页面。覆盖在 TaskScreen 之上。
 * 展示最新播报全文 + 历史记录列表。
 */
@Composable
fun ReportViewScreen(
    reports: List<ReportEntry>,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 80.dp, bottom = 40.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { /* 阻止点击穿透 */ }
                .padding(20.dp)
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("📋 播报记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(8.dp))

            if (reports.isEmpty()) {
                Text("暂无播报记录", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 40.dp).align(Alignment.CenterHorizontally))
            } else {
                // 日期选择器（横向滚动，显示最近的播报）
                val uniqueDates = reports.map { it.date }.distinct()
                if (uniqueDates.size > 1) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        uniqueDates.take(10).forEachIndexed { idx, date ->
                            val isSelected = date == reports.getOrNull(selected)?.date
                            Text(
                                date,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        val idx2 = reports.indexOfFirst { it.date == date }
                                        if (idx2 >= 0) selected = idx2
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                }

                // 当前选中的播报
                val report = reports.getOrNull(selected) ?: return@Column
                Text(report.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (report.isMorning) Color(0xFFFF8F00) else Color(0xFF5C6BC0))
                Spacer(Modifier.height(4.dp))
                Text(report.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // 正文（可滚动）
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    renderReportContent(report.content)
                }

                // 历史记录导航
                if (reports.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { if (selected < reports.size - 1) selected++ },
                            enabled = selected < reports.size - 1
                        ) { Text("← 更早", fontSize = 12.sp) }
                        Text("${selected + 1}/${reports.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically))
                        TextButton(
                            onClick = { if (selected > 0) selected-- },
                            enabled = selected > 0
                        ) { Text("更新 →", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

/** 渲染播报内容的 markdown 风格文本 */
@Composable
private fun renderReportContent(text: String) {
    val lines = text.lines()
    for ((i, line) in lines.withIndex()) {
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> Spacer(Modifier.height(6.dp))

            trimmed.matches(Regex("^-{3,}$")) -> {
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            // List item: starts with - or *
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val content = trimmed.substring(1).trim()
                Row(Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                    Text("•  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                    Text(content.replace("**", ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                }
            }

            // Blockquote
            trimmed.startsWith("> ") -> {
                val content = trimmed.substring(1).trim()
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(content.replace("**", ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                }
            }

            // Section header (starts with emoji or bold markers)
            trimmed.matches(Regex("^[📋🔥💡🌟💬🌅🌙📌⏰✅📊💌].*")) -> {
                Text(trimmed.replace("**", ""), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            }

            // Regular text (could be header-like without emoji)
            else -> {
                Text(trimmed.replace("**", ""), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
            }
        }
    }
}
