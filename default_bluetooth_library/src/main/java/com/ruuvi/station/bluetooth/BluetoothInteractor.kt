package com.ruuvi.station.bluetooth

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.ruuvi.station.bluetooth.util.Foreground
import com.ruuvi.station.bluetooth.util.ScannerSettings
import timber.log.Timber
import java.util.*

class BluetoothInteractor(
        private val application: Application,
        private val onTagsFoundListener: IRuuviTagScanner.OnTagFoundListener,
        val settings: ScannerSettings
) {
    private var isRunningInForeground = false

    private var ruuviRangeNotifier: IRuuviTagScanner =
        RuuviTagScanner(application, "BluetoothInteractor")

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private val isApi31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
                startForegroundService()
                if (!isApi31) startBackgroundScanning()
            } else {
                Timber.d("background scanning disabled")
            }
        }
    }

    fun startForegroundService() {
        BluetoothForegroundService.start(application)
    }

    fun stopForegroundService() {
        Timber.d("stopForegroundService")
        val serviceIntent = Intent(application, BluetoothForegroundService::class.java)
        application.stopService(serviceIntent)
    }

    init {
        Foreground.init(application)

        Foreground.get().addListener(listener)
    }

    fun startForegroundScanning() {
        Timber.d("startForegroundScanning")
        stopForegroundService()
        if (!isApi31) ScanningPeriodicReceiver.cancel(application)
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

    fun readLogs(id: String, from: Date?, listener: IRuuviGattListener): Boolean {
        Timber.d("readLogs")
        return ruuviRangeNotifier.connect(id, from, listener)
    }

    fun getFwVersion(macAddress: String, listener: IRuuviGattListener): Boolean {
        Timber.d("getFwVersion $macAddress")
        return ruuviRangeNotifier.getFwVersion(macAddress, listener)
    }

    fun disconnect(id: String): Boolean {
        Timber.d("gatt disconnect")
        return ruuviRangeNotifier.disconnect(id)
    }

    fun stopScanning() {
        Timber.d("stopScanning")
        ruuviRangeNotifier.stopScanning()
    }

    fun stopScanningFromBackground() {
        Timber.d("stopScanningFromBackground (isRunningInForeground = $isRunningInForeground)")
        if (!isRunningInForeground) ruuviRangeNotifier.stopScanning()
    }

    fun restoreBluetoothScan() {
        Timber.d("restoring interactor")
        stopScanning()
        ruuviRangeNotifier = RuuviTagScanner(application, "BluetoothInteractor")
    }

    fun canScan() = ruuviRangeNotifier.canScan()

    // This should return for how long background scan should be activated (depends on BackgroundScanInterval) (ms)
    // For interval of 60min - work time will be 1m4s
    fun getWorkTime(): Long = 4000 + settings.getBackgroundScanIntervalMilliseconds() / 60
}