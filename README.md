# AiTodoApp 🤖✅

**AI 驱动的智能待办应用** — 用自然语言管理任务，AI 理解你的意图，自动分类、排期、提醒。

> 手机是 AI Agent 的壳，LLM 通过云端 API 完成所有思考和操作。

---

## ✨ 核心特性

### 🧠 AI 自然语言交互
- 像跟人说话一样描述任务，AI 自动解析
- 「后天交大物实验报告」→ 自动设截止日期和优先级
- 「把买菜标记为完成」→ 智能多字段匹配定位任务
- 支持创建、修改、完成、删除、归档任务，管理标签，修改设置

### 📅 智能日程管理
- 5 级优先级（P0 🔥 紧急 ~ P4 ⚪ 低），根据截止日期自动调整
- 支持截止日期 + 截止时间 + 多天计划排期
- **系统日历同步**：任务截止→日历事件，含提前提醒
- 过期任务自动标记，精确到时间的过期判断

### 📊 AI 日报播报
- 每日早晚定时生成 AI 播报（WorkManager 调度）
- 🌅 早间：今日待办 + 紧急任务 + 时间分配建议
- 🌙 晚间：完成回顾 + 进度统计 + 明日计划
- 支持手动触发测试播报

### 🔔 智能前台通知
- 通知栏常驻显示**下一个该做的任务**
- 根据场景变换文案：过期提醒、截止催促、日常闲聊、完成庆祝
- 深夜静默时段（4:00-6:00）自动隐藏任务信息
- 支持趣味文案池，每天不一样

### 🏷 标签管理
- AI 创建任务时自动生成临时标签 → 可转正为永久标签
- 按标签筛选查看任务

### 📦 归档与回收站
- 已完成任务次日自动归档（按日期分组查看）
- 删除任务进入 30 天回收站，可恢复或永久删除
- 午夜定时器精确到秒触发自动归档

### 📋 操作日志系统
- 全链路追踪：每次 AI 交互生成 traceId，串联请求→工具调用→结果
- 时间轴展示，支持按 traceId 分组折叠
- 20+ 种操作类型：任务、标签、设置、日历、系统事件全覆盖
- 自动清理 7 天前的日志

### ⚙️ 高度可定制
- 兼容任意 OpenAI 兼容 API（默认 DeepSeek）
- 可自定义 API 地址、Key、模型名称
- 多种排序方式：手动 / 截止日期 / 优先级 / 创建时间
- 后台保活开关、日报调度时间自定义
- 日夜间主题自适应（Material3 Dynamic Color）

### 📤 数据导出
- 本地数据文件查看器，支持分享导出

---

## 🔧 首次使用设置

### 1️⃣ API 配置

1. 打开 App → 进入「设置」页
2. 填写 **API 地址**（默认 `https://api.deepseek.com/chat/completions`）
3. 填入你的 **API Key**
4. 返回任务页，开始用自然语言创建任务！

### 2️⃣ 必要权限设置

| 权限 | 用途 | 设置路径 |
|------|------|---------|
| 📅 **日历读写** | 任务截止→系统日历事件（可选） | 系统设置 → 应用 → AiTodoApp → 权限 → 日历 → 允许 |
| 🔔 **通知** | 接收日报播报、任务提醒 | 系统设置 → 应用 → AiTodoApp → 通知 → 开启 |
| ⚡ **省电策略** | 防止后台被杀，确保日报准时推送 | 系统设置 → 应用 → AiTodoApp → 耗电 → **不限制** |
| 🔒 **锁后台** | 防止清理近期任务时被关掉 | 多任务界面 → 找到 AiTodoApp → 下拉锁定 |

> 日历同步功能默认关闭，在「设置」页开启后仍需手动前往系统权限页开启「读取日历」。

---

## 💡 使用示例

```
用户：明天下午3点开会，讨论项目进度
→ AI 创建任务「开会」P2 🟡 | 计划日期：明天 14:00

用户：把买菜标记为完成
→ AI 智能匹配并标记完成

用户：后天交实验报告
→ AI 创建任务「交实验报告」P0 🔥 | 截止：后天

用户：这周有哪些任务要完成？
→ AI 列出本周待办

用户：删掉洗衣服
→ AI 匹配删除
```

---

## 🛠 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 2.2+ |
| UI | Jetpack Compose + Material3 |
| 架构 | 单 Activity + MVVM |
| AI 接口 | OkHttp + OpenAI 兼容 API（Function Calling） |
| 本地存储 | kotlinx.serialization JSON（原子写入 + 自动备份恢复） |
| 后台任务 | WorkManager 定时日报 + 通知刷新 |
| 日历同步 | Android CalendarContract API |
| 最低 SDK | 26（Android 8.0） |
| 目标 SDK | 36 |

---

## 🔧 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| API 地址 | OpenAI 兼容端点 | `https://api.deepseek.com/chat/completions` |
| 模型 | LLM 模型名 | `deepseek-v4-flash` |
| 自动日历同步 | 任务截止→日历事件 | 关闭 |
| 默认提醒时间 | 提前提醒分钟 | 30 min |
| 默认任务时长 | 日历事件时长 | 60 min |
| 排序方式 | 手动/截止/优先级/创建 | 手动 |
| 日报开关 | 早晚 AI 播报 | 关闭 |
| 后台保活 | 常驻通知保活 | 关（需手动开） |
| 过期任务分区 | 过期单独显示 | 开启 |
| Token 用量 | 顶栏显示 Token 消耗 | 关闭 |

---

## 📂 项目结构

```
com.example.aitodoapp/
├── MainActivity.kt           # 入口 + 数据模型 + 主导航 + 午夜定时器
├── MainViewModel.kt          # 业务逻辑（CRUD、排序、自动优先级、日历同步、自动归档、批量写入）
├── ForegroundService.kt      # 前台保活服务（显示下一个任务 + 趣味文案）
│
├── data/
│   ├── AiService.kt          # AI API 客户端（Function Calling + 日报生成）
│   ├── AiTypes.kt            # AI 动作类型（Create/Complete/Delete/Update 等）
│   ├── AiActionExt.kt        # AiAction → MatchCriteria 转换
│   ├── TaskRepository.kt     # 任务 / 标签 JSON 持久化（原子写入 + 备份恢复）
│   ├── SettingsRepository.kt # 设置持久化
│   ├── ReportRepository.kt   # 播报记录持久化
│   ├── ReportWorker.kt       # WorkManager 定时日报
│   ├── NotificationHelper.kt # 通知渠道创建
│   ├── NotificationContent.kt# 前台通知文案决策（下一个任务 + 场景判断）
│   ├── NotificationRefreshWorker.kt # 通知刷新 Worker
│   ├── GagPool.kt            # 通知栏趣味文案池
│   ├── CalendarSyncHelper.kt # 系统日历同步
│   ├── ActionLogRepository.kt# 操作日志持久化
│   ├── AppLogger.kt          # 统一日志入口（20+ 便捷方法）
│   ├── TokenRepository.kt    # Token 用量统计
│   └── LocalDateSerializer.kt# LocalDate 序列化
│
├── model/
│   └── ReportEntry.kt        # 播报数据模型
│
├── ui/
│   ├── screens/
│   │   ├── TaskScreen.kt     # 主任务页（AI 对话 + 任务列表 + 日期筛选）
│   │   ├── ArchiveScreen.kt  # 归档页
│   │   ├── TagManagerScreen.kt # 标签管理
│   │   ├── SettingsScreen.kt # 设置页（保存 diff + 文件分享 + 回收站）
│   │   ├── ReportViewScreen.kt # 播报查看
│   │   └── LogScreen.kt      # 操作日志（traceId 分组）
│   ├── components/           # 可复用组件
│   │   ├── TaskItem.kt       # 任务条（优先级色条 + 截止 + 标签）
│   │   ├── AddTaskDialog.kt  # 添加任务弹窗
│   │   ├── EditTaskDialog.kt # 编辑任务弹窗
│   │   ├── TagChip.kt        # 标签 chip
│   │   ├── ReportBadge.kt    # 播报未读标记
│   │   ├── ReportCard.kt     # 播报卡片渲染
│   │   └── CalendarTestDialog.kt # 日历调试
│   └── theme/                # 主题（颜色/字体/日夜间）
└── keepRules/
    └── rules.keep            # ProGuard 保留规则
```

---

## 🚀 构建

```bash
git clone https://github.com/themoon12321/AiTodoApp.git
cd AiTodoApp
./gradlew assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

---

**AiTodoApp** — 让 AI 做你的待办管家。🧠✨
