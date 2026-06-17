package com.example.aitodoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.aitodoapp.data.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.data.LocalDateSerializer
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.data.TaskRepository
import com.example.aitodoapp.ui.theme.AiTodoAppTheme
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

// ============ 数据模型 ============

@Serializable
enum class Priority(val emoji: String, val label: String, val color: Color) {
    P0("🔥", "紧急", Color(0xFFE53935)),
    P1("🔴", "高", Color(0xFFFB8C00)),
    P2("🟡", "中", Color(0xFFFDD835)),
    P3("🟢", "普通", Color(0xFF43A047)),
    P4("⚪", "低", Color(0xFF9E9E9E));
    companion object { fun fromString(s: String): Priority = entries.find { it.name == s } ?: P3 }
}

@Serializable
data class Tag(val name: String, val isTemporary: Boolean = true)

@Serializable
data class Task(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val priority: Priority = Priority.P3,
    val tags: List<String> = emptyList(),
    @Serializable(with = LocalDateSerializer::class) val deadline: LocalDate? = null,
    val isCompleted: Boolean = false, val isArchived: Boolean = false,
    @Serializable(with = LocalDateSerializer::class) val completedAt: LocalDate? = null
)

// ============ 主 Activity ============

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TaskRepository.init(applicationContext)
        SettingsRepository.init(applicationContext)
        enableEdgeToEdge()
        setContent { AiTodoAppTheme { AppMain() } }
    }
}

// ============ 主导航 ============

@Composable
fun AppMain() {
    val today = LocalDate.now()
    var tab by remember { mutableStateOf(0) }
    var tasks by remember { mutableStateOf(TaskRepository.load<Task>("tasks.json")) }
    var allTags by remember { mutableStateOf(TaskRepository.load<Tag>("tags.json")) }
    var loaded by remember { mutableStateOf(false) }

    fun saveAll() { TaskRepository.save("tasks.json", tasks); TaskRepository.save("tags.json", allTags) }

    LaunchedEffect(Unit) {
        if (!loaded) {
            loaded = true; var changed = false
            if (tasks.isEmpty()) {
                tasks = listOf(
                    Task("T-001", "交大物实验报告", Priority.P0, listOf("大物", "实验报告"), today),
                    Task("T-002", "洗衣服", Priority.P2, emptyList(), today.minusDays(1)),
                    Task("T-003", "看那篇文献", Priority.P3, listOf("文献"), today.plusDays(3)),
                    Task("T-004", "买数据线", Priority.P4, emptyList(), null),
                    Task("T-005", "机械建模作业", Priority.P1, listOf("建模"), today.plusDays(1)),
                ); changed = true
            } else {
                tasks = tasks.map { if (it.completedAt != null && it.completedAt < today && !it.isArchived) { changed = true; it.copy(isArchived = true) } else it }
            }
            if (allTags.isEmpty()) {
                allTags = listOf(Tag("大物", false), Tag("实验报告", false), Tag("文献", false), Tag("建模", false), Tag("英语", false), Tag("高数", false))
                changed = true
            }
            if (changed) saveAll()
        }
    }

    fun completeTask(id: String) {
        tasks = tasks.map { if (it.id == id) { if (it.isCompleted) it.copy(isCompleted = false, completedAt = null) else it.copy(isCompleted = true, completedAt = today) } else it }
            .let { it.filter { !it.isCompleted } + it.filter { it.isCompleted } }; saveAll()
    }
    fun deleteTask(id: String) { tasks = tasks.filter { it.id != id }; saveAll() }
    fun unarchiveTask(id: String) { tasks = tasks.map { if (it.id == id) it.copy(isArchived = false, isCompleted = false) else it }; saveAll() }
    fun addTag(n: String): String { val t = n.trim(); if (t.isBlank() || allTags.any { it.name == t }) return t; allTags = allTags + Tag(t, true); saveAll(); return t }
    fun createTag(n: String) { val t = n.trim(); if (t.isNotBlank() && allTags.none { it.name == t }) { allTags = allTags + Tag(t, false); saveAll() } }
    fun promoteTag(n: String) { allTags = allTags.map { if (it.name == n) it.copy(isTemporary = false) else it }; saveAll() }
    fun deleteTag(n: String) { allTags = allTags.filter { it.name != n }; tasks = tasks.map { it.copy(tags = it.tags.filter { t -> t != n }) }; saveAll() }
    fun addTask(title: String, pri: Priority, dl: LocalDate?, tags: List<String>) {
        tags.forEach { addTag(it) }; tasks = listOf(Task(title = title, priority = pri, tags = tags, deadline = dl)) + tasks; saveAll()
    }

    val activeTasks = tasks.filter { !it.isArchived }
    val archivedTasks = tasks.filter { it.isArchived }

    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📋", fontSize = 16.sp) }, label = { Text("任务 (${activeTasks.size})") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📦", fontSize = 16.sp) }, label = { Text("归档 (${archivedTasks.size})") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("🏷", fontSize = 16.sp) }, label = { Text("标签") })
            NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Text("⚙️", fontSize = 16.sp) }, label = { Text("设置") })
        }
    }) { innerPadding ->
        when (tab) {
            0 -> TaskScreen(activeTasks, allTags, ::completeTask, ::addTask, ::deleteTask, Modifier.padding(innerPadding), false)
            1 -> TaskScreen(archivedTasks, allTags, ::unarchiveTask, { _, _, _, _ -> }, ::deleteTask, Modifier.padding(innerPadding), true)
            2 -> TagManagerScreen(allTags, ::createTag, ::promoteTag, ::deleteTag, Modifier.padding(innerPadding))
            3 -> SettingsScreen(Modifier.padding(innerPadding))
        }
    }
}

// ============ 设置页 ============

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val s = remember { mutableStateOf(SettingsRepository.load()) }
    var apiUrl by remember { mutableStateOf(s.value.apiUrl) }
    var apiKey by remember { mutableStateOf(s.value.apiKey) }
    var model by remember { mutableStateOf(s.value.model) }
    var saved by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("API 设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("API 地址", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = apiUrl, onValueChange = { apiUrl = it; saved = false },
            placeholder = { Text("https://api.deepseek.com/chat/completions") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(16.dp))
        Text("API Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; saved = false },
            placeholder = { Text("sk-...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(16.dp))
        Text("模型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = model, onValueChange = { model = it; saved = false },
            placeholder = { Text("deepseek-v4-flash") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = { SettingsRepository.save(SettingsRepository.Settings(apiUrl.trim(), apiKey.trim(), model.trim())); saved = true },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("保存") }
        if (saved) { Spacer(Modifier.height(8.dp)); Text("✓ 已保存", color = MaterialTheme.colorScheme.primary) }
    }
}

// ============ 任务页 ============

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskScreen(tasks: List<Task>, allTags: List<Tag>, onComplete: (String) -> Unit, onAddTask: (String, Priority, LocalDate?, List<String>) -> Unit, onDelete: (String) -> Unit, modifier: Modifier, isArchive: Boolean) {
    var showInput by remember { mutableStateOf(false) }; var chatMode by remember { mutableStateOf(false) }; var chatInput by remember { mutableStateOf("") }; var deleteTarget by remember { mutableStateOf<String?>(null) }; var aiReply by remember { mutableStateOf("") }; var aiLoading by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DateHeader(LocalDate.now())
            if (tasks.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (isArchive) "还没有归档的任务" else "还没有任务", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(Modifier.fillMaxSize()) { items(tasks, key = { it.id }) { task -> TaskItem(task, isArchive, { onComplete(task.id) }, { deleteTarget = task.id }); HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) } }
        }
        if (!isArchive) Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).combinedClickable(onClick = { showInput = true }, onLongClick = { chatMode = true }), contentAlignment = Alignment.Center) {
            Text("+", fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        if (chatMode) {
            Box(Modifier.fillMaxSize().clickable { chatMode = false }.background(Color.Black.copy(alpha = 0.3f)))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(if (aiReply.isNotEmpty()) aiReply else "跟 AI 说你要做什么",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (aiReply.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, placeholder = { Text(if (aiLoading) "处理中..." else "记个事...") }, singleLine = true, enabled = !aiLoading,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val t = chatInput.trim()
                        if (t.isNotBlank() && !aiLoading) {
                            aiLoading = true; aiReply = ""
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val result = AiService.processMessage(t)
                                    scope.launch(Dispatchers.Main) {
                                        aiReply = result.text
                                        aiLoading = false
                                        result.actions.forEach { action ->
                                            if (action is com.example.aitodoapp.data.AiAction.CreateTask) {
                                                onAddTask(action.title, action.priority, action.deadline, action.tags)
                                            }
                                        }
                                        chatInput = ""
                                    }
                                } catch (e: Exception) {
                                    scope.launch(Dispatchers.Main) {
                                        aiReply = "出错了：${e.message}"
                                        aiLoading = false
                                    }
                                }
                            }
                        }
                    }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(52.dp), enabled = !aiLoading) { Text(if (aiLoading) "..." else "发送") }
                }
            }
        }
        if (showInput) AddTaskDialog(allTags, { showInput = false }) { t, p, d, tags -> onAddTask(t, p, d, tags); showInput = false }
        if (deleteTarget != null) AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("删除任务") }, text = { Text("确定要彻底删除这个任务吗？") },
            confirmButton = { TextButton({ onDelete(deleteTarget!!); deleteTarget = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("取消") } })
    }
}

// ============ 标签管理 ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagerScreen(allTags: List<Tag>, onCreate: (String) -> Unit, onPromote: (String) -> Unit, onDelete: (String) -> Unit, modifier: Modifier) {
    var newTagName by remember { mutableStateOf("") }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { formalTags.forEach { TagChip(it.name, false) { onDelete(it.name) } }; if (formalTags.isEmpty()) Text("还没有正式标签", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
}

// ============ 标签组件 ============

@Composable
fun TagChip(name: String, isTemporary: Boolean, onDelete: () -> Unit) {
    val bg = if (isTemporary) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    val fg = if (isTemporary) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onPrimaryContainer
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (isTemporary) "📌 " else "#", fontSize = 12.sp, color = fg); Text(name, style = MaterialTheme.typography.labelMedium, color = fg)
        Spacer(Modifier.width(6.dp)); Text("✕", fontSize = 12.sp, color = fg.copy(alpha = 0.5f), modifier = Modifier.clickable(onClick = onDelete))
    }
}

// ============ 添加任务弹窗 ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(allTags: List<Tag>, onDismiss: () -> Unit, onConfirm: (String, Priority, LocalDate?, List<String>) -> Unit) {
    var title by remember { mutableStateOf("") }; var selectedPriority by remember { mutableStateOf(Priority.P3) }
    var hasDeadline by remember { mutableStateOf(false) }; var deadlineDate by remember { mutableStateOf(LocalDate.now()) }
    var showPriorityMenu by remember { mutableStateOf(false) }; var selectedTags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }; var showTagSuggest by remember { mutableStateOf(false) }
    val suggestions = allTags.map { it.name }.filter { it !in selectedTags }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("新建任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("请输入任务") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("优先级", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                Box { OutlinedButton({ showPriorityMenu = true }, shape = RoundedCornerShape(10.dp)) { Text("${selectedPriority.emoji} ${selectedPriority.label}", fontSize = 14.sp) }
                    DropdownMenu(showPriorityMenu, { showPriorityMenu = false }) { Priority.entries.forEach { DropdownMenuItem(text = { Text("${it.emoji} ${it.label}") }, onClick = { selectedPriority = it; showPriorityMenu = false }) } } } }
            Spacer(Modifier.height(16.dp))
            Text("标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(4.dp))
            if (selectedTags.isNotEmpty()) { FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { selectedTags.forEach { TagChip(it, false) { selectedTags = selectedTags.filter { t -> t != it } } } }; Spacer(Modifier.height(6.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = tagInput, onValueChange = { tagInput = it; showTagSuggest = true }, placeholder = { Text(if (suggestions.isEmpty()) "输入新标签" else "输入或选择标签") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(8.dp)); Button({ val n = tagInput.trim(); if (n.isNotBlank() && n !in selectedTags) { selectedTags = selectedTags + n; tagInput = "" } }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(52.dp)) { Text("+") }
            }
            if (showTagSuggest && tagInput.isNotBlank() && suggestions.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestions.filter { it.contains(tagInput, ignoreCase = true) }.take(5).forEach { s -> Text("+ $s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { selectedTags = selectedTags + s; tagInput = "" }) }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasDeadline, { hasDeadline = it }); Spacer(Modifier.width(4.dp)); Text("设置截止日期") }
            if (hasDeadline) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { deadlineDate = deadlineDate.minusDays(1) }, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) { Text("﹣", fontSize = 20.sp) }
                    Spacer(Modifier.width(16.dp)); Text(deadlineDate.format(DateTimeFormatter.ofPattern("M月d日  EEEE")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(16.dp)); Button(onClick = { deadlineDate = deadlineDate.plusDays(1) }, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 20.sp) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("取消") }
                Button(onClick = { if (title.isNotBlank()) onConfirm(title.trim(), selectedPriority, if (hasDeadline) deadlineDate else null, selectedTags) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = title.isNotBlank()) { Text("添加") }
            }
        }
    }
}

// ============ 日期顶部 ============

@Composable
fun DateHeader(today: LocalDate) {
    val dow = when (today.dayOfWeek.value) { 1 -> "星期一"; 2 -> "星期二"; 3 -> "星期三"; 4 -> "星期四"; 5 -> "星期五"; 6 -> "星期六"; 7 -> "星期日"; else -> "" }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${today.monthValue}月", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium); Text("${today.dayOfMonth}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.width(16.dp))
        Column { Text("今天要做的事", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(dow, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)) }
    }
}

// ============ 单条任务 ============

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TaskItem(task: Task, isArchive: Boolean, onComplete: () -> Unit, onDelete: () -> Unit) {
    val today = LocalDate.now(); val isOverdue = task.deadline != null && task.deadline < today && !task.isCompleted; val isDueToday = task.deadline == today && !task.isCompleted; val isDone = task.isCompleted || task.isArchived
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onDelete).padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)).background(if (isDone) Color(0xFFBDBDBD) else task.priority.color))
            Spacer(Modifier.width(12.dp))
            if (!isArchive) { Checkbox(task.isCompleted, { onComplete() }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50), uncheckedColor = task.priority.color.copy(alpha = 0.5f))); Spacer(Modifier.width(4.dp)) }
            else { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF4CAF50).copy(alpha = 0.8f)), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = if (!isDone && (isDueToday || isOverdue)) FontWeight.SemiBold else FontWeight.Normal, textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None, color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!isDone) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("${task.priority.emoji} ${task.priority.label}", style = MaterialTheme.typography.labelSmall, color = task.priority.color) }
                        if (task.deadline != null) { Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            val dt = when { isOverdue -> "过期${today.toEpochDay() - task.deadline.toEpochDay()}天"; isDueToday -> "今天截止"; else -> task.deadline.format(DateTimeFormatter.ofPattern("M月d日")) }
                            val dc = when { isOverdue -> MaterialTheme.colorScheme.error; isDueToday -> Color(0xFFE65100); else -> MaterialTheme.colorScheme.onSurfaceVariant }
                            Text(dt, style = MaterialTheme.typography.labelSmall, color = dc) }
                        task.tags.forEach { Text("· #$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                    }
                }
                if (isArchive) { Spacer(Modifier.height(4.dp)); TextButton(onComplete) { Text("取消归档", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) } }
            }
        }
    }
}

// ============ 预览 ============

@Preview(showBackground = true, showSystemUi = true, heightDp = 700)
@Composable
fun AppMainPreview() { AiTodoAppTheme { AppMain() } }
