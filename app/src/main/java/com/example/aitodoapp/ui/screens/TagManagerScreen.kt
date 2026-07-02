package com.example.aitodoapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.example.aitodoapp.ui.components.TagChip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.Tag
import com.example.aitodoapp.Task
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagerScreen(allTags: List<Tag>, allTasks: List<Task>, onCreate: (String) -> Unit, onPromote: (String) -> Unit, onDelete: (String) -> Unit, modifier: Modifier) {
    var newTagName by remember { mutableStateOf("") }; var selectedTag by remember { mutableStateOf("") }
    val formalTags = allTags.filter { !it.isTemporary }; val tempTags = allTags.filter { it.isTemporary }
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("标签管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("创建标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newTagName, onValueChange = { newTagName = it }, placeholder = { Text("输入标签名") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.width(8.dp)); Button({ onCreate(newTagName.trim()); newTagName = "" }, shape = RoundedCornerShape(12.dp)) { Text("创建") }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { formalTags.forEach { val cnt = allTasks.count { t -> t.tags.contains(it.name) }; TagChip(it.name + " (" + cnt.toString() + ")", false, onDelete = { onDelete(it.name) }, onClick = { selectedTag = it.name }) }; if (formalTags.isEmpty()) Text("还没有正式标签", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("临时标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Text("(${tempTags.size})", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp)); Text("添加任务时自动收录，点击箭头转为正式标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (tempTags.isEmpty()) Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) { Text("没有临时标签", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) { items(tempTags) { tag -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${tag.name}", modifier = Modifier.weight(1f))
            OutlinedButton({ onPromote(tag.name) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) { Text("→ 转为正式", fontSize = 12.sp) }
            Spacer(Modifier.width(8.dp)); Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).padding(4.dp).clickable { onDelete(tag.name) }) } } }
    }
        if (selectedTag.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { selectedTag = "" }.padding(32.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp)) {
                    Text("🏷 " + selectedTag, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    val taggedTasks = allTasks.filter { it.tags.contains(selectedTag) }
                    if (taggedTasks.isEmpty()) {
                        Text("没有任务使用此标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("共 " + taggedTasks.size.toString() + " 个任务", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                            taggedTasks.forEach { task ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (task.isCompleted) "✅" else "⬜", fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None)
                                        Text(if (task.deadline != null) DateTimeFormatter.ofPattern("M月d日").format(task.deadline) else "无截止", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { selectedTag = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("关闭") }
                }
            }
        }
}