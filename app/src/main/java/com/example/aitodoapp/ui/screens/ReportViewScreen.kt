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

@Composable
fun ReportViewScreen(reports: List<ReportEntry>, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部导航
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", fontSize = 20.sp, modifier = Modifier.clickable(onClick = onDismiss))
            Spacer(Modifier.width(12.dp))
            Text("📋 播报记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (reports.isNotEmpty()) Text("${selected + 1}/${reports.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无播报记录", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            val report = reports.getOrNull(selected) ?: return

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                // 标题
                Text(report.title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = if (report.isMorning) Color(0xFFFF8F00) else Color(0xFF5C6BC0))
                Text(report.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))

                val lines = report.content.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() -> Spacer(Modifier.height(4.dp))

                        // 分隔线 ---
                        trimmed.matches(Regex("^-{3,}$")) -> {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }

                        // 💬 暖心问候段（最小字）
                        trimmed.startsWith("💬") -> {
                            Text(trimmed, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }

                        // 🌟 温馨提示（稍小字）
                        trimmed.startsWith("🌟") -> {
                            Text(trimmed, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp, modifier = Modifier.padding(vertical = 3.dp))
                        }

                        // 📌 / ⏰ 小贴士内部项
                        trimmed.matches(Regex("^[-]?\\s*[📌⏰]")) -> {
                            val clean = trimmed.removePrefix("-").trim()
                            Text(clean, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp))
                        }

                        // Section header：以 emoji 开头（📋🔥💡📊🗓️💌等）
                        trimmed.matches(Regex("^[📋🔥💡📊🗓️💌📈🎯💎🔔⚡✅].*")) -> {
                            Text(trimmed, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        }

                        // 任务行：以 - 开头，含 | 分隔符
                        trimmed.startsWith("- ") && trimmed.contains("|") -> {
                            renderTaskLine(trimmed)
                        }

                        // 一般列表项
                        trimmed.startsWith("- ") || trimmed.startsWith("● ") || trimmed.startsWith("• ") -> {
                            Text(trimmed.removePrefix("- ").removePrefix("● ").removePrefix("• "), fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp))
                        }

                        // 引用块
                        trimmed.startsWith("> ") -> {
                            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(trimmed.substring(1).trim(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                            }
                        }

                        // 🌅🌙 标题行
                        trimmed.matches(Regex("^[🌅🌙].*代办报告.*")) || trimmed.matches(Regex("^[🌅🌙].*")) -> {
                            Text(trimmed, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                color = if (report.isMorning) Color(0xFFFF8F00) else Color(0xFF5C6BC0),
                                modifier = Modifier.padding(vertical = 2.dp))
                        }

                        // 普通正文
                        else -> {
                            Text(trimmed, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp, modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
            }

            // 底部翻页
            if (reports.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (selected < reports.size - 1) selected++ }, enabled = selected < reports.size - 1) { Text("← 更早", fontSize = 13.sp) }
                    Text(report.title + " · " + report.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { if (selected > 0) selected-- }, enabled = selected > 0) { Text("更新 →", fontSize = 13.sp) }
                }
            }
        }
    }
}

/** 渲染单行任务：- 任务名称 | P0🔥 | 截止今天 | 2h */
@Composable
private fun renderTaskLine(line: String) {
    val content = line.removePrefix("- ").trim()
    val parts = content.split("|").map { it.trim() }.filter { it.isNotBlank() }
    if (parts.isEmpty()) return

    val title = parts[0]
    val detailParts = parts.drop(1)

    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 3.dp, bottom = 3.dp)) {
        val remaining = mutableListOf<String>()
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 提取优先级 emoji
            var priorityEmoji = ""
            for (p in detailParts) {
                val matched = Regex("P[0-4]").find(p)
                if (matched != null) {
                    priorityEmoji = when (matched.value) {
                        "P0" -> "🔥"; "P1" -> "🔴"; "P2" -> "🟡"; "P3" -> "🟢"; else -> "⚪"
                    }
                    val rest = p.replace(matched.value, "").replace("🔥", "").replace("🔴", "").replace("🟡", "").replace("🟢", "").replace("⚪", "").trim()
                    if (rest.isNotBlank()) remaining.add(rest.removePrefix(":").trim().removePrefix(":").trim())
                } else {
                    remaining.add(p)
                }
            }
            if (priorityEmoji.isNotEmpty()) { Text(priorityEmoji, fontSize = 15.sp); Spacer(Modifier.width(4.dp)) }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        if (remaining.isNotEmpty()) {
            Text(remaining.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp, start = if (Regex("P[0-4]").find(detailParts.firstOrNull() ?: "") != null) 22.dp else 0.dp))
        }
    }
}
