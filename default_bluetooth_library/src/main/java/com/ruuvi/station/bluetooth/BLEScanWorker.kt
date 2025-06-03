package com.ruuvi.station.bluetooth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ruuvi.station.bluetooth.BluetoothLibrary.bluetoothInteractor
import kotlinx.coroutines.delay
import timber.log.Timber

class BLEScanWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        //disabled because it works ok without notification
        //setForeground(createForegroundInfo())

        Timber.d("BLEScanWorker doWork")

        bluetoothInteractor.startScan(true)

        delay(10_000) // scan for 10 seconds

        bluetoothInteractor.stopScanningFromBackground()

        Timber.d("BLEScanWorker stop")

        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "ble_scan_worker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val channel = NotificationChannel(
                channelId,
                "BLE Scanning",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Scanning BLE devices")
            .setSmallIcon(bluetoothInteractor.settings.getNotificationIconId())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            ForegroundInfo(1, notification)
        }
    }

    companion object {
        const val MY_SERVICE_UUID = "0000aaaa-0000-1000-8000-00805f9b34fb"
    }
}