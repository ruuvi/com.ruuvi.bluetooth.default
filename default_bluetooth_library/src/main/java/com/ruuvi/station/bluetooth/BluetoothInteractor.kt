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
        val settings: ScannerSettings
) {
    private var isRunningInForeground = false

    private var ruuviRangeNotifier: IRuuviTagScanner =
            RuuviRangeNotifier(application, "BluetoothInteractor")

    private val listener: Foreground.Listener = object : Foreground.Listener {
        override fun onBecameForeground() {
            Timber.d("ruuvi onBecameForeground start foreground scanning")

            isRunningInForeground = true

            startForegroundScanning()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(serviceIntent)
        } else {
            application.startService(serviceIntent)
        }
    }

    fun stopForegroundService() {
        Timber.d("stopForegroundService")
        val serviceIntent = Intent(application, BluetoothForegroundService::class.java)
        application.stopService(serviceIntent)
    }

    init {

        Foreground.init(application)

        // foreground listener does not trigger the first onforeground, so first just set it to true
        isRunningInForeground = true

        Foreground.get().addListener(listener)
    }

    fun startForegroundScanning() {
        Timber.d("startForegroundScanning")
        ScanningPeriodicReceiver.cancel(application)
        stopForegroundService()
        startScan()
    }

    fun startBackgroundScanning() {
        Timber.d("startBackgroundScanning")
        ScanningPeriodicReceiver.start(application, settings.getBackgroundScanIntervalMilliseconds())
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

    fun restoreBluetoothScan() {
        Timber.d("restoring interactor")
        stopScanning()
        ruuviRangeNotifier = RuuviRangeNotifier(application, "BluetoothInteractor")
    }

    fun canScan() = ruuviRangeNotifier.canScan()
}