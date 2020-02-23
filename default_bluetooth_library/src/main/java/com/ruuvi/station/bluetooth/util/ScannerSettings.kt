package com.ruuvi.station.bluetooth.util

open class ScannerSettings {
    open fun allowBackgroundScan() = false
    open fun getBackgroundScanInterval() = 60 * 1000L
}