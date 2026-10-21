package io.github.echu2237.sidebar.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import io.github.echu2237.sidebar.MainActivity
import io.github.echu2237.sidebar.R
import io.github.echu2237.sidebar.data.AppEntry
import io.github.echu2237.sidebar.data.AppRepository
import io.github.echu2237.sidebar.data.FavoritesRepository
import io.github.echu2237.sidebar.dp
import io.github.echu2237.sidebar.overlay.EdgeTriggerView
import io.github.echu2237.sidebar.overlay.PanelView
import io.github.echu2237.sidebar.shizuku.ShizukuManager
import io.github.echu2237.sidebar.util.SbLog

/**
 * Foreground service: owns two overlay windows, the right-edge trigger strip and the sidebar panel.
 */
class SidebarService : Service() {

    companion object {
        const val ACTION_STOP = "io.github.echu2237.sidebar.action.STOP"
        private const val CHANNEL_ID = "sidebar_service_hidden"
        private const val LEGACY_CHANNEL_ID = "sidebar_service"
        private const val NOTIFICATION_ID = 1

        /** Lets the settings page show the running state */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var triggerView: EdgeTriggerView? = null
    private var panelView: PanelView? = null
    private var parkedPanel: PanelView? = null
    private var closingPanel: PanelView? = null
    private var panelAnimator: ValueAnimator? = null
    private var panelWidthPx = 0
    private var panelHeightPx = 0
    private var panelOpenedAtMs = 0L
    private var freeformUnavailableNotified = false
    private var lastDragFraction = 0f
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_need_overlay_permission, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        isRunning = true
        startAsForeground()
        showTrigger()
        // When Shizuku is already authorized, pre-bind the shell service so the first floating-window launch is instant
        if (ShizukuManager.isPermissionGranted) {
            Thread { ShizukuManager.bindShell(this) }.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        removeAllViews()
        // Unbind the Shizuku UserService: otherwise every service lifecycle leaves behind a resident
        // io.github.echu2237.sidebar:sidebar_shell process (measured to accumulate, about 100MB+ RSS each)
        runCatching { ShizukuManager.unbindShell(this) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- overlay management ----------

    private fun showTrigger() {
        val dm = resources.displayMetrics
        val trigger = EdgeTriggerView(
            context = this,
            panelWidthPx = dp(96f), // matches the panel width (drag progress is computed from it)
            onTap = { openPanel() },
            onDragStart = { beginPanelPreview() },
            onDragProgress = { fraction -> updatePanelPreview(fraction) },
            onDragSettle = { open -> settlePanelPreview(open) },
        )
        // Trigger strip: window hugs the right edge, visual handle is drawn flush at the far right.
        // Window is 14dp wide: the rightmost ~20dp inside apps is intercepted by the HyperOS back-gesture zone (system level),
        // an inherent limitation of the edge-hugging approach; height is limited to the middle 30% of the screen.
        val params = WindowManager.LayoutParams(
            dp(14f),
            (dm.heightPixels * 0.3f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0 // flush with the edge
        }
        windowManager.addView(trigger, params)
        triggerView = trigger
    }

    // ---------- Finger-following drag preview ----------

    /**
     * (Re)bind the Shizuku shell service on demand.
     * The shell service exits on idle and may have quit on its own since last use;
     * re-binding while the panel opens means it is usually ready by the time the user taps an icon.
     */
    private fun ensureShellBound() {
        if (!ShizukuManager.isPermissionGranted) return
        if (ShizukuManager.isBound) return
        Thread { ShizukuManager.bindShell(this) }.start()
    }

    /** Finger starts dragging: put the parked panel into "preview" state (visible but not taking touches) */
    private fun beginPanelPreview() {
        if (panelView != null || closingPanel != null) return
        ensureShellBound()
        val panel = obtainPanel() ?: return
        panelView = panel
        // The window is already at its normal position (never moved after creation); preview only moves the content with the finger
        // Suppress spurious OUTSIDE dismissal during preview
        panelOpenedAtMs = SystemClock.uptimeMillis()
        panel.alpha = 1f
        panel.translationX = panelWidthPx.toFloat()
        loadAppsInto(panel)
    }

    private fun updatePanelPreview(fraction: Float) {
        val panel = panelView ?: return
        lastDragFraction = fraction
        panel.translationX = panelWidthPx * (1f - fraction)
    }

    /** Release: drag progress decides whether to expand or snap back and collapse */
    private fun settlePanelPreview(open: Boolean) {
        val panel = panelView ?: return
        SbLog.d("drag settle: fraction=$lastDragFraction open=$open")
        if (open) {
            panelOpenedAtMs = SystemClock.uptimeMillis()
            wakePanelWindow(panel)
            setTriggerVisible(false) // hide the handle once the panel is expanded
            animatePanel(panel, panel.translationX, 0f, 200L, onEnd = null)
        } else {
            panelView = null
            closingPanel = panel
            animatePanel(panel, panel.translationX, panelWidthPx.toFloat(), 160L) {
                parkPanel(panel)
                setTriggerVisible(true)
                if (closingPanel === panel) closingPanel = null
            }
        }
    }

    /** Hide the handle while the panel is expanded, restore after collapse (View-level switch, no window operations) */
    private fun setTriggerVisible(visible: Boolean) {
        triggerView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Only move the window position (touch flags unchanged) */
    private fun positionPanelWindow(panel: PanelView, x: Int) {
        (panel.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            if (lp.x == x && lp.gravity == (Gravity.END or Gravity.CENTER_VERTICAL)) return
            lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            lp.x = x
            try {
                windowManager.updateViewLayout(panel, lp)
            } catch (_: Exception) {
            }
        }
    }

    /** Take the parked panel, or create a new one (windows are never destroyed after creation) */
    private fun obtainPanel(): PanelView? {
        parkedPanel?.let {
            parkedPanel = null
            it.alpha = 1f
            return it
        }
        if (panelView != null) return panelView
        val dm = resources.displayMetrics
        val panelWidth = dp(96f) // native MIUI style: narrow icon strip panel
        panelWidthPx = panelWidth
        panelHeightPx = (dm.heightPixels * 0.72f).toInt()
        val p = PanelView(
            this,
            onAppClick = { app -> launchApp(app) },
            onDismiss = {
                // Ignore OUTSIDE for 350ms after opening (the finger lifting from the summon gesture immediately fires one OUTSIDE)
                if (SystemClock.uptimeMillis() - panelOpenedAtMs > 350) hidePanel()
            },
        )
        val params = WindowManager.LayoutParams(
            panelWidth,
            (dm.heightPixels * 0.72f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // does not take touches during preview
            // Note: do not use FLAG_BLUR_BEHIND — HyperOS implements cross-window blur for third-party overlays
            // very poorly; user testing showed it causes lag and even full-device freezes (reported in stage 1, reproduced in stage 5)
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
            windowAnimations = 0 // stop the system (HyperOS) from applying default enter/exit animations to the overlay window
        }
        p.translationX = panelWidth.toFloat()
        windowManager.addView(p, params)
        return p
    }

    /** Wake the window: only restores touchability (position/size/gravity are never changed, reducing system-side anomalies) */
    private fun wakePanelWindow(panel: PanelView) {
        (panel.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try {
                windowManager.updateViewLayout(panel, lp)
            } catch (_: Exception) {
            }
        }
    }

    /** Load the app list asynchronously (filtered by favorites) to fill the panel */
    private fun loadAppsInto(panel: PanelView) {
        Thread {
            val all = AppRepository.loadLaunchableApps(this)
            val favorites = FavoritesRepository.selectedAppsBlocking(this)
            val apps = if (favorites.isEmpty()) all else all.filter { it.packageName in favorites }
            mainHandler.post { if (panelView === panel) panel.setApps(apps) }
        }.start()
    }

    private fun openPanel() {
        SbLog.d("openPanel: panelView=$panelView closing=$closingPanel parked=$parkedPanel")
        if (panelView != null || closingPanel != null) return

        ensureShellBound()
        panelOpenedAtMs = SystemClock.uptimeMillis()
        val panel = obtainPanel() ?: return
        panelView = panel
        wakePanelWindow(panel)
        setTriggerVisible(false) // hide the handle once the panel is expanded
        panel.alpha = 1f
        panel.translationX = panelWidthPx.toFloat()
        animatePanel(panel, panelWidthPx.toFloat(), 0f, 260L, onEnd = null)
        loadAppsInto(panel)
    }

    private fun hidePanel(then: (() -> Unit)? = null) {
        SbLog.d("hidePanel: panelView=$panelView")
        val panel = panelView ?: run { then?.invoke(); return }
        panelView = null
        closingPanel = panel
        val target = maxOf(panel.width, panelWidthPx).toFloat()
        animatePanel(panel, panel.translationX, target, 180L) {
            parkPanel(panel)
            setTriggerVisible(true) // restore the handle after collapse
            if (closingPanel === panel) closingPanel = null
            then?.invoke()
        }
    }

    /**
     * "Park" the collapsed panel: keep the window alive but invisible and untouchable.
     *
     * Frame-by-frame screen-recording evidence: while removeView destroys the window, no matter where the window
     * was parked first or whether alpha is 0, SurfaceFlinger replays the window fully open for 1~2 frames (a +11.3/255
     * brightness jump, exactly the signature of the fully open panel) — this is the flash at the end of collapse.
     * Therefore the panel window is never destroyed after creation (except when the service stops); collapse = park.
     */
    private fun parkPanel(panel: PanelView) {
        (panel.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            // Parking = window stays at its original size in place, just untouchable.
            // This is the stable approach, proven over long daily use.
            // Note: never resize the window (the 1x1 experiment froze the whole device),
            // and never removeView (flash replayed during destruction).
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                windowManager.updateViewLayout(panel, lp)
            } catch (_: Exception) {
            }
        }
        parkedPanel = panel
    }

    /**
     * View transform animations (GPU compositing, no per-frame IPC overhead):
     * - Open: slide in + slight bounce
     * - Close: slide out + fade throughout (alpha drops linearly to 0 with progress),
     *   slide and fade are visible together, so collapse is a smooth "slide-out fade" rather than vanishing instantly
     * - Only after onEnd is the window removed / the follow-up action run (e.g. startActivity)
     */
    private fun animatePanel(
        panel: PanelView,
        from: Float,
        to: Float,
        duration: Long,
        onEnd: (() -> Unit)?,
    ) {
        panelAnimator?.cancel()
        panel.translationX = from
        val closing = to != 0f
        val anim = ValueAnimator.ofFloat(from, to).apply {
            interpolator = if (!closing) OvershootInterpolator(1.05f) else DecelerateInterpolator()
            this.duration = duration
            addUpdateListener { animation ->
                panel.translationX = animation.animatedValue as Float
                if (closing) {
                    panel.alpha = 1f - animation.animatedFraction
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke()
                }
            })
        }
        panelAnimator = anim
        anim.start()
    }

    private fun removeAllViews() {
        panelAnimator?.cancel()
        panelAnimator = null
        triggerView?.let { safelyRemoveView(it) }
        triggerView = null
        panelView?.let { safelyRemoveView(it) }
        panelView = null
        closingPanel?.let { safelyRemoveView(it) }
        closingPanel = null
        parkedPanel?.let { safelyRemoveView(it) }
        parkedPanel = null
    }

    private fun safelyRemoveView(view: android.view.View) {
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // View not yet attached or already removed
        }
    }

    // ---------- Launching apps ----------

    private fun launchApp(app: AppEntry) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Key: launch only after the panel has fully collapsed. Otherwise the system's launch-transition screenshot captures
        // the not-yet-collapsed panel — the main source of the "fully open frame flashes during collapse" bug.
        hidePanel(then = {
            Thread {
                val freeformLaunched = tryFreeformLaunch(app)
                if (!freeformLaunched) {
                    // Hint once when floating windows are unavailable (avoid bothering on every tap)
                    if (!freeformUnavailableNotified) {
                        freeformUnavailableNotified = true
                        val hint = if (!ShizukuManager.isShizukuInstalled) {
                            R.string.toast_shizuku_missing
                        } else {
                            R.string.toast_shizuku_inactive
                        }
                        mainHandler.post {
                            Toast.makeText(this, getString(hint), Toast.LENGTH_LONG).show()
                        }
                    }
                    if (intent != null) {
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            mainHandler.post {
                                Toast.makeText(this, getString(R.string.toast_launch_failed, app.label), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        mainHandler.post {
                            Toast.makeText(this, getString(R.string.toast_launch_failed, app.label), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.start()
        })
    }

    /**
     * Launch in a freeform floating window via Shizuku (shell identity).
     * Stage 3 testing: am start --windowingMode 5 can pull other apps into mode=freeform on this device (HyperOS3/Android16).
     * Returns false on failure or when not authorized, falling back to a normal launch.
     */
    private fun tryFreeformLaunch(app: AppEntry): Boolean {
        if (!ShizukuManager.isPermissionGranted) return false
        val cmd = ShizukuManager.freeformStartCommand(app.packageName, app.activityName)
        val result = ShizukuManager.exec(cmd)
        SbLog.d("freeform launch -> $result")
        if (result == null) {
            // Shell service lost (possibly reclaimed by the system): re-bind, and fall back to fullscreen launch this time
            runCatching { ShizukuManager.bindShell(this) }
            return false
        }
        return result.startsWith("exit=0")
    }

    // ---------- Foreground notification ----------

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel importance set to NONE: the foreground service runs as usual, but the notification is not shown in the shade.
            // Use a new channel id (importance of an existing channel cannot be changed once created, and the system remembers user changes)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_NONE,
                ),
            )
            // Clean up the old channel (it is visible; leaving it would add an extra entry in notification settings)
            runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
        }
        val contentPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, SidebarService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(contentPi)
            .addAction(0, getString(R.string.notif_stop), stopPi)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
