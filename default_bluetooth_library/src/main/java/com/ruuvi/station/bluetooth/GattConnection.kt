package com.ruuvi.station.bluetooth

import android.bluetooth.*
import android.content.Context
import android.os.Build
import net.swiftzer.semver.SemVer
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.schedule

class GattConnection(context: Context, var device: BluetoothDevice) {
    lateinit var mBluetoothGatt: BluetoothGatt
    var syncFrom: Date? = null
    var listener: IRuuviGattListener? = null
    var logs = mutableListOf<LogReading>()

    private val infoService: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    private val nordicRxTxService: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    private val manufacturerCharacteristic: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    private val modelCharacteristics: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    private val firmwareCharacteristics: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

    private val nordicRxCharacteristic: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private val nordicTxCharacteristic: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    var manufacturer = ""
    var model = ""
    var firmware = ""

    var shouldReadLogs = true
    var retryConnectionCounter = 0
    val MAX_CONNECT_RETRY = 3
    var isConnected = false
    var syncedPoints = 0

    fun setOnRuuviGattUpdate(listener: IRuuviGattListener) {
        this.listener = listener
    }

    fun log(message: String) {
        Timber.d("${mBluetoothGatt.device.address} GATT: $message")
    }

    private fun writeRXCharacteristic(value: ByteArray): Boolean {
        val rxService: BluetoothGattService = mBluetoothGatt.getService(nordicRxTxService)
                ?: //Service not supported
                return false
        val rxChar = rxService.getCharacteristic(nordicRxCharacteristic)
                ?: // service not supported
                return false
        rxChar.value = value
        log("writeRXCharacteristic ${value.toHexString()}")
        return mBluetoothGatt.writeCharacteristic(rxChar)
    }

    private val HEX_CHARS = "0123456789ABCDEF"

    fun String.hexStringToByteArray(): ByteArray {

        val result = ByteArray(length / 2)

        for (i in 0 until length step 2) {
            val firstIndex = HEX_CHARS.indexOf(this[i]);
            val secondIndex = HEX_CHARS.indexOf(this[i + 1]);

            val octet = firstIndex.shl(4).or(secondIndex)
            result.set(i.shr(1), octet.toByte())
        }

        return result
    }

    fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID) {
        val ser = mBluetoothGatt.getService(serviceUUID)
        if (ser == null) {
            log("serviceUUID $serviceUUID is null")
            mBluetoothGatt.disconnect()
            return
        }
        val char = ser.getCharacteristic(characteristicUUID)
        if (char == null) {
            log("characteristicUUID $characteristicUUID is null")
            mBluetoothGatt.disconnect()
            return
        }
        mBluetoothGatt.readCharacteristic(char)
    }

    fun registerToNordicRxTx() {
        val ser = mBluetoothGatt.getService(nordicRxTxService)
        if (ser == null) {
            log("nordicRxTxService is null")
            mBluetoothGatt.disconnect()
            return
        }
        val char = ser.getCharacteristic(nordicTxCharacteristic)
        if (char == null) {
            log("nordicTxCharacteristic is null")
            mBluetoothGatt.disconnect()
            return
        }

        // get heartbeats
        mBluetoothGatt.setCharacteristicNotification(char, true)
        val descriptor: BluetoothGattDescriptor = char.getDescriptor(CCCD);
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        mBluetoothGatt.writeDescriptor(descriptor)
    }

    fun readLog() {
        shouldReadLogs = false
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
            mBluetoothGatt.disconnect()
        }
    }

    fun toBytes(time: Long): ByteArray {
        return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(time).array()
    }

    fun toBytes(number: Int): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(number).array()
    }

    fun ByteArray.toHexString() =
            asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }

    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
        ) {
            //log("BTSTATE: " + newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                if (shouldReadLogs) {
                    try {
                        listener?.connected(true)
                    } catch (e: Exception) {
                        log(e.toString())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        gatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_OPTION_NO_PREFERRED)
                    };
                    log("Connected")
                    mBluetoothGatt.discoverServices()
                } else {
                    gatt.disconnect()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (shouldReadLogs && (++retryConnectionCounter) < MAX_CONNECT_RETRY) {
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
                            if (isDebug || version.compareTo(logVersion) != -1) {
                                log("Tag has log firmware, reading..")
                                listener?.deviceInfo(model, firmware, true)
                                registerToNordicRxTx()
                                return
                            }
                        } catch (e: Exception) {
                            log("Failed to parse FW")
                        }
                        shouldReadLogs = false
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

        private fun byteToInt(bytes: ByteArray): Int {
            var result = 0.toUInt()
            var shift = 0
            for (byte in bytes.reversed()) {
                val uByte = byte.toUByte()
                result = result or (uByte.toUInt() shl shift)
                shift += 8
            }
            return result.toInt()
        }

        private fun byteToLong(bytes: ByteArray): Long {
            var result = 0.toULong()
            var shift = 0
            for (byte in bytes.reversed()) {
                val uByte = byte.toUByte()
                result = result or (uByte.toULong() shl shift)
                shift += 8
            }
            return result.toLong()
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
                val time = Date(byteToLong(timestamp) * 1000)
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
                    val temp = byteToInt(value) / 100.0
                    logs[idx].temperature = temp
                }
                if (type.contentEquals("3A3110".hexStringToByteArray())) {
                    val humidity = byteToLong(value).toFloat() / 100.0
                    logs[idx].humidity = humidity
                }
                if (type.contentEquals("3A3210".hexStringToByteArray())) {
                    val pressure = byteToLong(value).toDouble()
                    logs[idx].pressure = pressure
                }
            }
        }
    }

    fun connect(context: Context, fromDate: Date?): Boolean {
        Timber.d("Connecting to GATT on ${device.address}")
        shouldReadLogs = true
        logs.clear()
        syncFrom = fromDate
        syncedPoints = 0
        val gattConnection = device.connectGatt(context, false, mGattCallback) ?: return false
        mBluetoothGatt = gattConnection
        return true
    }

    fun disconnect() {
        shouldReadLogs = false
        mBluetoothGatt.disconnect()
    }
}
