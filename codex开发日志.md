# Codex 开发日志

> 最后更新：2026-06-20

---

## 项目概述

**AiTodoApp** — Android 原生 Kotlin + Jetpack Compose AI 代办管理 App。核心理念：不用填表单，说句话就能管任务。

**技术栈：** Kotlin / Compose Material3 / OkHttp / kotlinx.serialization / DeepSeek API

---

## 本轮修改

### 1. priorityLocked 锁定逻辑修复
**文件：** `MainActivity.kt` — `updateTask()`
`priorityLocked = lockPriority || it.priorityLocked`，一旦锁定不会被非优先级编辑清零。

### 2. AiService JSON 构建 → kotlinx.serialization DSL
**文件：** `data/AiService.kt`
移除 `escapeJson()` 和 `toolsJson` 裸字符串，改用 `buildJsonObject/buildJsonArray/addJsonObject` DSL。

### 3. AI update_task 删旧建新 → 原地修改
**文件：** `MainActivity.kt` — TaskScreen 聊天块
从 `onAddTask+onDelete` 改为直接调 `onUpdateTask`，保留 id/completedAt/priorityLocked。

### 4. Manifest 恢复
**文件：** `AndroidManifest.xml`、`strings.xml`
恢复 `git checkout` 清掉的权限和服务声明。

### 5. TaskRepository 数据备份保护
**文件：** `data/TaskRepository.kt`
save 前 rename 旧文件为 `.bak`，load 损坏时回退读 `.bak`。

### 6. 日期筛选下拉
**文件：** `MainActivity.kt`
- `DayFilter` 枚举：OVERDUE / TODAY / TOMORROW / DAY_AFTER / ALL
- 右上角横滚按钮改为日期头同行紧凑下拉
- TODAY 过滤增加 `deadline == today`

### 7. 过期任务分区
**文件：** `MainActivity.kt`
- 列表底部常驻可折叠"⏰ 过期任务 (N)"区域
- TODAY 视图下已显示的过期任务不会重复出现

### 8. 设置页：过期显示偏好
**文件：** `SettingsRepository.kt` + `MainActivity.kt`
- `Settings.showOverdueInline` 字段（默认 true）
- 开启：过期任务混入主列表
- 关闭：过期任务单独显示在底部
- 设置页新增 Switch 控件

---

## 文件变更清单

```
AndroidManifest.xml        — 恢复权限 + Service 声明
strings.xml                — 恢复 voice_service_desc
SettingsRepository.kt      — 新增 showOverdueInline 字段
TaskRepository.kt          — save/load 备份保护
AiService.kt               — JSON 构建 DSL 化
MainActivity.kt            — 上述 1/3/6/7/8 全部
```

## 已知问题

| 问题 | 严重度 |
|------|--------|
| 语音输入不可用（国产 ROM 拦截） | 🔴 |
| 午夜自动归档缺失（无 WorkManager） | 🔴 |
| API Key 明文存储 | 🟡 |
| 无障碍服务 typeAllMask 过度监听 | 🟡 |
| 无截止日期通知 | 🟡 |

## 待开发

- [x] 数据备份保护
- [x] 日期筛选 Tab
- [x] 过期任务分区 + 设置开关
- [ ] 截止日期通知（WorkManager + NotificationChannel）
- [ ] MainActivity 拆分
- [ ] 日历月视图