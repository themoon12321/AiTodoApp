package com.example.aitodoapp.data

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object CalendarSyncHelper {

    private var cachedCalendarId = -1L
    private val systemZone = ZoneId.systemDefault()

    private fun getCalendarId(context: Context): Long {
        if (cachedCalendarId > 0) return cachedCalendarId
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, arrayOf("_id"), null, null, null
        )
        cursor?.use {
            while (it.moveToNext()) { val id = it.getLong(0); if (id > 0) { cachedCalendarId = id; return id } }
        }
        return -1L
    }

    private fun addReminder(context: Context, eventId: Long, minutes: Int) {
        try {
            val rem = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, rem)
        } catch (_: Exception) {}
    }

    /** 创建全天事件（无具体时间时使用） */
    private fun insertAllDayEvent(context: Context, title: String, date: LocalDate, calId: Long, reminderMinutes: Int): Long? {
        val startMillis = date.atStartOfDay(systemZone).toInstant().toEpochMilli()
        val endMillis = startMillis + 86400000L
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.EVENT_TIMEZONE, systemZone.id)
            put(CalendarContract.Events.ALL_DAY, 1)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = uri.lastPathSegment?.toLong() ?: return null
        if (reminderMinutes >= 0) addReminder(context, eventId, reminderMinutes)
        return eventId
    }

    /** 创建计时事件（duration<=0 展示为 15 分钟短事件标记，自动防跨天） */
    private fun insertTimedEvent(context: Context, title: String, date: LocalDate, time: LocalTime, durationMinutes: Int, calId: Long, reminderMinutes: Int): Long? {
        val startDateTime = ZonedDateTime.of(date, time, systemZone)
        val effectiveMinutes = if (durationMinutes <= 0) 15 else durationMinutes
        val endDateTime = startDateTime.plusMinutes(effectiveMinutes.toLong())
        val dayEnd = startDateTime.toLocalDate().atTime(java.time.LocalTime.MAX).atZone(systemZone)
        val actualEnd = if (endDateTime.isAfter(dayEnd)) startDateTime.plusMinutes(15) else endDateTime
        val startMillis = startDateTime.toInstant().toEpochMilli()
        val endMillis = actualEnd.toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.EVENT_TIMEZONE, systemZone.id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = uri.lastPathSegment?.toLong() ?: return null
        if (reminderMinutes >= 0) addReminder(context, eventId, reminderMinutes)
        return eventId
    }

    /** 创建日历事件。有时段则创建计时事件，否则全天事件 */
    fun createEvent(context: Context, title: String, date: LocalDate, reminderMinutes: Int = 30, time: LocalTime? = null, durationMinutes: Int = 60): Long? {
        var calId = getCalendarId(context)
        if (calId > 0) {
            try {
                val eid = if (time != null) insertTimedEvent(context, title, date, time, durationMinutes, calId, reminderMinutes)
                          else insertAllDayEvent(context, title, date, calId, reminderMinutes)
                if (eid != null) return eid
            } catch (_: Exception) {}
            cachedCalendarId = -1
        }
        for (id in 1L..5L) {
            try {
                val eid = if (time != null) insertTimedEvent(context, title, date, time, durationMinutes, id, reminderMinutes)
                          else insertAllDayEvent(context, title, date, id, reminderMinutes)
                if (eid != null) { cachedCalendarId = id; return eid }
            } catch (_: Exception) { continue }
        }
        return null
    }

    fun deleteEvent(context: Context, eventId: Long) {
        try {
            context.contentResolver.delete(CalendarContract.Reminders.CONTENT_URI, "event_id = ?", arrayOf(eventId.toString()))
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, "_id = ?", arrayOf(eventId.toString()))
        } catch (_: Exception) {}
    }
}
