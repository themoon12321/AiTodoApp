package com.example.aitodoapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TagChip(name: String, isTemporary: Boolean, onDelete: () -> Unit, onClick: () -> Unit = {}) {
    val bg = if (isTemporary) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    val fg = if (isTemporary) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onPrimaryContainer
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (isTemporary) "📌 " else "#", fontSize = 12.sp, color = fg); Text(name, style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.clickable(onClick = onClick))
        Spacer(Modifier.width(6.dp)); Text("✕", fontSize = 12.sp, color = fg.copy(alpha = 0.5f), modifier = Modifier.clickable(onClick = onDelete))
    }
}
