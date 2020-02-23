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
        ScanForDevicesService.enqueueWork(context)
        scheduleNext(context)
    }

    companion object {
        private var scanInterval = 0L

        fun start(context: Context, interval: Long) {
            Timber.d("ScanningPeriodicReceiver.start!")
            scanInterval = interval
            schedule(context, interval)
        }

        fun scheduleNext(context: Context) {
            schedule(context, scanInterval)
        }

        fun cancel(context: Context) {
            Timber.d("ScanningPeriodicReceiver.cancel!")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(createPendingIntent(context))
        }

        private fun schedule(context: Context, delay: Long) {
            Timber.d("ScanningPeriodicReceiver.schedule!")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis() + delay

            val alarmIntent = createPendingIntent(context)

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

        private fun createPendingIntent(context: Context): PendingIntent {
            Timber.d("ScanningPeriodicReceiver.createPendingIntent")
            val intent = Intent(context, ScanningPeriodicReceiver::class.java)
            return PendingIntent.getBroadcast(context, 0, intent, 0)
        }
    }

}