package com.smartmotionrecorder.util

import android.content.Context

object MonitoringStateStore {
    private const val PREF_NAME = "monitor_state"
    private const val KEY_EXPECTED = "expected_monitoring"
    private const val KEY_MODE = "monitor_mode"
    private const val KEY_HEARTBEAT = "heartbeat_ms"
    private const val KEY_LAST_STOP_REASON = "last_stop_reason"
    private const val KEY_LAST_TAMPER_MSG = "last_tamper_msg"
    private const val KEY_LAST_TAMPER_TIME = "last_tamper_time"
    private const val KEY_ANTI_TAMPER_ENABLED = "anti_tamper_enabled"
    private const val KEY_PREVIEW_ACTIVE = "preview_active"

    fun setAntiTamperEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ANTI_TAMPER_ENABLED, enabled).commit()
    }

    fun isAntiTamperEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ANTI_TAMPER_ENABLED, true)
    }

    fun markMonitoringStarted(context: Context, mode: String) {
        prefs(context).edit()
            .putBoolean(KEY_EXPECTED, true)
            .putString(KEY_MODE, mode)
            .putLong(KEY_HEARTBEAT, System.currentTimeMillis())
            .commit()
    }

    fun heartbeat(context: Context) {
        if (!isExpected(context)) return
        prefs(context).edit()
            .putLong(KEY_HEARTBEAT, System.currentTimeMillis())
            .commit()
    }

    fun markMonitoringStopped(context: Context, reason: String, expectedStop: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_EXPECTED, false)
            .putString(KEY_MODE, null)
            .putString(KEY_LAST_STOP_REASON, if (expectedStop) reason else "unexpected:$reason")
            .commit()
    }

    fun isExpected(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EXPECTED, false)
    }

    fun getMode(context: Context): String? {
        return prefs(context).getString(KEY_MODE, null)
    }

    fun getLastHeartbeat(context: Context): Long {
        return prefs(context).getLong(KEY_HEARTBEAT, 0L)
    }

    fun setTamperMessage(context: Context, message: String) {
        prefs(context).edit()
            .putString(KEY_LAST_TAMPER_MSG, message)
            .putLong(KEY_LAST_TAMPER_TIME, System.currentTimeMillis())
            .commit()
    }

    fun setPreviewActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREVIEW_ACTIVE, active).commit()
    }

    fun isPreviewActive(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PREVIEW_ACTIVE, false)
    }

    fun consumeTamperMessage(context: Context): String? {
        val prefs = prefs(context)
        val message = prefs.getString(KEY_LAST_TAMPER_MSG, null)
        if (message != null) {
            prefs.edit()
                .remove(KEY_LAST_TAMPER_MSG)
                .remove(KEY_LAST_TAMPER_TIME)
                .commit()
        }
        return message
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
