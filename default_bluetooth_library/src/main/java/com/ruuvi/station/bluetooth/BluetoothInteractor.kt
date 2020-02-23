package com.ruuvi.station.bluetooth

import android.app.Application
import com.ruuvi.station.bluetooth.util.Foreground
import com.ruuvi.station.bluetooth.util.ScannerSettings
import timber.log.Timber

class BluetoothInteractor(
        private val application: Application,
        private var onTagsFoundListener: IRuuviTagScanner.OnTagFoundListener
) {
    var settings = ScannerSettings()

    private var isRunningInForeground = false

    private val ruuviRangeNotifier: IRuuviTagScanner by lazy {
        RuuviRangeNotifier(application, "BluetoothInteractor")
    }

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
            } else {
                Timber.d("background scanning disabled")
            }
        }
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

    fun canScan() = ruuviRangeNotifier.canScan()
}