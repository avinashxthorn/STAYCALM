package com.staycalm.focus

import android.app.Service
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Keeps the locked countdown alive even when no music is playing or Activity is open. */
class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val pulse = object : Runnable {
        override fun run() {
            val store = FocusStore(this@TimerService)
            if (store.completeIfDue() || !store.state().running || !store.lockTimerNotification()) {
                stopSelf()
                return
            }
            // Android 14+ allows swiping an ongoing alert while unlocked. Restore it,
            // including on devices which do not deliver its delete PendingIntent.
            if (getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 1002 }) {
                val state = store.state()
                Notice.sessionStarted(this@TimerService, state.endWall, state.mode)
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = FocusStore(this)
        val state = store.state()
        // Meet the startForegroundService deadline even if pause/completion raced startup.
        startForeground(1002, Notice.sessionNotification(this, state.endWall, state.mode))
        handler.removeCallbacks(pulse)
        if (!state.running || !store.lockTimerNotification() || store.completeIfDue()) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.post(pulse)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        val store = FocusStore(this)
        stopForeground(if (store.state().running && !store.lockTimerNotification())
            STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        fun ensureRunning(context: Context) {
            val store = FocusStore(context)
            val state = store.state()
            if (!state.running) return
            if (!store.lockTimerNotification()) {
                context.stopService(Intent(context, TimerService::class.java))
                Notice.sessionStarted(context, state.endWall, state.mode)
                return
            }
            try {
                context.startForegroundService(Intent(context, TimerService::class.java))
            } catch (_: IllegalStateException) {
                // The OS may forbid a background start; keep the persisted timer + alarm.
                Notice.sessionStarted(context, state.endWall, state.mode)
            } catch (_: SecurityException) {
                Notice.sessionStarted(context, state.endWall, state.mode)
            }
        }
    }
}
