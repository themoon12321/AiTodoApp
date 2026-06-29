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
        val bakFile = File(dir, ".bak")
        if (!file.exists()) {
            // Primary file missing — try backup
            if (bakFile.exists()) {
                return try {
                    val text = bakFile.readText()
                    if (text.isBlank()) emptyList()
                    else json.decodeFromString<List<T>>(text)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
            return emptyList()
        }
        return try {
            val text = file.readText()
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<T>>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            // Primary file corrupted — try backup
            if (bakFile.exists()) {
                return try {
                    val text = bakFile.readText()
                    if (text.isBlank()) emptyList()
                    else json.decodeFromString<List<T>>(text)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                    emptyList()
                }
            }
            emptyList()
        }
    }

    inline fun <reified T> save(filename: String, data: List<T>) {
        val dir = dataDir ?: return
        val file = File(dir, filename)
        val bakFile = File(dir, ".bak")
        try {
            // Rename existing to backup before overwriting
            if (file.exists()) {
                bakFile.delete()
                file.renameTo(bakFile)
            }
            file.writeText(json.encodeToString(data))
            // Write succeeded, remove backup
            bakFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            // Write failed — keep backup if it exists
        }
    }
}
