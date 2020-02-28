package com.ruuvi.station.bluetooth

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber
import java.util.*

class ScanningPeriodicReceiver: BroadcastReceiver(){
    override fun onReceive(context: Context, intent: Intent?) {
        Timber.d("ScanningPeriodicReceiver.onReceive!")
        var scanInterval = INTERVAL_DEFAULT
        intent?.let {
            scanInterval = it.getLongExtra(INTERVAL_EXTRA, INTERVAL_DEFAULT)
        }
        ScanForDevicesService.enqueueWork(context)
        scheduleNext(context, scanInterval)
    }

    companion object {
        private const val INTERVAL_EXTRA = "INTERVAL"
        private const val INTERVAL_DEFAULT = 60000L

        fun start(context: Context, interval: Long) {
            Timber.d("ScanningPeriodicReceiver.start! Interval = $interval ms")
            schedule(context, interval)
        }

        fun scheduleNext(context: Context, scanInterval: Long) {
            schedule(context, scanInterval)
        }

        fun cancel(context: Context) {
            Timber.d("ScanningPeriodicReceiver.cancel!")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(createPendingIntent(context, INTERVAL_DEFAULT))
        }

        private fun schedule(context: Context, scanInterval: Long) {
            Timber.d("ScanningPeriodicReceiver.schedule! scanInterval = $scanInterval")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis() + scanInterval

            val alarmIntent = createPendingIntent(context, scanInterval)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        alarmIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
            }
        }

        private fun createPendingIntent(context: Context, scanInterval: Long): PendingIntent {
            Timber.d("ScanningPeriodicReceiver.createPendingIntent")
            val intent = Intent(context, ScanningPeriodicReceiver::class.java)
            intent.putExtra(INTERVAL_EXTRA, scanInterval)
            return PendingIntent.getBroadcast(context, 0, intent, 0)
        }
    }

}