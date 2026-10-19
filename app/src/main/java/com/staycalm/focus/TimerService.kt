package com.staycalm.focus

import android.app.Service
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Persistent foreground countdown and, when explicitly enabled, the focus app overlay.
 * Overlay monitoring runs only for an unfinished focus session.
 */
class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var watcher: FocusAppWatcher
    private val pulse = object : Runnable {
        override fun run() {
            val store = FocusStore(this@TimerService)
            if (store.completeIfDue()) {
                watcher.hide()
                stopSelf()
                return
            }
            val state = store.state()
            if (!shouldStayRunning(store, state)) {
                watcher.hide()
                stopSelf()
                return
            }
            watcher.tick(state, store.blockDistractingApps())
            val notificationIsMissing = getSystemService(NotificationManager::class.java)
                .activeNotifications.none { it.id == 1002 }
            if (notificationIsMissing && (store.lockTimerNotification() ||
                FocusAppBlocker.keepAlive(this@TimerService, state))) {
                Notice.sessionStarted(this@TimerService, state.endWall, state.mode)
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        watcher = FocusAppWatcher(this)
        val state = FocusStore(this).state()
        startForeground(1002, Notice.sessionNotification(this, state.endWall, state.mode))
        activeService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = FocusStore(this)
        val state = store.state()
        startForeground(1002, Notice.sessionNotification(this, state.endWall, state.mode))
        handler.removeCallbacks(pulse)
        if (store.completeIfDue() || !shouldStayRunning(store, store.state())) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.post(pulse)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        watcher.hide()
        if (activeService === this) activeService = null
        val store = FocusStore(this)
        val state = store.state()
        val unlocked = state.running && !store.lockTimerNotification() &&
            !SessionUninstallGuard.keepTimerAlive(this) && !FocusAppBlocker.keepAlive(this, state)
        stopForeground(if (unlocked) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        if (unlocked) Notice.sessionStarted(this, state.endWall, state.mode)
        super.onDestroy()
    }

    private fun shouldStayRunning(store: FocusStore, state: FocusState): Boolean =
        FocusAppBlocker.keepAlive(this, state) ||
            (state.running && (store.lockTimerNotification() || SessionUninstallGuard.keepTimerAlive(this)))

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val startQueued = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile private var activeService: TimerService? = null

        fun stop(context: Context) {
            val app = context.applicationContext
            mainHandler.post {
                val store = FocusStore(app)
                val state = store.state()
                val shouldRun = FocusAppBlocker.keepAlive(app, state) ||
                    (state.running && (store.lockTimerNotification() || SessionUninstallGuard.keepTimerAlive(app)))
                if (!shouldRun) activeService?.stopSelf()
            }
        }

        fun ensureRunning(context: Context) {
            if (!startQueued.compareAndSet(false, true)) return
            val app = context.applicationContext
            mainHandler.post {
                startQueued.set(false)
                val store = FocusStore(app)
                val state = store.state()
                val shouldRun = FocusAppBlocker.keepAlive(app, state) ||
                    (state.running && (store.lockTimerNotification() || SessionUninstallGuard.keepTimerAlive(app)))
                if (!shouldRun) {
                    stop(app)
                    if (state.running) Notice.sessionStarted(app, state.endWall, state.mode)
                    return@post
                }
                try {
                    app.startForegroundService(Intent(app, TimerService::class.java))
                } catch (_: IllegalStateException) {
                    if (state.running) Notice.sessionStarted(app, state.endWall, state.mode)
                } catch (_: SecurityException) {
                    if (state.running) Notice.sessionStarted(app, state.endWall, state.mode)
                }
            }
        }
    }
}
