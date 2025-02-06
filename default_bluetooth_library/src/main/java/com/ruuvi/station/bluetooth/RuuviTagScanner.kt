package com.ruuvi.station.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.ruuvi.station.bluetooth.decoder.BleScanResult
import com.ruuvi.station.bluetooth.gatt.NordicGattManager
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean

class RuuviTagScanner(
        private val context: Context,
        private val from: String
) : IRuuviTagScanner {

    private val bluetoothPermissionInteractor = BluetoothPermissionsInteractor(context)
    private var tagListener: IRuuviTagScanner.OnTagFoundListener? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private val devices: ConcurrentMap<String, BleScanResult> = ConcurrentHashMap()
    private val gattManagers: ConcurrentMap<String, NordicGattManager> = ConcurrentHashMap()
    private var isLeExtendedAdvertisingSupported: Boolean = false

    private val scanSettings: ScanSettings
        get() {
            val scanSettings =
                ScanSettings.Builder()
                        .setReportDelay(0)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                scanSettings.setLegacy(false)
            }
            return scanSettings.build()
        }

    private val isScanning = AtomicBoolean(false)
    private val sequenceMap = HashMap<String, Int>()

    init {
        Timber.d("[$from] Setting up range notifier")
        initScanner()
    }

    private fun initScanner() {
        Timber.d("Trying to initialize bluetooth adapter")
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            isLeExtendedAdvertisingSupported = bluetoothManager.adapter.isLeExtendedAdvertisingSupported
        }
        scanner = bluetoothAdapter?.bluetoothLeScanner
    }

    @SuppressLint("MissingPermission")
    override fun startScanning(
            foundListener: IRuuviTagScanner.OnTagFoundListener
    ) {
        Timber.d("[$from] startScanning")

        if (!canScan()) {
            Timber.d("Can't scan bluetoothAdapter is null")
            initScanner()
            if (!canScan()) return
        }
        if (!isScanning.compareAndSet(false, true)) {
            Timber.d("Already scanning!")
            return
        }

        this.tagListener = foundListener
        scanner?.startScan(getScanFilters(), scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    override fun canScan(): Boolean =
        bluetoothAdapter != null &&
            scanner != null &&
            bluetoothPermissionInteractor.requiredPermissionsGranted() &&
            bluetoothAdapter?.state == BluetoothAdapter.STATE_ON

    override fun connect(macAddress: String, readLogsFrom: Date?, listener: IRuuviGattListener): Boolean {
        val device = devices[macAddress]
        device.let {
            it?.let { leResult ->
                var gattManager = gattManagers[macAddress]
                if (gattManager == null) {
                    gattManager = NordicGattManager(context, leResult.device)
                    gattManagers[macAddress] = gattManager
                }
                gattManager.setCallBack(listener)
                gattManager.getLogs(readLogsFrom)
            }
        }
        return device != null
    }

    override fun getFwVersion(macAddress: String, listener: IRuuviGattListener): Boolean {
        val device = devices[macAddress]
        device.let {
            it?.let { leResult ->
                var gattManager = gattManagers[macAddress]
                if (gattManager == null) {
                    gattManager = NordicGattManager(context, leResult.device)
                    gattManagers[macAddress] = gattManager
                }
                gattManager.setCallBack(listener)
                gattManager.getVersion()
            }
        }
        return device != null
    }

    override fun disconnect(macAddress: String): Boolean {
        Timber.d("disconnect $macAddress")
        gattManagers[macAddress]?.let { manager ->
            manager.executeDisconnect()
            manager.setCallBack(null)
            return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    override fun stopScanning() {
        if (!canScan()) return
        Timber.d("[$from] stopScanning isScanning = $isScanning")
        scanner?.stopScan(scanCallback)
        isScanning.set(false)
    }

    private var scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            Timber.d("[$from] onScanResult $result")
            super.onScanResult(callbackType, result)
            result?.let {
                val leresult = BleScanResult(
                    it.device,
                    it.rssi,
                    it.scanRecord?.bytes,
                    isLeExtendedAdvertisingSupported
                )
                val parsed = leresult.parse()

                var connectable = it.scanRecord?.deviceName != null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !connectable) {
                    connectable = it.isConnectable
                }

                if (connectable) {
                    devices[leresult.device.address] = leresult
                } else {
                    connectable = gattManagers[it.device.address]?.isConnected == true
                }

                if (parsed != null) {
                    parsed.connectable = connectable
                    sendDataToListener(parsed)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.d("[$from] onScanFailed error code = $errorCode")
            super.onScanFailed(errorCode)
        }
    }

    private fun sendDataToListener(tag: FoundRuuviTag) {
        if (tag.measurementSequenceNumber != null) {
            val lastSequenceNumber = sequenceMap[tag.id]

            if (lastSequenceNumber == null || tag.measurementSequenceNumber != lastSequenceNumber) {
                tagListener?.onTagFound(tag)

                tag.id?.let {id ->
                    tag.measurementSequenceNumber?.let { sequenceNumber ->
                        sequenceMap[id] = sequenceNumber
                    }
                }
            } else {
                Timber.d("Measurement skipped for ${tag.id} sequenceNumber = ${tag.measurementSequenceNumber}")
            }
        } else {
            tagListener?.onTagFound(tag)
        }
    }

    private fun getScanFilters(): List<ScanFilter> {
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
}