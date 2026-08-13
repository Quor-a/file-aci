package com.ai.assistance.quro.file

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 唤醒 Receiver：ZorvAI 在停止态绑定前先发 ACTION_WAKE 广播拉起本进程（§4.6）。
 * 收到后启动 ACI Service，使后续 AIDL/LocalSocket 绑定能立即成功。
 */
class FileAciWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "ai.aci.core.ACTION_WAKE") {
            try {
                val svc = Intent(context, FileAciService::class.java)
                context.startService(svc)
                Log.i("FileACI", "wake receiver 拉起 FileAciService")
            } catch (e: Throwable) {
                Log.e("FileACI", "wake startService 失败: ${e.message}")
            }
        }
    }
}
