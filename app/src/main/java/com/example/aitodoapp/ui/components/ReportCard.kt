package com.example.aitodoapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 可折叠的早晚间播报卡片。放在任务列表顶部。
 * @param reportText AI 生成的播报原文（按行解析渲染）
 * @param isLoading 是否正在生成中
 * @param isMorning true=早间 false=晚间
 * @param onGenerate 点击生成按钮的回调
 */
@Composable
fun ReportCard(
    reportText: String,
    isLoading: Boolean,
    isMorning: Boolean,
    onGenerate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasReport = reportText.isNotBlank()
    val headerEmoji = if (isMorning) "🌅" else "🌙"
    val headerTitle = if (isMorning) "早间播报" else "晚间播报"
    val accentColor = if (isMorning) Color(0xFFFF8F00) else Color(0xFF5C6BC0)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (hasReport) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        // Header row - always visible
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { if (hasReport) expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(headerEmoji, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(headerTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasReport) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasReport && !expanded) {
                    // Show first line as preview
                    val preview = reportText.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("```") }
                        ?: "点击查看详情"
                    Text(preview.take(40),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            if (hasReport) {
                Text(if (expanded) "▲" else "▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Generate button (shown only when no report)
        if (!hasReport && !isLoading) {
            Button(
                onClick = onGenerate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("${headerEmoji} 生成$headerTitle", fontSize = 13.sp)
            }
        }

        // Loading indicator
        if (isLoading) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(6.dp))
                Text("正在生成${headerTitle}...", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Expanded content
        AnimatedVisibility(
            visible = hasReport && expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                renderMarkdown(reportText)
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onGenerate,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("🔄 重新生成", fontSize = 12.sp, color = accentColor)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * 简单的播报文本渲染器。
 * 按行解析，识别 section header / 列表项 / 分隔线 / 引用 / 普通文本。
 */
@Composable
private fun renderMarkdown(text: String) {
    val lines = text.lines()
    for ((index, line) in lines.withIndex()) {
        val trimmed = line.trim()
        when {
            // Empty line → spacing
            trimmed.isEmpty() -> Spacer(Modifier.height(4.dp))

            // Separator: ---
            trimmed.matches(Regex("^-{3,}$")) -> {
                HorizontalDivider(
                    Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }

            // Section header: 🌅 📋 🔥 💡 💬 etc. (starts with emoji, no leading dash)
            trimmed.matches(Regex("^[\\u{1F300}-\\u{1FAFF}\\u2600-\\u27BF\\u{2000}-\\u{2069}]|^\\*{1,2}")) -> {
                val text = trimmed.removePrefix("**").removeSuffix("**").trim()
                Text(
                    text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            // Bold header: **text**
            trimmed.matches(Regex("^\\*\\*.+\\*\\*$")) -> {
                val text = trimmed.removePrefix("**").removeSuffix("**")
                Text(
                    text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            // List item: starts with - or * followed by space
            trimmed.matches(Regex("^[-*]\\s.*")) -> {
                val content = trimmed.substring(1).trim()
                Row(Modifier.padding(start = 4.dp, top = 1.dp, bottom = 1.dp)) {
                    Text("•  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        parseBold(content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }

            // Blockquote: starts with >
            trimmed.matches(Regex("^>\\s.*")) -> {
                val content = trimmed.substring(1).trim()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        parseBold(content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            // Regular text
            else -> {
                Text(
                    parseBold(trimmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/** 简单解析 **bold** 标记为粗体（目前直接返回，后续可扩展为 AnnotatedString） */
private fun parseBold(text: String): String = text.replace("**", "")
