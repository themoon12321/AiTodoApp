package com.example.aitodoapp.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

object SettingsRepository {

    private const val FILE_NAME = "settings.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
    }

    fun load(): Settings {
        val f = file ?: return Settings()
        if (!f.exists()) return Settings()
        return try {
            val text = f.readText()
            if (text.isBlank()) Settings()
            else json.decodeFromString<Settings>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val bak = File(f.absolutePath + ".bak")
                if (bak.exists()) {
                    val text = bak.readText()
                    if (text.isNotBlank()) return json.decodeFromString<Settings>(text)
                }
            } catch (_: Exception) {}
            Settings()
        }
    }

    fun save(settings: Settings) {
        val f = file ?: return
        val bak = File(f.absolutePath + ".bak")
        try {
            if (f.exists()) { if (bak.exists()) bak.delete(); f.renameTo(bak) }
            f.writeText(json.encodeToString(settings))
            if (bak.exists()) bak.delete()
        } catch (e: Exception) { e.printStackTrace() }
    }

    @Serializable
    data class Settings(
        val apiUrl: String = "https://api.deepseek.com/chat/completions",
        val apiKey: String = "",
        val model: String = "deepseek-v4-flash",
        val showOverdueInline: Boolean = true,
        val longPressChat: Boolean = true,
        val showTokenUsage: Boolean = false,
        val autoSyncCalendar: Boolean = false,
        val defaultReminderMinutes: Int = 30,
        val defaultDurationMinutes: Int = 60,
        val reportEnabled: Boolean = false,
        val morningReportTime: String = "07:00",
        val eveningReportTime: String = "21:00"
    )
}
