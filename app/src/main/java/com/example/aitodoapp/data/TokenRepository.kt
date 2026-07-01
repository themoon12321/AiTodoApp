package com.example.aitodoapp.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.time.LocalDate

@Serializable
data class DayTokens(
    val prompt: Int = 0,
    val completion: Int = 0
)

@Serializable
data class TokenRecord(
    val daily: Map<String, DayTokens> = emptyMap()
)

object TokenRepository {

    private const val FILE_NAME = "token.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
    }

    private fun todayKey(): String = LocalDate.now().toString()

    fun load(): TokenRecord {
        val f = file ?: return TokenRecord()
        if (!f.exists()) return TokenRecord()
        return try {
            val text = f.readText()
            if (text.isBlank()) TokenRecord()
            else json.decodeFromString<TokenRecord>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            TokenRecord()
        }
    }

    private fun save(record: TokenRecord) {
        val f = file ?: return
        try { f.writeText(json.encodeToString(record)) }
        catch (e: Exception) { e.printStackTrace() }
    }

    fun recordUsage(prompt: Int, completion: Int) {
        val record = load()
        val key = todayKey()
        val today = record.daily[key] ?: DayTokens()
        val updated = record.daily + (key to DayTokens(
            prompt = today.prompt + prompt,
            completion = today.completion + completion
        ))
        save(record.copy(daily = updated))
    }

    fun getTodayTokens(): DayTokens {
        val record = load()
        return record.daily[todayKey()] ?: DayTokens()
    }

    fun getTotalTokens(): DayTokens {
        val record = load()
        var totalPrompt = 0
        var totalCompletion = 0
        for (entry in record.daily) {
            totalPrompt += entry.value.prompt
            totalCompletion += entry.value.completion
        }
        return DayTokens(prompt = totalPrompt, completion = totalCompletion)
    }
}
