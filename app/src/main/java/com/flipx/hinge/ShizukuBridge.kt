package com.flipx.hinge

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuBridge {

    private const val TAG = "FlipxHinge"
    private const val PERM_REQ_CODE = 1001

    // Bump on every UserService code change — Shizuku restarts the daemon when the
    // version increments (otherwise it keeps the old loaded class running).
    private const val SERVICE_VERSION = 18

    @Volatile var service: IUserService? = null
        private set

    @Volatile private var appCtx: Context? = null
    @Volatile private var rootService: HingeUserService? = null

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                "com.flipx.hinge",
                HingeUserService::class.java.name
            )
        )
            .daemon(true)
            .processNameSuffix("hinge_watcher")
            .debuggable(false)
            .version(SERVICE_VERSION)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                val svc = IUserService.Stub.asInterface(binder)
                service = svc
                Log.i(TAG, "UserService connected")
                runCatching {
                    appCtx?.let { ctx ->
                        svc.setEventDevice(Prefs.eventDevice(ctx))
                    }
                }.onFailure { Log.w(TAG, "setEventDevice err: ${it.message}") }
                runCatching { svc.startWatch() }
                    .onFailure { Log.w(TAG, "startWatch err: ${it.message}") }
                // Sync the user's launcher picks into the daemon's in-memory fields
                // so it can detect "are we on a configured launcher right now."
                appCtx?.let { ctx ->
                    runCatching {
                        svc.setLaunchers(Prefs.openLauncher(ctx), Prefs.closeLauncher(ctx))
                    }.onFailure { Log.w(TAG, "setLaunchers err: ${it.message}") }
                    // Reapply the orientation-lock preference — wm flag resets across reboots
                    runCatching {
                        svc.setOrientationLock(Prefs.orientationLock(ctx))
                    }.onFailure { Log.w(TAG, "setOrientationLock err: ${it.message}") }
                }
                listener?.onConnected()
            } else {
                Log.w(TAG, "UserService binder invalid")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (appCtx?.let { Prefs.serviceMode(it) } != "root") {
                service = null
                Log.i(TAG, "UserService disconnected")
                listener?.onDisconnected()
            }
        }
    }

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onPermissionResult(granted: Boolean)
    }

    @Volatile var listener: Listener? = null

    private val permListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == PERM_REQ_CODE) {
            val granted = result == PackageManager.PERMISSION_GRANTED
            listener?.onPermissionResult(granted)
            if (granted) bind()
        }
    }

    fun init(context: Context) {
        appCtx = context.applicationContext
        val ctx = context.applicationContext
        if (Prefs.serviceMode(ctx) == "root") {
            startRootMode(ctx)
            return
        }
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky { tryBindOrRequest() }
        Shizuku.addBinderDeadListener {
            if (appCtx?.let { Prefs.serviceMode(it) } != "root") {
                service = null
                listener?.onDisconnected()
            }
        }
    }

    fun startRootMode(ctx: Context) {
        if (rootService == null) {
            val svc = HingeUserService()
            svc.setUseSu(true)
            svc.setEventDevice(Prefs.eventDevice(ctx))
            svc.setLaunchers(Prefs.openLauncher(ctx), Prefs.closeLauncher(ctx))
            svc.setOrientationLock(Prefs.orientationLock(ctx))
            svc.startWatch()
            rootService = svc
            service = svc
            Log.i(TAG, "Root Service started directly")
            listener?.onConnected()
        }
    }

    fun stopRootMode() {
        rootService?.stopWatch()
        rootService?.destroy()
        rootService = null
        service = null
        listener?.onDisconnected()
    }

    fun switchMode(ctx: Context, mode: String) {
        Prefs.setServiceMode(ctx, mode)
        if (mode == "root") {
            unbind()
            startRootMode(ctx)
        } else {
            stopRootMode()
            tryBindOrRequest()
        }
    }

    fun tryBindOrRequest() {
        val ctx = appCtx ?: return
        if (Prefs.serviceMode(ctx) == "root") {
            startRootMode(ctx)
            return
        }
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku not running")
            return
        }
        if (hasPermission()) bind() else Shizuku.requestPermission(PERM_REQ_CODE)
    }

    fun hasPermission(): Boolean {
        val ctx = appCtx ?: return false
        if (Prefs.serviceMode(ctx) == "root") return true
        if (!Shizuku.pingBinder()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun isShizukuAlive(): Boolean = Shizuku.pingBinder()

    /** Push launcher picks to the running daemon, e.g. after the user changes them. */
    fun pushLaunchers(ctx: Context) {
        val svc = service ?: return
        runCatching {
            svc.setLaunchers(Prefs.openLauncher(ctx), Prefs.closeLauncher(ctx))
        }.onFailure { Log.w(TAG, "pushLaunchers err: ${it.message}") }
    }

    fun pushEventDevice(ctx: Context) {
        val svc = service ?: return
        runCatching {
            svc.setEventDevice(Prefs.eventDevice(ctx))
        }.onFailure { Log.w(TAG, "pushEventDevice err: ${it.message}") }
    }

    private fun bind() {
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Throwable) {
            Log.e(TAG, "bindUserService failed", e)
        }
    }

    fun unbind() {
        if (appCtx?.let { Prefs.serviceMode(it) } != "root") {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
        service = null
    }
}
