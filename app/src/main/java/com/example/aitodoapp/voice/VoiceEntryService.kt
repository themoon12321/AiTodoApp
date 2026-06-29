package com.example.aitodoapp.voice

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent

class VoiceEntryService : AccessibilityService() {

    private var lastVolDown = 0L
    private val doubleTapMs = 500L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolDown < doubleTapMs) {
                lastVolDown = 0L
                vibrate()
                triggerVoice()
                return true
            }
            lastVolDown = now
            mainHandler.postDelayed({ lastVolDown = 0L }, doubleTapMs)
        }
        return false
    }

    private fun triggerVoice() {
        try {
            val intent = Intent(this, VoiceDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun vibrate() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= 26)
            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(80)
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(e: android.view.accessibility.AccessibilityEvent?) {}
}
