package com.ruuvi.station.bluetooth

import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.ruuvi.station.bluetooth.util.extensions.hexStringToByteArray
import com.ruuvi.station.bluetooth.util.extensions.toHexString
import com.ruuvi.station.bluetooth.util.extensions.toInt
import com.ruuvi.station.bluetooth.util.extensions.toLong
import net.swiftzer.semver.SemVer
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.schedule

class GattConnection(context: Context, var device: BluetoothDevice) {
    private lateinit var bluetoothGatt: BluetoothGatt
    var syncFrom: Date? = null
    var listener: IRuuviGattListener? = null
    var logs = mutableListOf<LogReading>()

    var manufacturer = ""
    var model = ""
    var firmware = ""

    var shouldReadLogs = true
    var shouldGetFw = false
    var shouldFinish = false
    var retryConnectionCounter = 0
    var isConnected = false
    var syncedPoints = 0

    fun setOnRuuviGattUpdate(listener: IRuuviGattListener) {
        this.listener = listener
    }

    fun log(message: String) {
        Timber.d("${bluetoothGatt.device.address} GATT: $message")
    }

    fun resetState() {
        retryConnectionCounter = 0
        bluetoothGatt.close()
    }

    private fun writeRXCharacteristic(value: ByteArray): Boolean {
        val rxService: BluetoothGattService = bluetoothGatt.getService(nordicRxTxService)
                ?: //Service not supported
                return false
        val rxChar = rxService.getCharacteristic(nordicRxCharacteristic)
                ?: // service not supported
                return false
        rxChar.value = value
        log("writeRXCharacteristic ${value.toHexString()}")
        return bluetoothGatt.writeCharacteristic(rxChar)
    }

    fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID) {
        val ser = bluetoothGatt.getService(serviceUUID)
        if (ser == null) {
            log("serviceUUID $serviceUUID is null")
            bluetoothGatt.disconnect()
            return
        }
        val char = ser.getCharacteristic(characteristicUUID)
        if (char == null) {
            log("characteristicUUID $characteristicUUID is null")
            bluetoothGatt.disconnect()
            return
        }
        bluetoothGatt.readCharacteristic(char)
    }

    fun registerToNordicRxTx() {
        val ser = bluetoothGatt.getService(nordicRxTxService)
        if (ser == null) {
            log("nordicRxTxService is null")
            bluetoothGatt.disconnect()
            return
        }
        val char = ser.getCharacteristic(nordicTxCharacteristic)
        if (char == null) {
            log("nordicTxCharacteristic is null")
            bluetoothGatt.disconnect()
            return
        }

        // get heartbeats
        bluetoothGatt.setCharacteristicNotification(char, true)
        val descriptor: BluetoothGattDescriptor = char.getDescriptor(CCCD)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        bluetoothGatt.writeDescriptor(descriptor)
    }

    fun readLog() {
        shouldFinish = true
        val readAll = 0x3A3A11
        val now = System.currentTimeMillis() / 1000
        var then: Long = 0
        syncFrom?.let {
            then = it.time / 1000
        }
        log("Reading logs from $then")
        val readAllBytes = toBytes(readAll).copyOfRange(1, 4)
        val nowBytes = toBytes(now).copyOfRange(4, 8)
        val thenBytes = toBytes(then).copyOfRange(4, 8)
        val msg = readAllBytes.plus(nowBytes).plus(thenBytes)
        if (!writeRXCharacteristic(msg)) {
            bluetoothGatt.disconnect()
        }
    }

    fun connect(context: Context, fromDate: Date?): Boolean {
        Timber.d("Connecting to GATT on ${device.address}")
        shouldReadLogs = true
        shouldFinish = false
        logs.clear()
        syncFrom = fromDate
        syncedPoints = 0
        val gattConnection = device.connectGatt(context, false, gattCallback) ?: return false
        bluetoothGatt = gattConnection
        return true
    }

    fun getFwVersion(context: Context): Boolean {
        shouldReadLogs = false
        shouldGetFw = true
        shouldFinish = false
        logs.clear()
        val gattConnection = device.connectGatt(context, false, gattCallback) ?: return false
        bluetoothGatt = gattConnection
        return true
    }

    fun disconnect() {
        shouldFinish = true
        bluetoothGatt.disconnect()
    }

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
        ) {
            //log("BTSTATE: " + newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                if (!shouldFinish) {
                    try {
                        listener?.connected(true)
                    } catch (e: Exception) {
                        log(e.toString())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        gatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_OPTION_NO_PREFERRED)
                    }
                    log("Connected")
                    bluetoothGatt.discoverServices()
                } else {
                    gatt.disconnect()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (!shouldFinish && (++retryConnectionCounter) < MAX_CONNECT_RETRY) {
                    connect(context, syncFrom)
                } else {
                    try {
                        listener?.connected(false)
                    } catch (e: Exception) {
                        log(e.toString())
                    }
                    gatt.close()
                    log("Disconnected")
                }
                // this is here to prevent the tag from showing up as "not connectable"
                // as the the tag will be in disconnected state later than the phone reports
                Timer().schedule(1500) {
                    isConnected = false
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Services discovered")
                readCharacteristic(infoService, manufacturerCharacteristic)
            }
        }

        override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    manufacturerCharacteristic -> {
                        manufacturer = characteristic.getStringValue(0)
                        readCharacteristic(infoService, modelCharacteristics)
                    }
                    modelCharacteristics -> {
                        model = characteristic.getStringValue(0)
                        readCharacteristic(infoService, firmwareCharacteristics)
                    }
                    firmwareCharacteristics -> {
                        log("Reading FW")
                        firmware = characteristic.getStringValue(0)
                        try {
                            val firstNumberIndex = firmware.indexOfFirst { it.isDigit() }
                            firmware = firmware.subSequence(firstNumberIndex, firmware.length).toString()
                            val version = SemVer.parse(firmware)
                            val logVersion: SemVer = SemVer.parse("3.28.12")
                            // assume that all debug firmware can read logs
                            val isDebug = version.buildMetadata != null && version.buildMetadata!!.contains("debug")
                            if (shouldReadLogs && (isDebug || version.compareTo(logVersion) != -1)) {
                                log("Tag has log firmware, reading..")
                                listener?.deviceInfo(model, firmware, true)
                                registerToNordicRxTx()
                                return
                            }
                        } catch (e: Exception) {
                            log("Failed to parse FW")
                        }
                        shouldFinish = true
                        try {
                            listener?.deviceInfo(model, firmware, false)
                        } catch (e: Exception) {
                            log(e.toString())
                        }
                        gatt.disconnect()
                    }
                }
            } else {
                gatt.disconnect()
            }
        }

        override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
        }

        override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val data = characteristic.value
            if (data[0] == 5.toByte()) {
                // heartbeat
                try {
                    listener?.heartbeat(data.toHexString())
                } catch (e: Exception) {
                    log(e.toString())
                }
                if (shouldReadLogs) {
                    readLog()
                }
            } else {
                if (data.toHexString().endsWith("ffffffffffffffff")) {
                    log("DONE with logs")
                    // end of logs
                    val oddData = logs.filter { x -> x.temperature == 0.toDouble() && x.humidity == 0.toDouble() && x.pressure == 0.toDouble() }
                    if (oddData.isNotEmpty()) {
                        logs.removeAll { x -> x.temperature == 0.toDouble() && x.humidity == 0.toDouble() && x.pressure == 0.toDouble() }
                    }
                    try {
                        listener?.dataReady(logs)
                    } catch (e: Exception) {
                        log(e.toString())
                    }
                    log("Calling disconnect")
                    gatt.disconnect()
                }
                val type = data.copyOfRange(0, 3)
                val timestamp = data.copyOfRange(3, 7)
                val value = data.copyOfRange(7, 11)
                val time = Date(timestamp.toLong() * 1000)
                var idx = logs.indexOfFirst { x -> x.date.time == time.time }
                if (idx == -1) {
                    val reading = LogReading()
                    reading.id = device.address
                    reading.date = time
                    logs.add(reading)
                    idx = logs.size - 1
                    syncedPoints++
                    listener?.syncProgress(syncedPoints)
                }
                if (type.contentEquals("3A3010".hexStringToByteArray())) {
                    val temp = value.toInt() / 100.0
                    logs[idx].temperature = temp
                }
                if (type.contentEquals("3A3110".hexStringToByteArray())) {
                    val humidity = value.toLong().toFloat() / 100.0
                    logs[idx].humidity = humidity
                }
                if (type.contentEquals("3A3210".hexStringToByteArray())) {
                    val pressure = value.toLong().toDouble()
                    logs[idx].pressure = pressure
                }
            }
        }
    }

    companion object {
        const val MAX_CONNECT_RETRY = 3
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val infoService: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        private val nordicRxTxService: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val manufacturerCharacteristic: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        private val modelCharacteristics: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        private val firmwareCharacteristics: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        private val nordicRxCharacteristic: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val nordicTxCharacteristic: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

        fun toBytes(time: Long): ByteArray {
            return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(time).array()
        }

        fun toBytes(number: Int): ByteArray {
            return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(number).array()
        }
    }
}