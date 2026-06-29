package com.example.aitodoapp.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.aitodoapp.data.AiService
import com.example.aitodoapp.data.AiAction
import com.example.aitodoapp.data.TaskRepository
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.Task
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class VoiceDialogActivity : ComponentActivity() {

    private val scope = MainScope()

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text != null) processTask(text)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply { text = "🎤 请说出任务..."; textSize = 20f; gravity = 17; setPadding(40,40,40,40) }
        setContentView(tv)

        TaskRepository.init(applicationContext)
        SettingsRepository.init(applicationContext)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出代办任务")
        }
        try { voiceLauncher.launch(intent) }
        catch (e: Exception) { finish() }
    }

    private fun processTask(text: String) {
        (findViewById<android.widget.FrameLayout>(android.R.id.content)?.getChildAt(0) as? TextView)?.text = "⏳ 正在处理..."
        scope.launch {
            try {
                val result = AiService.processMessage(text)
                result.actions.forEach { action ->
                    if (action is AiAction.CreateTask) {
                        val existing = TaskRepository.load<Task>("tasks.json")
                        TaskRepository.save("tasks.json", existing + Task(
                            title = action.title, priority = action.priority,
                            tags = action.tags, deadline = action.deadline
                        ))
                    }
                }
            } catch (_: Exception) {}
            android.os.Handler(mainLooper).postDelayed({ if (!isFinishing) finish() }, 1500)
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.launch { /* 取消协程 */ } }
}
