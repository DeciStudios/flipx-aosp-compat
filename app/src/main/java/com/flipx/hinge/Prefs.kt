package com.flipx.hinge

import android.content.Context

object Prefs {
    private const val NAME = "flipx_prefs"
    private const val KEY_OPEN = "open_launcher_pkg"
    private const val KEY_CLOSE = "close_launcher_pkg"
    private const val KEY_HINGE_OPEN = "hinge_is_open"
    private const val KEY_ORIENTATION_LOCK = "orientation_lock"
    private const val KEY_SERVICE_MODE = "service_mode"
    private const val KEY_EVENT_DEVICE = "event_device"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun openLauncher(ctx: Context): String =
        prefs(ctx).getString(KEY_OPEN, "") ?: ""

    fun closeLauncher(ctx: Context): String =
        prefs(ctx).getString(KEY_CLOSE, "") ?: ""

    fun setOpenLauncher(ctx: Context, pkg: String) {
        prefs(ctx).edit().putString(KEY_OPEN, pkg).apply()
    }

    fun setCloseLauncher(ctx: Context, pkg: String) {
        prefs(ctx).edit().putString(KEY_CLOSE, pkg).apply()
    }

    fun isHingeOpen(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_HINGE_OPEN, true)

    fun setHingeOpen(ctx: Context, open: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_HINGE_OPEN, open).apply()
    }

    /** Whether flipx should lock the display rotation based on hinge state:
     *  open → portrait, closed → landscape. Default false. */
    fun orientationLock(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ORIENTATION_LOCK, false)

    fun setOrientationLock(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ORIENTATION_LOCK, enabled).apply()
    }

    fun serviceMode(ctx: Context): String =
        prefs(ctx).getString(KEY_SERVICE_MODE, "shizuku") ?: "shizuku"

    fun setServiceMode(ctx: Context, mode: String) {
        prefs(ctx).edit().putString(KEY_SERVICE_MODE, mode).apply()
    }

    fun eventDevice(ctx: Context): String =
        prefs(ctx).getString(KEY_EVENT_DEVICE, "/dev/input/event1") ?: "/dev/input/event1"

    fun setEventDevice(ctx: Context, device: String) {
        prefs(ctx).edit().putString(KEY_EVENT_DEVICE, device).apply()
    }
}
