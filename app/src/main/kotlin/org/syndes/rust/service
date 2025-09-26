package org.syndes.rust.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

@Suppress("DEPRECATION")
class WakeLockService {
    private var fullWakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val wakelock_tag = "simpletexteditor:wakelog"
    }

    fun acquireLock(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // keep original flags for compatibility; these are deprecated but used intentionally
        fullWakeLock = powerManager.newWakeLock(
            (PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                or PowerManager.FULL_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP),
            wakelock_tag
        )

        try {
            // acquire for 10 minutes like original
            fullWakeLock?.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e("ERROR", e.toString())
        }
    }

    fun releaseLock() {
        if (fullWakeLock == null) return
        try {
            fullWakeLock?.release()
        } catch (e: Exception) {
            Log.e("ERROR", e.toString())
        }
    }
}
