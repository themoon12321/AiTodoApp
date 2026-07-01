package com.example.aitodoapp.data

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

object CalendarSyncHelper {

    private var cachedCalendarId = -1L
    private val utcZone = ZoneId.of("UTC")

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

    private fun insertEvent(context: Context, title: String, date: LocalDate, calId: Long, reminderMinutes: Int): Long? {
        val startMillis = date.atStartOfDay(utcZone).toInstant().toEpochMilli()
        val endMillis = startMillis + 86400000L
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.ALL_DAY, 1)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = uri.lastPathSegment?.toLong() ?: return null
        if (reminderMinutes >= 0) addReminder(context, eventId, reminderMinutes)
        return eventId
    }

    fun createEvent(context: Context, title: String, date: LocalDate, reminderMinutes: Int = 30): Long? {
        var calId = getCalendarId(context)
        if (calId > 0) {
            try { val eid = insertEvent(context, title, date, calId, reminderMinutes); if (eid != null) return eid } catch (_: Exception) {}
            cachedCalendarId = -1  // 缓存 ID 失效，清除后走 fallback
        }
        for (id in 1L..5L) {
            try {
                val eid = insertEvent(context, title, date, id, reminderMinutes)
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
