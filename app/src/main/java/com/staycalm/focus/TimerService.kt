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

    override fun onCreate() {
        super.onCreate()
        val state = FocusStore(this).state()
        startForeground(1002, Notice.sessionNotification(this, state.endWall, state.mode))
        activeService = this
    }

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
        if (activeService === this) activeService = null
        val store = FocusStore(this)
        val state = store.state()
        val unlocked = state.running && !store.lockTimerNotification()
        stopForeground(if (unlocked) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        // Rebuild after detaching so no system-added foreground/ongoing flag survives unlocking.
        if (unlocked) Notice.sessionStarted(this, state.endWall, state.mode)
        super.onDestroy()
    }

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val startQueued = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile private var activeService: TimerService? = null

        fun stop(context: Context) {
            val app = context.applicationContext
            mainHandler.post {
                val store = FocusStore(app)
                if (!store.state().running || !store.lockTimerNotification()) {
                    // A pending startup must first enter the foreground, then stop itself.
                    // Stopping it before onCreate can otherwise leave Android's timeout armed.
                    activeService?.stopSelf()
                }
            }
        }

        fun ensureRunning(context: Context) {
            // Request startup only when the main looper can actually create the service.
            // Coalesce rapid start/pause/resume operations and re-read the persisted state
            // rather than launching a foreground service for an already-stopped session.
            if (!startQueued.compareAndSet(false, true)) return
            val app = context.applicationContext
            mainHandler.post {
                startQueued.set(false)
                val store = FocusStore(app)
                val state = store.state()
                if (!state.running) return@post
                if (!store.lockTimerNotification()) {
                    stop(app)
                    Notice.sessionStarted(app, state.endWall, state.mode)
                    return@post
                }
                try {
                    app.startForegroundService(Intent(app, TimerService::class.java))
                } catch (_: IllegalStateException) {
                    // The OS may forbid a background start; keep the persisted timer + alarm.
                    Notice.sessionStarted(app, state.endWall, state.mode)
                } catch (_: SecurityException) {
                    Notice.sessionStarted(app, state.endWall, state.mode)
                }
            }
        }
    }
}
