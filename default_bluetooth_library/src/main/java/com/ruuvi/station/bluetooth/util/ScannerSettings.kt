package com.ruuvi.station.bluetooth.util

import android.app.PendingIntent

interface ScannerSettings {
    fun allowBackgroundScan(): Boolean
    fun getBackgroundScanIntervalMilliseconds(): Long
    fun getNotificationIconId(): Int
    fun getNotificationTitle(): String
    fun getNotificationText(): String
    fun getNotificationPendingIntent(): PendingIntent?
}