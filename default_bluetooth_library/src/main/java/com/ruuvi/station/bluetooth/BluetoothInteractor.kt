package com.ruuvi.station.bluetooth

import android.app.Application
import android.content.Intent
import android.os.Build
import com.ruuvi.station.bluetooth.util.Foreground
import com.ruuvi.station.bluetooth.util.ScannerSettings
import timber.log.Timber

class BluetoothInteractor(
        private val application: Application,
        private val onTagsFoundListener: IRuuviTagScanner.OnTagFoundListener,
        val settings: ScannerSettings = ScannerSettings()
) {
    private var isRunningInForeground = false

    private val ruuviRangeNotifier: IRuuviTagScanner by lazy {
        RuuviRangeNotifier(application, "BluetoothInteractor")
    }

    private val listener: Foreground.Listener = object : Foreground.Listener {
        override fun onBecameForeground() {

            Timber.d("ruuvi onBecameForeground start foreground scanning")

            isRunningInForeground = true

            startForegroundScanning()
            stopForegroundService()
        }

        override fun onBecameBackground() {
            Timber.d("ruuvi onBecameBackground start background scanning")
            stopScanning()
            isRunningInForeground = false

            if (settings.allowBackgroundScan()) {
                startBackgroundScanning()
                startForegroundService()
            } else {
                Timber.d("background scanning disabled")
            }
        }
    }

    fun startForegroundService() {
        val serviceIntent = Intent(application, BluetoothForegroundService::class.java)
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(serviceIntent)
        } else {
            application.startService(serviceIntent)
        }
    }

    fun stopForegroundService() {
        val serviceIntent = Intent(application, BluetoothForegroundService::class.java)
        application.stopService(serviceIntent)
    }

    init {
        Foreground.init(application)
        Foreground.get().addListener(listener)
    }

    fun startForegroundScanning() {
        Timber.d("startForegroundScanning")
        ScanningPeriodicReceiver.cancel(application)
        startScan()
    }

    fun startBackgroundScanning() {
        Timber.d("startBackgroundScanning")
        ScanningPeriodicReceiver.start(application, settings.getBackgroundScanInterval())
    }

    fun startScan() {
        Timber.d("startScan")
        ruuviRangeNotifier.startScanning(onTagsFoundListener)
    }

    fun stopScanning() {
        Timber.d("stopScanning")
        ruuviRangeNotifier.stopScanning()
    }

    fun stopScanningFromBackground() {
        Timber.d("stopScanningFromBackground")
        if (!isRunningInForeground) ruuviRangeNotifier.stopScanning()
    }

    fun canScan() = ruuviRangeNotifier.canScan()
}