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
            Settings()
        }
    }

    fun save(settings: Settings) {
        val f = file ?: return
        try { f.writeText(json.encodeToString(settings)) }
        catch (e: Exception) { e.printStackTrace() }
    }

    @Serializable
    data class Settings(
        val apiUrl: String = "https://api.deepseek.com/chat/completions",
        val apiKey: String = "",
        val model: String = "deepseek-v4-flash",
        val showOverdueInline: Boolean = true,
        val longPressChat: Boolean = true
    )
}
