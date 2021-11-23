package com.ruuvi.station.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BluetoothPermissionsInteractor(val context: Context) {
    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val requiredPermissionsApi31 = mutableListOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    fun requiredPermissionsGranted(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            requiredPermissionsApi31.all { isPermissionGranted(it) }
        } else {
            requiredPermissions.all { isPermissionGranted(it) }
        }
    }

    private fun isPermissionGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}