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
import java.util.concurrent.atomic.AtomicBoolean

class RuuviRangeNotifier(
        private val context: Context,
        private val from: String
) : IRuuviTagScanner {

    private var tagListener: IRuuviTagScanner.OnTagFoundListener? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var tagConnections = mutableListOf<GattConnection>()
    private var bluetoothDevices = mutableListOf<LeScanResult>()

    private val scanSettings: ScanSettings
        get() = ScanSettings.Builder()
                .setReportDelay(0)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

    private val isScanning = AtomicBoolean(false)
    private val crashResolver = BluetoothCrashResolver(context)
    private val sequenceMap = HashMap<String, Int>()

    init {
        Timber.d("[$from] Setting up range notifier")
        initScanner()
    }

    private fun initScanner() {
        Timber.d("Trying to initialize bluetooth adapter")
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        scanner = bluetoothAdapter?.bluetoothLeScanner
    }

    @SuppressLint("MissingPermission")
    override fun startScanning(
            foundListener: IRuuviTagScanner.OnTagFoundListener
    ) {
        Timber.d("[$from] startScanning")
        crashResolver.start()

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
        bluetoothAdapter != null && scanner != null && bluetoothAdapter?.state == BluetoothAdapter.STATE_ON

    fun getTagConnection(macAddress: String): GattConnection? {
        return tagConnections.findLast { it.mBluetoothGatt.device.address.toString() == macAddress }
    }

    override fun connect(macAddress: String, readLogsFrom: Date?, listener: IRuuviGattListener): Boolean {
        bluetoothDevices.find { x -> x.device.address == macAddress }.let {
            it?.let { leResult ->
                val gattConnection = getTagConnection(macAddress)
                if (gattConnection == null) {
                    val gatt = GattConnection(context, leResult.device)
                    gatt.setOnRuuviGattUpdate(listener)
                    val connected = gatt.connect(context, readLogsFrom)
                    if (connected)
                        tagConnections.add(gatt)
                    return connected
                } else {
                    gattConnection.let { gatt ->
                        gatt.mBluetoothGatt.close()
                        gatt.setOnRuuviGattUpdate(listener)
                        return gatt.connect(context, readLogsFrom)
                    }
                }
            }
            return it != null
        }
    }

    override fun disconnect(macAddress: String): Boolean {
        val connection = getTagConnection(macAddress)
        if (connection != null) {
            connection.disconnect()
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
                val leresult = LeScanResult()
                leresult.device = it.device
                leresult.rssi = it.rssi
                leresult.scanData = it.scanRecord?.bytes
                val parsed = leresult.parse()
                if (parsed != null) {
                    var connectable = it.scanRecord?.deviceName != null
                    if (connectable) {
                        val idx = bluetoothDevices.indexOfFirst { x -> x.device.address == leresult.device.address }
                        if (idx != -1) {
                            bluetoothDevices[idx] = leresult
                        } else {
                            bluetoothDevices.add(leresult)
                        }
                    } else if (getTagConnection(it.device.address)?.isConnected == true) {
                        connectable = true
                    }
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
}
