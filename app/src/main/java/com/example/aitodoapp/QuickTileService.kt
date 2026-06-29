package com.example.aitodoapp

import android.annotation.SuppressLint
import android.content.Intent
import android.service.quicksettings.TileService

@SuppressLint("NewApi")
class QuickTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_chat", true)
        }
        startActivityAndCollapse(intent)
    }
}
