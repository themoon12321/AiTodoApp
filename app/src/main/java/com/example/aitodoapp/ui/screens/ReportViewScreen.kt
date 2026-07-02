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
 * 全屏播报查看页。匹配用户模板风格：
 * - 任务名称单独一行（16sp Bold）
 * - 属性行以"- "开头（优先级emoji+截止+时长）
 * - section标题18sp Bold
 * - 温暖紧凑的间距
 */
@Composable
fun ReportViewScreen(
    reports: List<ReportEntry>,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部导航
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("←", fontSize = 20.sp, modifier = Modifier.clickable(onClick = onDismiss))
            Spacer(Modifier.width(12.dp))
            Text("📋 播报记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (reports.isNotEmpty()) Text("${selected + 1}/${reports.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无播报记录", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val report = reports.getOrNull(selected) ?: return
            var prevLineWasTaskTitle = false  // 跟踪上一行是否是任务标题

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                // 标题
                Text(report.title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = if (report.isMorning) Color(0xFFFF8F00) else Color(0xFF5C6BC0))
                Text(report.date, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(16.dp))

                // 逐行解析渲染
                val lines = report.content.lines()
                for ((i, line) in lines.withIndex()) {
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() -> Spacer(Modifier.height(6.dp))

                        // 分隔线
                        trimmed.matches(Regex("^-{3,}$")) -> {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            prevLineWasTaskTitle = false
                        }

                        // 属性行：以 "- " 开头（任务的属性行）
                        trimmed.startsWith("- ") -> {
                            renderAttrLine(trimmed.substring(1).trim())
                            prevLineWasTaskTitle = false
                        }

                        // Section header：以 emoji 开头
                        trimmed.matches(Regex("^[📋🔥💡🌟💬🌅🌙📌⏰✅📊💌📝💪🎯⚡🗓️📈💎🔔].*")) -> {
                            val text = trimmed.replace("**", "")
                            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                            prevLineWasTaskTitle = false
                        }

                        // 列表项（其他）
                        trimmed.startsWith("● ") || trimmed.startsWith("• ") -> {
                            Text(trimmed.removePrefix("● ").removePrefix("• "), fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp))
                            prevLineWasTaskTitle = false
                        }

                        // 引用块
                        trimmed.startsWith("> ") -> {
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(trimmed.substring(1).trim().replace("**", ""), fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                            }
                            prevLineWasTaskTitle = false
                        }

                        // 普通文本 → 可能是任务标题或段落正文
                        else -> {
                            val cleaned = trimmed.replace("**", "")
                            // 判断是否为任务标题：前一行是属性行且本行较短（<25字）且不含标点句号
                            val looksLikeTaskTitle = !prevLineWasTaskTitle && cleaned.length < 30 && !cleaned.endsWith("。") && !cleaned.endsWith("！") && cleaned.isNotBlank()
                            if (looksLikeTaskTitle) {
                                Text(cleaned, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                                prevLineWasTaskTitle = true
                            } else {
                                Text(cleaned, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp, modifier = Modifier.padding(vertical = 2.dp))
                                prevLineWasTaskTitle = false
                            }
                        }
                    }
                }
            }

            // 底部翻页
            if (reports.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (selected < reports.size - 1) selected++ }, enabled = selected < reports.size - 1) { Text("← 更早", fontSize = 14.sp) }
                    Text(report.title + " · " + report.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { if (selected > 0) selected-- }, enabled = selected > 0) { Text("更新 →", fontSize = 14.sp) }
                }
            }
        }
    }
}

/** 渲染任务属性行：- 优先级: P0🔥, 截止日期: 今天7月3日, 预估时长: 2h */
@Composable
private fun renderAttrLine(line: String) {
    val parts = line.split(",").map { it.trim() }
    val emojiColors = mapOf(
        "P0" to Color(0xFFE53935), "P1" to Color(0xFFFB8C00),
        "P2" to Color(0xFFFDD835), "P3" to Color(0xFF43A047), "P4" to Color(0xFF9E9E9E)
    )
    Row(Modifier.padding(start = 8.dp, top = 1.dp, bottom = 4.dp)) {
        var priorityEmoji = ""
        var otherParts = mutableListOf<String>()

        for (part in parts) {
            when {
                part.contains("优先级", ignoreCase = true) || part.matches(Regex("^P[0-4].*")) -> {
                    val raw = part.substringAfter(":").trim()
                    // Extract P0-P4 from any position
                    val pMatch = Regex("P[0-4]").find(raw)
                    if (pMatch != null) {
                        priorityEmoji = when (pMatch.value) {
                            "P0" -> "🔥"; "P1" -> "🔴"; "P2" -> "🟡"; "P3" -> "🟢"; else -> "⚪"
                        }
                    }
                }
                part.contains("截止", ignoreCase = true) || part.contains("日期", ignoreCase = true) -> {
                    otherParts.add("📅 " + part.substringAfter(":").trim())
                }
                part.contains("时长", ignoreCase = true) || part.contains("预估") -> {
                    otherParts.add("⏱" + part.substringAfter(":").trim())
                }
                part.contains("状态", ignoreCase = true) -> {
                    otherParts.add(part.substringAfter(":").trim())
                }
                part.isNotBlank() -> {
                    otherParts.add(part)
                }
            }
        }

        if (priorityEmoji.isNotEmpty()) {
            Text(priorityEmoji, fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
        }
        Text(otherParts.joinToString("  "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
