package com.example.aitodoapp.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 操作日志条目。每条记录用户或系统的一个操作。
 *
 * @param type 操作类型（见 [LogType]），决定显示的图标和分类
 * @param source 触发来源：AI（AI对话驱动）/ MANUAL（手动点击）/ SYSTEM（后台自动）
 * @param traceId 追踪链 ID，关联同一次 AI 对话的全部日志（输入→输出→工具调用→执行结果）
 * @param summary 一行摘要，时间轴上直接显示
 * @param detail 可选详情（如 AI 原始输入、工具调用列表、token 消耗），点开详情看
 * @param changes 变化记录（旧值→新值），如修改任务时记录改了哪些字段
 */
@Serializable
data class ActionLog(
    val id: String = System.currentTimeMillis().toString() + "-" + (counter++),
    val time: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")),
    @Serializable(with = LocalDateTimeSerializer::class) val timestamp: LocalDateTime = LocalDateTime.now(),
    val type: String,
    val source: String,
    val traceId: String = "",
    val summary: String,
    val detail: String = "",
    val changes: List<Change> = emptyList()
) {
    companion object {
        private var counter = 0
    }
}

/** 字段变化记录，用于 UPDATE 等操作追踪旧值→新值 */
@Serializable
data class Change(
    val field: String,
    val oldValue: String? = null,
    val newValue: String? = null
)

/** 操作类型，每个对应一个 emoji 图标，方便时间轴快速识别 */
object LogType {
    const val AI_INPUT = "ai_input"       // 📨 用户输入给 AI
    const val AI_OUTPUT = "ai_output"     // 🤖 AI 返回
    const val AI_TOOL = "ai_tool"         // 🔧 AI 调用工具
    const val CREATE = "create"           // ➕ 创建任务
    const val COMPLETE = "complete"       // ✅ 完成任务
    const val DELETE = "delete"           // 🗑️ 删除任务
    const val UPDATE = "update"           // ✏️ 修改任务
    const val ARCHIVE = "archive"         // 📦 归档
    const val UNARCHIVE = "unarchive"     // 📤 取消归档
    const val RESTORE = "restore"         // ♻️ 恢复任务
    const val TAG = "tag"                 // 🏷️ 标签操作
    const val SETTINGS = "settings"       // ⚙️ 设置变更
    const val CALENDAR = "calendar"       // 📅 日历同步
    const val REPORT = "report"           // 📊 日报播报
    const val SYSTEM = "system"           // 🔄 系统自动操作
    const val AI_TOOL_CALL = "ai_tool_call"  // 🛠️ AI 调用单个工具
    const val AI_RAW_OUTPUT = "ai_raw_output" // 📦 AI 原始返回 JSON

    fun emoji(type: String): String = when (type) {
        AI_INPUT -> "📨"
        AI_OUTPUT -> "🤖"
        AI_TOOL -> "🔧"
        AI_TOOL_CALL -> "🛠️"
        AI_RAW_OUTPUT -> "📦"
        CREATE -> "➕"
        COMPLETE -> "✅"
        DELETE -> "🗑️"
        UPDATE -> "✏️"
        ARCHIVE -> "📦"
        UNARCHIVE -> "📤"
        RESTORE -> "♻️"
        TAG -> "🏷️"
        SETTINGS -> "⚙️"
        CALENDAR -> "📅"
        REPORT -> "📊"
        SYSTEM -> "🔄"
        else -> "•"
    }
}

object ActionLogRepository {

    private const val FILE_NAME = "action_logs.json"
    private const val RETENTION_DAYS = 7
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; prettyPrint = false }
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
    }

    fun load(): List<ActionLog> {
        val f = file ?: return emptyList()
        if (!f.exists()) return emptyList()
        return try {
            val text = f.readText()
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<ActionLog>>(text)
        } catch (_: Exception) { emptyList() }
    }

    /** 添加一条日志，自动清理超过保留天数的旧记录 */
    fun add(log: ActionLog) {
        val logs = load().toMutableList()
        logs.add(0, log)  // 最新的放前面，时间轴倒序
        // 清理超过保留天数的记录
        val cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS.toLong())
        val filtered = logs.filter { it.timestamp.isAfter(cutoff) }
        save(filtered)
    }

    /** 原子写入：tmp → rename 旧为 bak → tmp 改名为正式 → 删 bak。与 TaskRepository 标准一致 */
    private fun save(logs: List<ActionLog>) {
        val f = file ?: return
        val tmp = File(f.absolutePath + ".tmp")
        val bak = File(f.absolutePath + ".bak")
        try {
            tmp.writeText(json.encodeToString(logs))
            if (f.exists()) { bak.delete(); f.renameTo(bak) }
            tmp.renameTo(f)
            bak.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            tmp.delete()
        }
    }

    fun clearAll() {
        save(emptyList())
    }
}
