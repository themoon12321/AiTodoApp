package com.example.aitodoapp.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

object TaskRepository {

    @PublishedApi internal val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @PublishedApi internal var dataDir: File? = null

    fun init(context: Context) {
        dataDir = context.filesDir
    }

    inline fun <reified T> load(filename: String): List<T> {
        val dir = dataDir ?: return emptyList()
        val file = File(dir, filename)
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<T>>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    inline fun <reified T> save(filename: String, data: List<T>) {
        val dir = dataDir ?: return
        try {
            File(dir, filename).writeText(json.encodeToString(data))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
