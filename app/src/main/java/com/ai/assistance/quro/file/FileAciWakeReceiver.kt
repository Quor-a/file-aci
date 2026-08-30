package com.ai.assistance.quro.file

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ACI Wake Receiver — ZorvAI sends ACTION_WAKE broadcast to wake up this process
 * before attempting to bind to FileAciService.
 */
class FileAciWakeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FileACI"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Wake received: ${intent?.action}")
        // Process is now alive; ZorvAI can proceed to bind to FileAciService.
    }
}
