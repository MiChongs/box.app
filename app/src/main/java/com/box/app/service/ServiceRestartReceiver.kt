package com.box.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 用于在 onTaskRemoved 后由 AlarmManager 触发服务重启。
 * 之所以使用 BroadcastReceiver 而不是直接 startForegroundService，是因为
 * Android 8+ 不允许在后台直接启动前台服务，但 BroadcastReceiver.onReceive()
 * 属于前台上下文，可以调用 startForegroundService()。
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        BoxForegroundService.ensureRunning(context)
    }

    companion object {
        private const val REQUEST_CODE = 9901

        fun scheduleRestart(context: Context, delayMs: Long = 2_000L) {
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, ServiceRestartReceiver::class.java),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
        }
    }
}
