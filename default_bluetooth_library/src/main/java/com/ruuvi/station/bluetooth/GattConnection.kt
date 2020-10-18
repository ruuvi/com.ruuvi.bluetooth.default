package com.ruuvi.station.bluetooth

import android.bluetooth.*
import android.content.Context
import net.swiftzer.semver.SemVer
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.*

class GattConnection(context: Context, device: BluetoothDevice, private val from: Date?) {
    lateinit var mBluetoothGatt: BluetoothGatt
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

    var isReadingLogs = false

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
        isReadingLogs = true
        val readAll = 0x3A3A11
        val now = System.currentTimeMillis() / 1000
        var then: Long = 0
        from?.let {
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
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!isReadingLogs) {
                    listener?.connected(true)
                    log("Connected")
                    mBluetoothGatt.discoverServices()
                } else {
                    gatt.disconnect()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.connected(false)
                log("Disconnected")
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

                            if (version.compareTo(logVersion) != -1) {
                                log("Tag has log firmware, reading..")
                                listener?.deviceInfo(model, firmware, true)
                                registerToNordicRxTx()
                                return
                            }
                        } catch (e: Exception) {
                            log("Failed to parse FW")
                        }
                        listener?.deviceInfo(model, firmware, false)
                        mBluetoothGatt.disconnect()
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
                listener?.heartbeat(data.toHexString())
                if (!isReadingLogs) {
                    readLog()
                }
            } else {
                if (data.toHexString().endsWith("ffffffffffffffff")) {
                    // end of logs
                    val oddData = logs.filter { x -> x.temperature == 0.toDouble() && x.humidity == 0.toDouble() && x.pressure == 0.toDouble() }
                    if (oddData.isNotEmpty()) {
                        logs.removeAll { x -> x.temperature == 0.toDouble() && x.humidity == 0.toDouble() && x.pressure == 0.toDouble() }
                    }
                    listener?.dataReady(logs)
                    mBluetoothGatt.disconnect()
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

    init {
        Timber.d("Connecting to GATT on ${device.address}")
        mBluetoothGatt = device.connectGatt(context, false, mGattCallback)
    }
}
