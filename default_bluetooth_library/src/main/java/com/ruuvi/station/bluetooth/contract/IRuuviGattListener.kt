package com.ruuvi.station.bluetooth.contract

interface IRuuviGattListener {
    fun connected(state: Boolean)
    fun deviceInfo(model: String, fw: String, canReadLogs: Boolean, serialNumber: String? = null)
    fun dataReady(data: List<LogReading>)
    fun heartbeat(raw: String)
    fun syncProgress(syncedDataPoints: Int)
    fun error(errorMessage: String)
}