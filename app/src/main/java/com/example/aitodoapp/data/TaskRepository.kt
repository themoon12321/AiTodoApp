package com.example.aitodoapp.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

object TaskRepository {

    @PublishedApi internal val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    @PublishedApi internal var dataDir: File? = null

    fun init(context: Context) {
        dataDir = context.filesDir
    }

    fun fileExists(filename: String): Boolean {
        val dir = dataDir ?: return false
        return File(dir, filename).exists()
    }

    inline fun <reified T> load(filename: String): List<T> {
        val dir = dataDir ?: return emptyList()
        val file = File(dir, filename)
        val bakFile = File(dir, "$filename.bak")

        // 主文件存在 → 尝试读取
        if (file.exists()) {
            try {
                val text = file.readText()
                if (text.isNotBlank()) return json.decodeFromString<List<T>>(text)
            } catch (e: Exception) {
                e.printStackTrace()
                // 主文件损坏 → 尝试备份
            }
        }

        // 主文件缺失或损坏 → 从备份恢复
        if (bakFile.exists()) {
            try {
                val text = bakFile.readText()
                if (text.isNotBlank()) {
                    val data = json.decodeFromString<List<T>>(text)
                    // 从备份成功恢复 → 写回主文件
                    try { file.writeText(text) } catch (_: Exception) {}
                    return data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return emptyList()
    }

    inline fun <reified T> save(filename: String, data: List<T>) {
        val dir = dataDir ?: return
        val file = File(dir, filename)
        val bakFile = File(dir, "$filename.bak")
        val tmpFile = File(dir, "$filename.tmp")

        try {
            // 先写入临时文件（如果崩溃，原始文件不受影响）
            tmpFile.writeText(json.encodeToString(data))

            // 原子替换：当前文件 → 备份，临时文件 → 当前文件
            if (file.exists()) {
                bakFile.delete()
                file.renameTo(bakFile)
            }
            tmpFile.renameTo(file)

            // 写入成功，清理备份
            bakFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            tmpFile.delete()  // 清理临时文件
        }
    }
}
