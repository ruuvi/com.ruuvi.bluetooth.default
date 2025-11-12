package com.ruuvi.station.bluetooth

import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.ruuvi.station.bluetooth.util.ScannerSettings
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.kodein
import org.kodein.di.generic.instance
import timber.log.Timber
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.abs

class BluetoothForegroundService : Service(), KodeinAware {
    override val kodein: Kodein by kodein()
    private val scannerSettings: ScannerSettings by instance()
    val bluetoothInteractor: BluetoothInteractor by instance()
    private val handler = Handler(Looper.getMainLooper())

    private var scanner = object : Runnable {
        var lastWidgetUpdate = 0L
        override fun run() {
            Timber.d("Start scanning in foreground service")
            bluetoothInteractor.startScan(true)
            Timer(false).schedule(bluetoothInteractor.getWorkTime()) {
                bluetoothInteractor.stopScanningFromBackground()
            }
            if (abs(Date().time - lastWidgetUpdate) > WIDGET_UPDATE_INTERVAL) {
                scannerSettings.getSimpleWidgetUpdatePendingIntent()?.let {
                    it.send()
                }
                scannerSettings.getComplexWidgetUpdatePendingIntent()?.let {
                    it.send()
                }
                lastWidgetUpdate = Date().time
            }
            val interval = scannerSettings.getBackgroundScanIntervalMilliseconds()
            Timber.d("Scheduling scanning with interval = $interval")
            handler.postDelayed(this, interval)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("Starting foreground service for bluetooth scan")
        createNotificationChannel()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, CHANNEL_ID)
        } else {
            NotificationCompat.Builder(this)
        }
        with(builder) {
            setSmallIcon(scannerSettings.getNotificationIconId())
            setContentTitle(scannerSettings.getNotificationTitle())
            setStyle(NotificationCompat.BigTextStyle().bigText(scannerSettings.getNotificationText()))
            setNumber(0)
            setShowWhen(false)
            scannerSettings.getNotificationPendingIntent()?.let { pendingIntent ->
                setContentIntent(pendingIntent)
            }
        }

        val interval = scannerSettings.getBackgroundScanIntervalMilliseconds()
        Timber.d("Scheduling scanning with interval = $interval")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, builder.build(), FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ID, builder.build())
        }

        handler.postDelayed(scanner, interval)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Timber.d("Destroy of foreground service for bluetooth scan")
        handler.removeCallbacks(scanner)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Timber.d("Creating NotificationChannel ID = $CHANNEL_ID; NAME = $CHANNEL_NAME ")
            val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    IMPORTANCE_LOW
            )
                    .apply {
                        setShowBadge(false)
                    }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val ID = 1337
        const val CHANNEL_ID = "foreground_scanner_channel"
        const val CHANNEL_NAME = "RuuviStation foreground scanner"
        const val WIDGET_UPDATE_INTERVAL = 5 * 60 * 1000

        fun start(context: Context) {
            val serviceIntent = Intent(context, BluetoothForegroundService::class.java)
            Timber.d("starting FS from companion")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}