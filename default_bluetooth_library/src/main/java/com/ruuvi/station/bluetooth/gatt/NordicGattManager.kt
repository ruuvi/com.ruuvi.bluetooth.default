package com.ruuvi.station.bluetooth.gatt

import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.ruuvi.station.bluetooth.FoundRuuviTag
import com.ruuvi.station.bluetooth.IRuuviGattListener
import com.ruuvi.station.bluetooth.LogReading
import com.ruuvi.station.bluetooth.decoder.validateValues
import com.ruuvi.station.bluetooth.util.extensions.*
import net.swiftzer.semver.SemVer
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest
import no.nordicsemi.android.ble.PhyRequest
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.util.*

class NordicGattManager(context: Context, val device: BluetoothDevice): BleManager(context) {
    private var gattCallback: IRuuviGattListener? = null
    private var actionType: ActionType = ActionType.GET_LOGS
    private var readLogsFrom: Date? = null

    private var logs = mutableListOf<LogReading>()
    private var syncedPoints = 0

    private var model: String? = null
    private var serialNumber: String? = null
    private var firmware: String? = null
    private var manufacturer: String? = null
    private var isAir: Boolean = false
    private var airInitialized = true

    private var modelCharacteristic: BluetoothGattCharacteristic? = null
    private var serialNumberCharacteristic: BluetoothGattCharacteristic? = null
    private var manufacturerCharacteristic: BluetoothGattCharacteristic? = null
    private var firmwareCharacteristic: BluetoothGattCharacteristic? = null
    private var nordicTxCharacteristic: BluetoothGattCharacteristic? = null
    private var nordicRxCharacteristic: BluetoothGattCharacteristic? = null

    override fun getGattCallback(): BleManagerGattCallback = GattCallback()

    fun setCallBack(callback: IRuuviGattListener?) {
        gattCallback = callback
    }

    fun getLogs(readLogsFrom: Date?) {
        Timber.d("$device getLogs readLogsFrom = $readLogsFrom")
        clearState()
        actionType = ActionType.GET_LOGS
        this.readLogsFrom = readLogsFrom
        connectToDevice()
    }

    fun getVersion() {
        Timber.d("$device getVersion")
        clearState()
        actionType = ActionType.GET_VERSION
        readLogsFrom = null
        connectToDevice()
    }

    private fun clearState() {
        Timber.d("$device clearState")
        logs = mutableListOf()
        syncedPoints = 0
        model = null
        serialNumber = null
        firmware = null
        manufacturer = null
    }

    private fun connectToDevice() {
        connect(device)
            .useAutoConnect(false)
            .retry(MAX_CONNECT_RETRY, 1000)
            .usePreferredPhy(PhyRequest.PHY_LE_2M_MASK)
            .timeout(30000)
            .invalid {
                Timber.d("$device connect INVALID")
                gattCallback?.error("connect INVALID")
            }
            .done {
                Timber.d("$device connect DONE")
            }
            .fail { device, status ->
                Timber.d("$device connect FAIL status = $status")
                gattCallback?.error("connect FAIL")
            }
            .enqueue()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH)
                .with { device, interval, latency, timeout ->
                    Timber.d("$device requestConnectionPriority interval = $interval latency = $latency timeout = $timeout")
                }
                .fail { device, status ->
                    Timber.d("$device requestConnectionPriority FAIL status = $status")
                }
                .enqueue()
        }
    }


    private fun checkIfInfoCollected() {
        val fw = firmware
        val model = model
        val manufacturer = manufacturer
        val serialNumberCollectedOrNotExpected = !serialNumber.isNullOrEmpty() || serialNumberCharacteristic == null
        if (fw != null && model != null && manufacturer != null && serialNumberCollectedOrNotExpected && airInitialized) {
            val canReadLogs = canReadLogs()
            Timber.d("$device canReadLogs = $canReadLogs")
            gattCallback?.deviceInfo(model, fw, canReadLogs, serialNumber)
            if (canReadLogs && actionType == ActionType.GET_LOGS) {
                registerToNordicRxTx()
            } else {
                executeDisconnect()
            }
        }
    }

    private fun initializeAir() {
        airInitialized = false
        Timber.d("$device initializeAir mtu = $mtu")
        requestMtu(512)
            .done {
                airInitialized = true
                checkIfInfoCollected()
                Timber.d("$device initialize MTU ADJUSTED = $mtu")
            }
            .fail { device, status ->
                Timber.d("$device initialize MTU set fail = $status")
            }
            .enqueue()
    }

    private fun canReadLogs(): Boolean {
        if (isAir) return true

        firmware?.let { firmware ->
            try {
                val firstNumberIndex = firmware.indexOfFirst { it.isDigit() }
                if (firstNumberIndex == -1) return false
                val version = SemVer.parse(firmware.subSequence(firstNumberIndex, firmware.length).toString())
                return version.compareTo(supportLoggingVersion) != -1
            } catch (e: IllegalArgumentException ) {
                Timber.e(e)
                return false
            }
        }
        return false
    }

    fun executeDisconnect() {
        Timber.d("$device executeDisconnect")
        disconnect()
            .done { device ->
                Timber.d("$device executeDisconnect DONE")
            }
            .fail { device, status ->
                Timber.d("$device executeDisconnect FAIL $status")
            }
            .enqueue()
        gattCallback?.connected(false)
    }

    private fun registerToNordicRxTx() {
        Timber.d("$device registerToNordicRxTx")
        val descriptor = nordicTxCharacteristic?.getDescriptor(CCCD)

        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            .fail { device, status ->
                Timber.d("$device writeDescriptor FAIL status = $status")
                executeDisconnect()
            }
            .enqueue()

        setNotificationCallback(nordicTxCharacteristic)
            .with { device, data ->
                Timber.d("$device notificationCallback nordicTxCharacteristic data = $data")
                if (isAir) {
                    processDataAir(device, data)
                } else {
                    processData(device, data)
                }
            }

        enableNotifications(nordicTxCharacteristic).enqueue()
        startReadingLogs()
    }

    fun ByteArray.asString (): String{
        return this.joinToString (" ") { String.format("%02X", it.toInt() and 0xff) }
    }

    private fun startReadingLogs() {
        Timber.d("$device startReadingLogs")
        val readInterval = getReadInterval()
        Timber.d("$device startReadingLogs readInterval ${readInterval.asString()}")

        nordicRxCharacteristic?.value = readInterval
        writeCharacteristic(nordicRxCharacteristic, readInterval, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            .fail { device, status ->
                Timber.d("$device startReadingLogs writeCharacteristic FAIL status = $status")
                executeDisconnect()
            }
            .enqueue()
    }

    private fun processData(device: BluetoothDevice, dataResponse: Data) {
        dataResponse.value?.let { data ->
            if (data[0] == 5.toByte()) {
                gattCallback?.heartbeat(data.toHexString())
            } else if (data.toHexString().endsWith(historyEnd, ignoreCase = true)) {
                Timber.d("History end")
                logs.removeAll { x -> x.temperature == 0.toDouble() && x.humidity == 0.toDouble() && x.pressure == 0.toDouble() }
                gattCallback?.dataReady(logs)
                executeDisconnect()
            } else {
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
                    gattCallback?.syncProgress(syncedPoints)
                }
                when {
                    type.contentEquals(temperatureType) -> {
                        val temp = value.toInt() / 100.0
                        logs[idx].temperature = temp
                    }
                    type.contentEquals(humidityType) -> {
                        Timber.d("humidity $value")
                        logs[idx].humidity = if (value.contentEquals(nullValue)) {
                            null
                        } else {
                            value.toLong().toFloat() / 100.0
                        }
                    }
                    type.contentEquals(pressureType) -> {
                        Timber.d("pressure $value")
                        logs[idx].pressure = if (value.contentEquals(nullValue)) {
                            null
                        } else {
                            value.toLong().toDouble()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun processDataAir(device: BluetoothDevice, dataResponse: Data) {
        dataResponse.value?.let { data ->
            if (data[0] == 0xE0.toByte()) {
                gattCallback?.heartbeat(data.toHexString())
            } else if (data.toHexString().endsWith(historyEndAir, true)) {
                Timber.d("History end")
                gattCallback?.dataReady(logs)
                executeDisconnect()
            } else {
                val recordsCount = data.getOrNull(3) ?: 0
                val recordLength = data.getOrNull(4) ?: 0
                Timber.d("processDataAir recordsCount = $recordsCount recordLength = $recordLength")
                for (i in 0 ..< recordsCount) {
                    val startIndex = 5 + i * recordLength
                    if (data.getOrNull(startIndex + recordLength - 1) != null) {
                        val recordData = data.copyOfRange(startIndex, startIndex + recordLength)
                        val logReading = decodeAirData(recordData)
                        logReading.id = device.address
                        logs.add(logReading)
                        syncedPoints++
                        gattCallback?.syncProgress(syncedPoints)
                    }
                    else {
                        Timber.d("processDataAir noData ${startIndex+recordLength} ${data.size}")
                    }
                }
            }
        }
    }

    private fun decodeAirData(data: ByteArray): LogReading {
        val timestamp = data.copyOfRange(0, 4)
        val time = Date(timestamp.toLong() * 1000)
        var result = FoundRuuviTag()
        result.dataFormat = DATA_FORMAT
        result.temperature = (((data[TEMPERATURE_POSITION ].toInt() shl 8) or
                (data[TEMPERATURE_POSITION + 1].toInt() and 0xFF)) / 200.0).roundHalfUp(4)
        result.humidity = (((data[HUMIDITY_POSITION].toInt() shl 8) or
                (data[HUMIDITY_POSITION + 1].toInt() and 0xFF)) / 400.0).roundHalfUp(4)
        result.pressure = ((data[PRESSURE_POSITION].toInt() and 0xFF) shl 8 or
                (data[PRESSURE_POSITION + 1].toInt() and 0xFF)).toDouble() + 50000
        result.pm1 = (((data[PM1_POSITION].toInt() and 0xFF) shl 8 or
                (data[PM1_POSITION + 1].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(2)
        result.pm25 = (((data[PM25_POSITION].toInt() and 0xFF) shl 8 or
                (data[PM25_POSITION + 1].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(2)
        result.pm4 = (((data[PM4_POSITION].toInt() and 0xFF) shl 8 or
                (data[PM4_POSITION + 1].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(2)
        result.pm10 = (((data[PM10_POSITION].toInt() and 0xFF) shl 8 or
                (data[PM10_POSITION + 1].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(2)
        result.co2 = ((data[CO2_POSITION].toInt() and 0xFF) shl 8 or
                (data[CO2_POSITION + 1].toInt() and 0xFF))
        result.voc = ((data[VOC_POSITION].toInt() and 0x01) shl 8) or
                (data[VOC_POSITION + 1].toInt() and 0xFF)
        result.nox = ((data[NOX_POSITION].toInt() and 0x01) shl 8) or
                (data[NOX_POSITION + 1].toInt() and 0xFF)
        result.luminosity = ((data[LUMINOSITY_POSITION].toInt() and 0xFF) shl 8) or
                (data[LUMINOSITY_POSITION + 1].toInt() and 0xFF)
        result.dBaAvg = (((data[DBA_AVG_POSITION].toInt() and 0xFF).toDouble()) / 2).roundHalfUp(2)
        result.dBaPeak = (((data[DBA_PEAK_POSITION].toInt() and 0xFF).toDouble()) / 2).roundHalfUp(2)
        result.voltage = ((data[VOLTAGE_POSITION ].toInt() and 0xFF).toDouble() * 0.03).roundHalfUp(4)
        result = validateValues(result)
        Timber.d("processDataAir time = $time DECODED $result" )
        return LogReading(time, result)
    }

    private fun getReadInterval(): ByteArray {
        val now = System.currentTimeMillis() / 1000
        var then: Long = 0
        readLogsFrom?.let {
            then = it.time / 1000
        }
        val nowBytes = now.toBytes().copyOfRange(4, 8)
        val thenBytes = then.toBytes().copyOfRange(4, 8)
        return (if (isAir) readAllBytesAir else readAllBytes).plus(nowBytes).plus(thenBytes)
    }

    private fun readFromInfoService() {
        Timber.d("$device reading from Info Service")

        readCharacteristic(modelCharacteristic)
            .with { device, data ->
                model = data.getStringValue(0)
                Timber.d("$device modelCharacteristic $model")
                isAir = model == ruuviAir
                if (isAir) initializeAir()
                checkIfInfoCollected()
            }
            .fail { device, status ->
                Timber.d("$device modelCharacteristic FAIL status = $status")
                executeDisconnect()
            }
            .enqueue()

        readCharacteristic(firmwareCharacteristic)
            .with { device, data ->
                firmware = data.getStringValue(0)
                Timber.d("$device firmwareCharacteristic $firmware")
                checkIfInfoCollected()
            }
            .fail { device, status ->
                Timber.d("$device firmwareCharacteristic FAIL status = $status")
                executeDisconnect()
            }
            .enqueue()

        if (serialNumberCharacteristic != null) {
            readCharacteristic(serialNumberCharacteristic)
                .with { device, data ->
                    serialNumber = data.getStringValue(0)
                    Timber.d("$device serialNumberCharacteristic $serialNumber")
                    checkIfInfoCollected()
                }
                .fail { device, status ->
                    Timber.d("$device serialNumberCharacteristic FAIL status = $status")
                    executeDisconnect()
                }
                .enqueue()
        }

        readCharacteristic(manufacturerCharacteristic)
            .with { device, data ->
                manufacturer = data.getStringValue(0)
                Timber.d("$device manufacturerCharacteristic $manufacturer")
                checkIfInfoCollected()
            }
            .fail { device, status ->
                Timber.d("$device manufacturerCharacteristic FAIL status = $status")
                executeDisconnect()
            }
            .enqueue()
    }

    private inner class GattCallback: BleManagerGattCallback() {
        override fun initialize() {
            readFromInfoService()
        }

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            gattCallback?.connected(true)

            val rxService = gatt.getService(nordicRxTxService)
            if (rxService != null) {
                nordicTxCharacteristic = rxService.getCharacteristic(nordicTxCharacteristicUUID)
                nordicRxCharacteristic = rxService.getCharacteristic(nordicRxCharacteristicUUID)
            }

            val infoService = gatt.getService(infoService)
            if (infoService != null) {
                modelCharacteristic = infoService.getCharacteristic(modelCharacteristicUUID)
                serialNumberCharacteristic = infoService.getCharacteristic(serialNumberCharacteristicUUID)
                manufacturerCharacteristic = infoService.getCharacteristic(manufacturerCharacteristicUUID)
                firmwareCharacteristic = infoService.getCharacteristic(firmwareCharacteristicUUID)
            }

            val isServiceSupported = rxService != null && infoService != null
            Timber.d("$device isRequiredServiceSupported = $isServiceSupported rxService(${rxService != null}) infoService (${infoService != null})")
            return isServiceSupported
        }

        override fun onConnectionUpdated(
            gatt: BluetoothGatt,
            interval: Int,
            latency: Int,
            timeout: Int
        ) {
            Timber.d("$device onConnectionUpdated interval = $interval latency = $latency timeout = $timeout mtu = $mtu")
            super.onConnectionUpdated(gatt, interval, latency, timeout)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int) {
            super.onMtuChanged(gatt, mtu)
            Timber.d("$device onMtuChanged mtu = $mtu")
        }

        override fun onDeviceReady() {
            Timber.d("$device onDeviceReady")
            super.onDeviceReady()
        }

        override fun onServicesInvalidated() {
            Timber.d("$device onServicesInvalidated")
            removeNotificationCallback(nordicTxCharacteristic)
            modelCharacteristic = null
            serialNumberCharacteristic = null
            manufacturerCharacteristic = null
            firmwareCharacteristic = null
            nordicTxCharacteristic = null
            nordicRxCharacteristic = null
            gattCallback?.connected(false)
        }
    }

    enum class ActionType {
        GET_LOGS,
        GET_VERSION
    }

    companion object {
        const val MAX_CONNECT_RETRY = 3
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val infoService: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        private val nordicRxTxService: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val manufacturerCharacteristicUUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        private val modelCharacteristicUUID: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        private val serialNumberCharacteristicUUID: UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
        private val firmwareCharacteristicUUID: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        private val nordicRxCharacteristicUUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val nordicTxCharacteristicUUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

        private val temperatureType = "3A3010".hexStringToByteArray()
        private val humidityType = "3A3110".hexStringToByteArray()
        private val pressureType = "3A3210".hexStringToByteArray()
        private val historyEnd = "FFFFFFFFFFFFFFFF"
        private val historyEndAir = "003B200020"
        private val nullValue = "FFFFFFFF".hexStringToByteArray()
        private val ruuviAir = "Ruuvi Air"

        val supportLoggingVersion: SemVer = SemVer.parse("3.28.12")
        val readAllBytes = 0x3A3A11.toBytes().copyOfRange(1, 4)
        val readAllBytesAir = 0x3B0021.toBytes().copyOfRange(1, 4)


        const val DATA_FORMAT = 0xE0
        const val TEMPERATURE_POSITION = 4
        const val HUMIDITY_POSITION = 6
        const val PRESSURE_POSITION = 8
        const val PM1_POSITION = 10
        const val PM25_POSITION = 12
        const val PM4_POSITION = 14
        const val PM10_POSITION = 16
        const val CO2_POSITION = 18
        const val VOC_POSITION = 20
        const val NOX_POSITION = 22
        const val LUMINOSITY_POSITION = 24
        const val DBA_AVG_POSITION = 26
        const val DBA_PEAK_POSITION = 27
        const val VOLTAGE_POSITION = 28
    }
}