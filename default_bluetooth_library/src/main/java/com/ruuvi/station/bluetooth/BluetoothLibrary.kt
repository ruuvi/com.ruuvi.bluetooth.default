package com.ruuvi.station.bluetooth

import android.app.Application
import com.ruuvi.station.bluetooth.contract.FoundRuuviTag
import com.ruuvi.station.bluetooth.contract.IRuuviTagScanner
import com.ruuvi.station.bluetooth.decoder.BleScanResult
import com.ruuvi.station.bluetooth.util.ScannerSettings
import com.ruuvi.station.bluetooth.util.extensions.hexStringToByteArray

object BluetoothLibrary {
    internal lateinit var bluetoothInteractor: BluetoothInteractor
    internal val scanIntervalMilliseconds
        get() = bluetoothInteractor.settings.getBackgroundScanIntervalMilliseconds()
    val isInitialized
        get() = this::bluetoothInteractor.isInitialized

    fun getBluetoothInteractor(
        application: Application,
        onTagsFoundListener: IRuuviTagScanner.OnTagFoundListener,
        settings: ScannerSettings): BluetoothInteractor
    {
        if (!isInitialized) {
            bluetoothInteractor = BluetoothInteractor(application, onTagsFoundListener, settings)
        }
        return bluetoothInteractor
    }

    fun decode(id: String, rawData: String, rssi: Int): FoundRuuviTag {
        val data = rawData.hexStringToByteArray()
        return BleScanResult.from(id, null, data, rssi) ?: throw Exception("Failed to decode id = $id data = [$rawData]")
    }
}