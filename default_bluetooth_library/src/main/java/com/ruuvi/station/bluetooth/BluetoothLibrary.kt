package com.ruuvi.station.bluetooth

import android.app.Application
import com.ruuvi.station.bluetooth.util.ScannerSettings

object BluetoothLibrary {

    private var bluetoothInteractor: BluetoothInteractor? = null

    fun initLibrary(
            application: Application,
            listner: IRuuviTagScanner.OnTagFoundListener,
            settings: ScannerSettings? = null)
    {
        if (bluetoothInteractor == null)
            bluetoothInteractor = BluetoothInteractor(application, listner)

        settings?.let {
            bluetoothInteractor?.settings = settings
        }
    }

    fun getBluetoothInteractor() : BluetoothInteractor {
        return bluetoothInteractor ?: throw Exception("BluetoothLibrary should be initialized!")
    }
}