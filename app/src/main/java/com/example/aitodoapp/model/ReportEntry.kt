package com.example.aitodoapp.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Immutable
@Serializable
data class ReportEntry(
    val content: String,
    val isMorning: Boolean,
    val date: String,  // LocalDate.toString()
    val isRead: Boolean = false
) {
    val title: String get() = if (isMorning) "🌅 早间播报" else "🌙 晚间播报"
}
