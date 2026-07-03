package com.example.aitodoapp.data

import android.content.Context
import com.example.aitodoapp.model.ReportEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.time.LocalDate

object ReportRepository {

    private const val FILE_NAME = "reports.json"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; prettyPrint = true }
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
    }

    fun load(): List<ReportEntry> {
        val f = file ?: return emptyList()
        if (!f.exists()) return emptyList()
        return try {
            val text = f.readText()
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<ReportEntry>>(text)
        } catch (_: Exception) { emptyList() }
    }

    fun save(reports: List<ReportEntry>) {
        val f = file ?: return
        try { f.writeText(json.encodeToString(reports)) }
        catch (_: Exception) {}
    }

    /** 添加新播报，自动清理超出保留天数的历史 */
    fun addReport(report: ReportEntry, retentionDays: Int = 30): List<ReportEntry> {
        val reports = load().toMutableList()
        reports.add(0, report) // 最新的放前面
        val cutoff = LocalDate.now().minusDays(retentionDays.toLong())
        val filtered = reports.filter { it.date >= cutoff.toString() }
        save(filtered)
        return filtered
    }

    /** 标记为已读 */
    fun markRead() {
        val reports = load().map { it.copy(isRead = true) }
        save(reports)
    }

    /** 是否有未读播报 */
    fun hasUnread(): Boolean = load().any { !it.isRead }

    /** 清除所有播报记录 */
    fun clearAll() { save(emptyList()) }
}
