# ViewModel 重构交接文档

## 为什么重构

当前 `MainActivity.kt` 的 `AppMain` 函数中，所有状态（tasks、allTags、settings 等）和操作（completeTask、addTask、deleteTask、updateTask 等）都直接定义在 `@Composable` 函数里，通过 callback lambda 层层传递给子组件。

**痛点**：新增一个参数（如 estimatedMinutes）需要同步修改 5 个地方：
1. 子组件的 callback 签名
2. 子组件调用处的 lambda
3. 父组件的 callback 签名
4. 父组件的 lambda
5. 操作函数本身的签名

## 目标

将状态和操作提取到 `MainViewModel` 中，UI 层直接读写 ViewModel，不再通过 callback 链传递。

## 方案

### 新建文件 `app/src/main/java/com/example/aitodoapp/MainViewModel.kt`

```kotlin
package com.example.aitodoapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.aitodoapp.data.*
import java.time.LocalDate

class MainViewModel : ViewModel() {
    // === 状态 ===
    var tasks by mutableStateOf(TaskRepository.load<Task>("tasks.json"))
        private set
    var allTags by mutableStateOf(TaskRepository.load<Tag>("tags.json"))
        private set
    var settings by mutableStateOf(SettingsRepository.load())
        private set
    var selectedDay by mutableStateOf(DayFilter.TODAY)
    var tab by mutableStateOf(0)

    val today: LocalDate get() = LocalDate.now()

    // 派生状态
    val allActive get() = tasks.filter { !it.isArchived && !it.isDeleted }
    val overdueTasks get() = allActive.filter { ... }
    val activeTasks get() = when (selectedDay) { ... }
    val archivedTasks get() = tasks.filter { it.isArchived && !it.isDeleted }
    val deletedTasks get() = tasks.filter { it.isDeleted }

    // === 操作 ===
    fun completeTask(id: String) { ... }
    fun deleteTask(id: String) { ... }
    fun archiveTask(id: String, completedDate: LocalDate? = null) { ... }
    fun unarchiveTask(id: String) { ... }
    fun addTask(title: String, ...) { ... }
    fun updateTask(id: String, ...) { ... }
    fun addTag(n: String): String { ... }
    fun createTag(n: String) { ... }
    fun promoteTag(n: String) { ... }
    fun deleteTag(n: String) { ... }
    fun saveAll() { ... }
}
```

### 改造后的 AppMain

```kotlin
@Composable
fun AppMain(viewModel: MainViewModel = viewModel()) {
    val today = viewModel.today
    // ↓ 不再有状态定义和操作函数，全部从 viewModel 读取
    TaskScreen(
        tasks = viewModel.activeTasks,
        allTags = viewModel.allTags,
        onComplete = { viewModel.completeTask(it) },
        onAddTask = { t, p, d, tags, c, pl, dt, em -> viewModel.addTask(t, p, d, tags, c, pl, dt, em) },
        ...
    )
}
```

### 子组件改造

TaskScreen 等子组件不再需要接收 15 个 callback 参数，改为接收 `MainViewModel` 或者只接收需要的状态：

```kotlin
// 改前
fun TaskScreen(tasks: ..., onComplete: ..., onAddTask: ..., onDelete: ..., ...)

// 改后
fun TaskScreen(viewModel: MainViewModel) {
    // 直接 viewModel.completeTask(task.id)
    // 直接 viewModel.addTask(...)
}
```

或者更温和的方式：只把 `tasks` 和操作函数从 ViewModel 里读，不改变子组件签名。

## 改动范围

### 需要改的文件

| 文件 | 改动内容 |
|------|---------|
| `MainViewModel.kt` | **新建**，从 AppMain 搬入所有状态和操作 |
| `MainActivity.kt` | AppMain 简化为 ViewModel 调用，保留模型类+匹配引擎+预览 |
| `TaskScreen.kt` | 回调签名可简化（可选直接传入 ViewModel） |
| `SettingsScreen.kt` | 回调签名可简化 |
| `ArchiveScreen.kt` | 回调签名可简化 |
| `ReportViewScreen.kt` | 无需改动 |
| 所有 `components/*.kt` | 无需改动 |

## 注意点

1. **不要改动 data 层**：TaskRepository、SettingsRepository、AiService 等不动
2. **不要改动模型类**：Task、Tag、Priority 等不动  
3. **不要改动 components**：AddTaskDialog、EditTaskDialog 等只依赖 callback，不动
4. **Context 依赖**：`completeTask` 中的 `CalendarSyncHelper.deleteEvent` 需要 Context，通过 `AndroidViewModel` 获取 ApplicationContext
5. **autoPriority** 可以保留在 AppMain 或移到 ViewModel

## 预期结果

- `MainActivity.kt` 从 ~400 行缩减到 ~250 行
- callbback 链从 5 层减少到 2 层（ViewModel → UI）
- 新增字段只需改 ViewModel + UI 调用处，2 个地方
