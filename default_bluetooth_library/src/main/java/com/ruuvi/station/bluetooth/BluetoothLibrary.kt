package com.ruuvi.station.bluetooth

import android.app.Application
import com.ruuvi.station.bluetooth.util.ScannerSettings

object BluetoothLibrary {
    internal lateinit var bluetoothInteractor: BluetoothInteractor
    internal val scanIntervalMilliseconds
        get() = bluetoothInteractor.settings.getBackgroundScanIntervalMilliseconds()
    val isInitialized
        get() = this::bluetoothInteractor.isInitialized

    fun getBluetoothInteractor(
            application: Application,
            onTagsFoundListener: IRuuviTagScanner.OnTagFoundListener,
            settings: ScannerSettings): BluetoothInteractor {
        if (!isInitialized) {
            bluetoothInteractor = BluetoothInteractor(application, onTagsFoundListener, settings)
        }
        return bluetoothInteractor
    }
}