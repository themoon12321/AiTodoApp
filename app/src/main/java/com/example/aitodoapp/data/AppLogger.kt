package com.example.aitodoapp.data

/**
 * 统一操作日志入口。
 *
 * 项目内所有模块统一通过本对象记录操作日志，确保格式统一、来源准确、支持 traceId 追踪。
 * 新增功能时只需调用对应的便捷方法即可，不需要手动拼 type/source/summary。
 *
 * 用法：
 *   AppLogger.taskCreated("买菜", traceId)
 *   AppLogger.settingsChanged(listOf("api_url", "model"))
 *   AppLogger.system("数据迁移完成", "活跃5 归档3 回收站1")
 *
 * 底层方法 [add] 直接写 ActionLogRepository，所有便捷方法最终走它。
 */
object AppLogger {

    // ============================================================
    // 底层
    // ============================================================

    /** 直接写一条日志（所有便捷方法最终走这里） */
    fun add(type: String, source: String, summary: String, detail: String = "", traceId: String = "") {
        try {
            ActionLogRepository.add(ActionLog(
                type = type, source = source, summary = summary, detail = detail, traceId = traceId
            ))
        } catch (_: Exception) {}
    }

    // ============================================================
    // 任务操作
    // ============================================================

    fun taskCreated(title: String, traceId: String = "") {
        add(LogType.CREATE, source(traceId), "创建任务：$title", traceId = traceId)
    }

    fun taskCompleted(title: String, traceId: String = "") {
        add(LogType.COMPLETE, source(traceId), "完成任务：$title", traceId = traceId)
    }

    fun taskUncompleted(title: String, traceId: String = "") {
        add(LogType.UPDATE, source(traceId), "取消完成：$title", traceId = traceId)
    }

    fun taskDeleted(title: String, traceId: String = "") {
        add(LogType.DELETE, source(traceId), "删除任务：$title", traceId = traceId)
    }

    fun taskUpdated(title: String, traceId: String = "") {
        add(LogType.UPDATE, source(traceId), "修改任务：$title", traceId = traceId)
    }

    fun taskArchived(title: String, traceId: String = "") {
        add(LogType.ARCHIVE, source(traceId), "归档任务：$title", traceId = traceId)
    }

    fun taskUnarchived(title: String, traceId: String = "") {
        add(LogType.UNARCHIVE, source(traceId), "取消归档：$title", traceId = traceId)
    }

    fun taskRestored(title: String, traceId: String = "") {
        add(LogType.RESTORE, source(traceId), "恢复任务：$title", traceId = traceId)
    }

    fun taskPermanentDeleted(title: String, traceId: String = "") {
        add(LogType.DELETE, source(traceId), "永久删除：$title", traceId = traceId)
    }

    // ============================================================
    // 标签操作
    // ============================================================

    fun tagCreated(name: String, traceId: String = "") {
        add(LogType.TAG, source(traceId), "创建标签：$name", traceId = traceId)
    }

    fun tagPromoted(name: String, traceId: String = "") {
        add(LogType.TAG, source(traceId), "标签转正：$name", traceId = traceId)
    }

    fun tagDeleted(name: String, traceId: String = "") {
        add(LogType.TAG, source(traceId), "删除标签：$name", traceId = traceId)
    }

    // ============================================================
    // 设置
    // ============================================================

    /** 记录设置变更。fields 传入变更的字段名列表 */
    fun settingsChanged(fields: List<String>) {
        add(LogType.SETTINGS, "MANUAL", "设置已更新：${fields.joinToString("、")}")
    }

    // ============================================================
    // 系统事件
    // ============================================================

    /** 通用系统事件 */
    fun system(summary: String, detail: String = "") {
        add(LogType.SYSTEM, "SYSTEM", summary, detail)
    }

    // ============================================================
    // 日历同步
    // ============================================================

    fun calendarEventCreated(taskTitle: String) {
        add(LogType.CALENDAR, "SYSTEM", "日历同步：创建事件「$taskTitle」")
    }

    fun calendarEventDeleted(taskTitle: String) {
        add(LogType.CALENDAR, "SYSTEM", "日历同步：删除事件「$taskTitle」")
    }

    fun calendarSyncError(taskTitle: String, detail: String) {
        add(LogType.CALENDAR, "SYSTEM", "日历同步失败「$taskTitle」", detail = detail)
    }

    // ============================================================
    // 前台服务
    // ============================================================

    fun foregroundServiceStarted() {
        add(LogType.SYSTEM, "SYSTEM", "前台服务已启动（保活）")
    }

    fun foregroundServiceStopped() {
        add(LogType.SYSTEM, "SYSTEM", "前台服务已停止（保活关闭）")
    }

    // ============================================================
    // 数据迁移
    // ============================================================

    fun dataMigrated(detail: String) {
        add(LogType.SYSTEM, "SYSTEM", "数据格式迁移完成", detail)
    }

    // ============================================================
    // 测试操作
    // ============================================================

    fun testOperation(name: String, result: String) {
        add(LogType.SYSTEM, "MANUAL", "测试：$name → $result")
    }

    // ============================================================
    // 播报
    // ============================================================

    fun reportScheduled(isMorning: Boolean) {
        add(LogType.SYSTEM, "SYSTEM", "${if (isMorning) "早间" else "晚间"}播报已调度")
    }

    fun reportCancelled(isMorning: Boolean) {
        add(LogType.SYSTEM, "SYSTEM", "${if (isMorning) "早间" else "晚间"}播报已取消")
    }

    fun reportCleared() {
        add(LogType.SYSTEM, "MANUAL", "播报记录已清除")
    }

    // ============================================================
    // 内部
    // ============================================================

    /** 根据是否有 traceId 自动推导来源 */
    private fun source(traceId: String): String = if (traceId.isNotBlank()) "AI" else "MANUAL"
}
