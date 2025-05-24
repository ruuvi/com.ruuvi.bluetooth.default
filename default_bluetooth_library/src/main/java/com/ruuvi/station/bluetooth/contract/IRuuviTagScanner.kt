package com.ruuvi.station.bluetooth.contract

import java.util.*

interface IRuuviTagScanner {

    fun startScanning(foundListener: OnTagFoundListener, background: Boolean = false)

    fun stopScanning()

    fun canScan(): Boolean

    /**
     * Connect and fetch logs from RuuviTag.
     *
     * This function establishes a GATT connection to a given RuuviTag, and then calls [listener].connected.
     * Next it reads the model name and firmware version and calls [listener].deviceInfo with the info.
     * Then it starts to read temperature, humidity and air pressure logs starting from [readLogsFrom].
     * After the logs are read [listener].dataReady will be called with the historical data, and
     * then the connection will be disconnected and [listener].connected will be called.
     * If the firmware version is lower than 3.28.12 the GATT connection will disconnect
     * after reading model and firmware info.
     * If for any reason the connection is dropped [listener].connected will be called.
     *
     * @param macAddress MAC of target tag, e.g. "AA:BB:CC:DD:EE:FF".
     * @param readLogsFrom From what date should the logs be fetched. Passing null will read full history.
     * @param listener Callback for received data.
     * @return true if the tag is in range and connection can be attempted, else false.
     */
    fun connect(macAddress: String, readLogsFrom: Date?, listener: IRuuviGattListener): Boolean

    fun getFwVersion(macAddress: String, listener: IRuuviGattListener): Boolean

    /**
     * Disconnect from GATT device
     *
     * @param macAddress MAC of target
     * @return true if connection exists and disconnect was called
     */
    fun disconnect(macAddress: String): Boolean

    interface OnTagFoundListener {
        fun onTagFound(tag: FoundRuuviTag)
    }
}