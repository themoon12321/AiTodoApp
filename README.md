# AiTodoApp 🤖✅

**AI 驱动的智能待办应用** — 用自然语言创建、管理、追踪任务，无需手动填写表单。

> 手机只是 AI Agent 的壳，LLM 通过云端 API 完成所有思考。

## ✨ 核心特性

### 🧠 AI 自然语言交互
- 用自然语言创建、修改、完成、删除任务，AI 自动理解语义
- 「后天交大物实验报告」→ 自动设截止日期和优先级
- 「把买菜标记为完成」→ 智能匹配任务并标记
- 支持查询已完成任务、管理标签、修改设置

### 📅 智能日程管理
- 5 级优先级（P0 🔥 紧急 ~ P4 ⚪ 低），支持根据截止日期自动调整
- 截止日期 + 截止时间 + 计划日期（支持多天排期）
- 可选同步到系统日历，含提前提醒
- 过期任务自动标记提示

### 📊 AI 日报播报
- 每日早晚定时生成 AI 播报
- 🌅 早上：今日待办 + 紧急任务 + 时间分配建议
- 🌙 晚上：完成回顾 + 进度统计 + 明日计划
- 支持手动触发测试播报

### 🏷 标签管理
- AI 创建任务时自动生成临时标签 → 可转正为永久标签
- 按标签筛选查看任务

### 📦 归档与回收站
- 已完成任务自动归档（按日期分组查看）
- 删除任务进入 30 天回收站，可恢复或永久删除
- 超期自动清理

### ⚙️ 高度可定制
- 兼容任意 OpenAI 兼容 API（默认 DeepSeek）
- 自定义 API 地址、Key、模型
- 多种排序方式：手动 / 截止日期 / 优先级 / 创建时间
- 日夜间主题自适应（Material3 Dynamic Color）

## 🔧 首次使用设置

### 1️⃣ API 配置

1. 打开 App → 进入「设置」页
2. 填写 **API 地址**（默认 `https://api.deepseek.com/chat/completions`，无需修改）
3. 填入你的 **API Key**
4. 返回任务页，开始用自然语言创建任务！

### 2️⃣ 必要权限设置

App 正常运行需要以下权限，请按需开启：

| 权限 | 用途 | 设置路径 |
|------|------|---------|
| 📅 **日历读写** | 任务截止日期同步到系统日历（可选） | 系统设置 → 应用 → AiTodoApp → 权限 → 日历 → 允许 |
| 🔔 **通知** | 接收日报播报、任务提醒 | 系统设置 → 应用 → AiTodoApp → 通知 → 开启 |
| ⚡ **省电策略** | 防止后台被杀，确保日报准时推送 | 系统设置 → 应用 → AiTodoApp → 耗电 → 省电策略 → **不限制** / 允许后台运行 |
| 🔒 **锁后台** | 防止清理近期任务时被关掉 | 进入多任务界面 → 找到 AiTodoApp → 向下拉 / 点击锁图标锁定 |

> **提示**：日历同步功能默认关闭，在「设置」页开启后，系统会自动弹窗请求日历权限，但部分 Android 版本仍需手动前往权限页开启「读取日历」。

## 💡 使用示例

```
用户输入：明天下午3点开会，讨论项目进度
→ AI 创建任务：「开会」P2 🟡 | 计划日期：明天 14:00

用户输入：把买菜标记为完成
→ AI 找到匹配任务并标记完成

用户输入：删掉后天交实验报告
→ AI 找到带有截止日期的匹配任务并删除

用户输入：这周有哪些任务要完成？
→ AI 列出本周待办任务
```

## 🛠 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 2.2+ |
| UI | Jetpack Compose + Material3 |
| 架构 | 单 Activity + MVVM |
| AI 接口 | OkHttp + OpenAI 兼容 API（Function Calling） |
| 本地存储 | kotlinx.serialization JSON 文件（原子写入 + 自动备份恢复） |
| 后台任务 | WorkManager 定时日报 |
| 日历同步 | Android CalendarContract API |
| 最低 SDK | 26（Android 8.0） |
| 目标 SDK | 36 |

## 🔧 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| API 地址 | OpenAI 兼容 API 端点 | `https://api.deepseek.com/chat/completions` |
| API Key | 你的 API 密钥 | — |
| 模型 | 使用的 LLM 模型 | `deepseek-v4-flash` |
| 自动日历同步 | 任务截止 → 系统日历事件 | 关闭 |
| 默认提醒时间 | 日历事件提前提醒分钟数 | 30 分钟 |
| 默认任务时长 | 日历事件默认持续时间 | 60 分钟 |
| 排序方式 | 手动 / 截止 / 优先级 / 创建时间 | 手动 |
| 日报开关 | 启用每日 AI 播报 | 关闭 |
| 过期任务分区 | 过期任务单独分区显示 | 开启 |
| 显示 Token 用量 | 在顶栏显示今日 Token 消耗 | 关闭 |

## 📂 项目结构

```
com.example.aitodoapp/
├── MainActivity.kt          # 入口 Activity + 数据模型 + 主导航
├── MainViewModel.kt         # 业务逻辑（CRUD、排序、自动优先级、日历同步）
├── data/
│   ├── AiService.kt         # AI API 客户端（Function Calling）
│   ├── AiTypes.kt           # AI 动作类型（Create / Complete / Delete / Update 等）
│   ├── AiActionExt.kt       # AiAction → MatchCriteria 转换
│   ├── TaskRepository.kt    # 任务 / 标签 JSON 持久化（原子写入 + 备份恢复）
│   ├── SettingsRepository.kt# 设置持久化
│   ├── ReportRepository.kt  # 播报记录持久化
│   ├── CalendarSyncHelper.kt# 系统日历同步（创建 / 删除日历事件）
│   ├── ReportWorker.kt      # WorkManager 定时日报
│   └── NotificationHelper.kt# 通知渠道创建
├── model/
│   └── ReportEntry.kt       # 播报数据模型
├── ui/
│   ├── screens/             # 任务 / 归档 / 标签 / 设置 / 播报页面
│   ├── components/          # 可复用组件（TaskItem, TagChip, AddTaskDialog 等）
│   └── theme/               # 主题（颜色、字体、日夜间模式）
```

## 🚀 构建与安装

```bash
git clone https://github.com/themoon12321/AiTodoApp.git
cd AiTodoApp
./gradlew assembleDebug
```

APK 生成路径：`app/build/outputs/apk/debug/app-debug.apk`

---

**AiTodoApp** — 让 AI 做你的待办管家。🧠✨
