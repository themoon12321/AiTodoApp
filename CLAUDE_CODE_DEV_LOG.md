# Claude Code 开发日志

> 记录从零到一构建 Android 代办 App 的过程、技术决策、踩坑记录
> 另一个 Agent 可通过本文档快速了解当前进度和上下文

---

## 2026-06-15：项目初始化 + Compose 切换

### 做了什么
- 仓库已有的 Empty Views Activity 模板，改为 **Jetpack Compose + Material3**
- 搭建基础 UI 骨架：纯文字清单 + 日期栏 + 统计标签

### 技术决策
- **为什么 Compose 不是 Views**：用户核心需求是"丝滑动画"，Compose 的 `spring(dampingRatio=0.6f)` 一行代码实现 Q 弹效果，Views 需要 ~30 行 ObjectAnimator
- **为什么 Material3 不是 Material2**：2026 年新项目默认 Material3，动态取色（Android 12+）是 Material3 特性

### 踩坑
- 数据类型 `LocalDate` 要求 API 26+，项目 minSdk 当时是 24，编译报错 → 升到 26
- `combinedClickable` 需要 `@OptIn(ExperimentalFoundationApi::class)`

---

## 2026-06-15 ~ 16：核心功能搭建

### 做了什么
- 任务清单：勾选 → 沉底，勾掉文字划掉变灰
- 长按菜单（优先级选择器、滑动选 → 后被用户否定 → 改成右侧面板 → 最终放弃整个长按菜单改成编辑弹窗）
- 添加任务弹窗：标题、分类（后被标签替代）、优先级、截止日期、标签

### 踩坑
- 优先级选择器做了 3 个版本（滑动选→右侧面板→纯文字），用户不满意交互细节 → 后来整体替换为编辑弹窗
- `Modifier.weight()` 只能在 `Row`/`Column` 内使用，不能在 `Box` 内 → 编译报错
- 分类和标签的关系讨论：最终用户确认"分类就是标签"，去掉单独的分类字段

### 关键用户反馈
- "一个 App 的门锁雕了三小时，墙都还没砌" → 不要再在单个交互上过度细化
- MVP 只做单行标题，详情字段后续加

---

## 2026-06-16：标签系统 + 本地存储 + 底部导航

### 做了什么
- **标签系统**：正式标签（手动创建）+ 临时标签（输入时自动收录），可"转正"
- **底部导航栏**：任务 / 归档 / 标签 三个 tab
- **本地存储**：`kotlinx.serialization` + JSON 文件，`TaskRepository` 泛型 `save<T>()`/`load<T>()`

### 技术决策
- **为什么 JSON 文件不是 Room**：几百条任务不需要数据库引擎，JSON 文件零依赖、零模板、毫秒级读写
- **两个文件**：`tasks.json` + `tags.json`，各自独立存储

### 踩坑
- `buildList` + `when{}` 嵌套导致编译器无法解析括号 → 改用 `mutableListOf` 和显式类型
- 缩进不一致导致 `}` 层级错位 → 重构时用 `Write` 全量重写而非片段 `Edit`

---

## 2026-06-17：AI 接入 + 计划日期

### 做了什么
- **DeepSeek API 接入**：OkHttp POST → JSON 解析 → Function Calling 工具调用
- **三个工具**：`create_task` / `complete_task` / `delete_task` / `update_task`
- **AI 策略**：标题精简、内容生成、多维度优先级、计划日期提取、标签策略、不要反问
- **计划日期字段**：`plannedDates: List<LocalDate>`，日历多选
- **今日/全部筛选**：右上角切换，今日只显示无计划 OR 计划含今天的任务
- **编辑弹窗**：可滚动 + 日历选择器 + 内容描述 + 删除确认
- **优先级自动调整**：每次启动按 DDL 重算（priorityLocked 保护手动修改）

### 技术决策
- **为什么 Function Calling 不是纯文本解析**：AI 输出结构化 JSON 工具调用，类型安全、字段精确
- **DeepSeek 兼容 OpenAI 格式**：`tools` 参数定义工具，`tool_choice: "auto"` 让 AI 自主选择

### 踩坑
- `JsonObject(mapOf(...))` 有 3 个以上参数时编译器类型推断失败 → 改回 JSON 字符串方案
- 后来另一个 Agent 改成了 `buildJsonObject { }` + kotlinx.serialization DSL（正确方案）

---

## 2026-06-18：编辑弹窗 + 日历选择器 + 计划日期显示

### 做了什么
- 编辑弹窗：可滚动（`verticalScroll` + `heightIn(max=560.dp)` → 后来改为 `fillMaxHeight(0.85f)`）
- 日历选择器：`DatePickerDialog` + `DatePickerState`
- 计划日期在任务条上显示：`📅 6/18等3天`
- 优先级锁机制：`priorityLocked` 字段，用户手动改过优先级的任务系统不再自动调整

### 踩坑
- 编辑弹窗底部被截 → `contentAlignment = Center` 和 `heightIn` 冲突 → 改为 `fillMaxHeight(0.85f)` + `Center`
- 计划日期序列化警告 → 新增 `LocalDateListSerializer`

---

## 2026-06-19：无障碍语音 + 通知栏 Tile

### 做了什么
- **通知栏 Tile**：`QuickTileService`，下拉通知栏点"AI 代办"→ 打开 App + 弹出聊天框
- **无障碍语音**：`VoiceEntryService`，双击音量-键触发语音识别
- **VoiceDialogActivity**：透明 Activity + `ActivityResultLauncher` 调用系统语音识别

### 技术决策
- **为什么无障碍不是悬浮球**：悬浮球需要 `SYSTEM_ALERT_WINDOW` 权限 + 前台 Service，OPPO 上容易被杀

### 踩坑
- 通知权限在 OPPO 上默认禁用且不可手动开启 → 语音反馈不能用通知
- `SpeechRecognizer` 在后台 Service 中受限（OPPO 限制了第三方语音识别）
- `startActivityForResult` 已废弃 → 改为 `registerForActivityResult` + `ActivityResultLauncher`
- **最终状态**：无障碍按键监听可用（双击音量键/震动反馈），但语音识别对话框未跑通（OPPO 系统拦截）

---

## 2026-06-30：综合改进

### 做了什么
- **AI 读取当前时间**：给系统 prompt 注入 `LocalDateTime.now()`（精确到分），让 AI 能正确计算"下周/明天/下午"等相对时间
- **弹窗点空白处取消**：编辑弹窗背景层可点击关闭，不用翻到下面找取消按钮
- **过期任务分区可控**：设置开关真正生效。开→独立折叠栏；关→混排（正常→过期→已完成）
- **已完成任务始终可见**：TODAY 筛选条件加入 `it.isCompleted`，勾掉的任务不会消失
- **AI 任务列表带状态标记**：传给 AI 的任务前加 `[已完成]` `[过期]` 前缀
- **新增 `completed_tasks` 工具**：AI 可主动查询已完成任务列表
- **长按 AI 对话框自动聚焦**：`FocusRequester` + `LaunchedEffect`，弹开时输入法直接弹出
- **长短按可互换**：设置新增「长按打开 AI 对话框」开关，满足不同习惯
- **新建任务窗口升级**：与编辑任务窗口一致——可滚动、带内容描述、日历选择器、计划日期

### 待优化清单
1. **语音入口不可用**：系统语音识别在 OPPO 被拦截
2. **午夜自动归档未实现**：仅在 App 启动时归档一次
3. **MainActivity.kt 单体巨石**：~670 行，无 ViewModel，无状态分层
4. `.bak` 文件命名冲突：`tasks.json` 和 `tags.json` 共用同个 `.bak`

---


## 2026-07-02：日历同步 + Token 统计 + 多轮改进

### 做了什么
- **日历系统同步**： 创建/删除/更新系统日历事件，支持 fallback 兼容 OPPO 等品牌
- **Task 模型加 **：防重复创建，事件与任务绑定
- **完整同步生命周期**：创建→写日历、修改截止→删旧建新、完成→删日历、取消完成→重建、删除任务→删日历
- **启动时补同步**：开启自动同步后，已有未同步任务自动补写日历
- **全天事件 UTC 修复**：解决时区错位导致日历显示前一天的问题
- **缓存 ID 失效自动 fallback**：日历 ID 失效时清缓存重新试探 1~5
- ** 支持计划日期**：AI 可修改任务的 ，不再需要删了重建
- **Token 统计系统**： 记录每日/总 Token 用量，头部可显示当日用量
- **设置页分组**：API 设置 / 显示偏好 / 数据统计 / 测试 分区管理
- **输入框自动拉伸**： 输入长文字自动换行
- **AI 处理进度指示器**：头部 + 对话框双进度条，等待时轮播趣味状态
- **完成反馈 5 秒消失**：emoji + 计数，头部显示后自动消失
- **标签任务计数**：标签管理显示每个标签关联的任务数
- **标签点击查看任务**：点击标签弹出该标签下的任务列表
- **TODAY 视图逻辑优化**：仅显示今日完成的任务，不再混入历史任务
- **AI 时间提取修复**：考试/会议场景 planned_dates 和 deadline 同时设置
- **SettingsRepository 备份**：写入前备份，损坏自动恢复（与原 TaskRepository 一致）
- **plannedDates 过期归类**：计划日期全部过期的任务归入过期区


## 2026-07-02：日历同步 + Token 统计 + 多轮改进

### 做了什么
- **日历系统同步**：CalendarSyncHelper 创建/删除/更新系统日历事件，支持 fallback 兼容各品牌
- **Task 模型加 calendarEventId**：防重复创建，事件与任务绑定
- **完整同步生命周期**：创建->写日历、修改截止->删旧建新、完成->删日历、取消完成->重建、删除任务->删日历
- **启动时补同步**：开启自动同步后，已有未同步任务自动补写日历
- **全天事件 UTC 修复**：解决时区错位导致日历显示前一天的问题
- **缓存 ID 失效自动 fallback**：日历 ID 失效时清缓存重新试探 1~5
- **update_task 支持计划日期**：AI 可修改任务的 planned_dates，不再需要删了重建
- **Token 统计系统**：TokenRepository 记录每日/总 Token 用量，头部可显示当日用量
- **设置页分组**：API 设置 / 显示偏好 / 数据统计 / 测试 分区管理
- **输入框自动拉伸**：maxLines=5 输入长文字自动换行
- **AI 处理进度指示器**：头部 + 对话框双进度条，等待时轮播趣味状态
- **完成反馈 5 秒消失**：emoji + 计数，头部显示后自动消失
- **标签任务计数**：标签管理显示每个标签关联的任务数
- **标签点击查看任务**：点击标签弹出该标签下的任务列表
- **TODAY 视图逻辑优化**：仅显示今日完成的任务，不再混入历史任务
- **AI 时间提取修复**：考试/会议场景 planned_dates 和 deadline 同时设置
- **SettingsRepository 备份**：写入前备份，损坏自动恢复（与原 TaskRepository 一致）
- **plannedDates 过期归类**：计划日期全部过期的任务归入过期区

## 2026-07-02 后续：通知+超时+重试 + 多字段匹配引擎 + AI全功能控制

### 做了什么
- **通知权限**：声明 POST_NOTIFICATIONS，新增 NotificationHelper，通知内容显示具体任务而非计数
- **超时优化**：OkHttp connectTimeout 15s / readTimeout 30s，避免后台网络断开后无限挂起
- **后台自动重试**：网络失败后保存输入 → 回到前台自动重发 → 40s 安全兜底
- **今日视图修复**：已完成任务只显示今天完成的（completedAt == today）
- **多字段匹配引擎**：`findBestMatch` 评分制（标题50+截止30+时间20+标签20+内容15+计划日期25），平局歧义返回 null 避免误操作
- **任务详情增强**：传给 AI 的任务列表含截止时间/标签/计划/内容摘要，不再只有标题
- **complete_task/delete_task 扩展**：支持 deadline/deadline_time/tags/content/planned_date 多字段精确定位
- **update_task 扩展**：match_ 前缀定位字段 + deadline_time/planned_times 修改字段
- **update_settings 工具**：AI 可修改所有设置项（API/模型/开关/提醒）
- **manage_tag 工具**：create/delete/promote 标签
- **archive_task / unarchive_task 工具**：归档/恢复任务
- **统一日历绑定**：沿用原有机制（创建→写日历，完成→删日历），不单独加日历工具
- **架构决策**：保留现有 Function Calling + when 分支架构，8 个工具在当前体量下刚好合适

### 待优化清单
1. **语音入口不可用**：系统语音识别在 OPPO 被拦截
2. **午夜自动归档未实现**：仅在 App 启动时归档一次
3. **MainActivity.kt 单体巨石**：~1400+ 行，无 ViewModel，无状态分层
4. **前台 Service 通知**：后续做播报时需要
5. **上下文多轮对话**：用户已提需求，待设计
6. **用户画像**：AI 自主学习用户习惯，动态调整策略

## 常用命令

```bash
# 编译
./gradlew compileDebugKotlin

# 完整构建
./gradlew assembleDebug

# 查看完整错误
./gradlew compileDebugKotlin 2>&1 | grep "\.kt:"

# 运行（需要连接设备）
./gradlew installDebug
```
