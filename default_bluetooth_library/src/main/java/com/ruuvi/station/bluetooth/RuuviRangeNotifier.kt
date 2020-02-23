package com.ruuvi.station.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.ruuvi.station.bluetooth.decoder.LeScanResult
import timber.log.Timber
import java.util.*

class RuuviRangeNotifier(
        private val context: Context,
        private val from: String
) : IRuuviTagScanner {
    private var tagListener: IRuuviTagScanner.OnTagFoundListener? = null

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scanSettings: ScanSettings by lazy {
        ScanSettings.Builder()
                .setReportDelay(0)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
    }

    private val scanner: BluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var isScanning = false

    init {
        Timber.d("[$from] Setting up range notifier")
    }

    @SuppressLint("MissingPermission")
    override fun startScanning(
            foundListener: IRuuviTagScanner.OnTagFoundListener
    ) {
        Timber.d("[$from] startScanning")
        if (!canScan()) {
            Timber.d("Can't scan bluetoothAdapter is null")
            return
        }
        if (isScanning) {
            Timber.d( "Already scanning!")
            return
        }
        this.tagListener = foundListener
        isScanning = true
        scanner.startScan(getScanFilters(), scanSettings, scanCallback)
    }

    override fun canScan(): Boolean = bluetoothAdapter != null && scanner != null

    @SuppressLint("MissingPermission")
    override fun stopScanning() {
        if (!canScan()) return
        Timber.d( "[$from] stopScanning isScanning = $isScanning")
        scanner.stopScan(scanCallback)
        isScanning = false
    }

    private var scanCallback = object:ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            Timber.d( "[$from] onScanResult $result")
            super.onScanResult(callbackType, result)

            result?.let {
                val leresult = LeScanResult()
                leresult.device = it.device
                leresult.rssi = it.rssi
                leresult.scanData = it.scanRecord?.bytes
                val parsed = leresult.parse()
                if (parsed != null) {
                    tagListener?.onTagFound(parsed)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.d( "[$from] onScanFailed error code = $errorCode")
            super.onScanFailed(errorCode)
        }
    }

    private fun getScanFilters(): List<ScanFilter>? {
        val filters: MutableList<ScanFilter> = ArrayList()
        val ruuviFilter = ScanFilter
                .Builder()
                .setManufacturerData(0x0499, byteArrayOf())
                .build()
        val eddystoneFilter = ScanFilter
                .Builder()
                .setServiceUuid(ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb"))
                .build()
        filters.add(ruuviFilter)
        filters.add(eddystoneFilter)
        return filters
    }

    companion object {
        private const val TAG = "RuuviRangeNotifier"
    }
}
