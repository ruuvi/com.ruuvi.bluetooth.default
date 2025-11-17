package com.ruuvi.station.bluetooth.decoder

import com.ruuvi.station.bluetooth.contract.FoundRuuviTag
import com.ruuvi.station.bluetooth.util.extensions.roundHalfUp
import timber.log.Timber
import kotlin.math.exp
import kotlin.math.ln

class DecodeFormat6: RuuviTagDecoder {
    override fun decode(data: ByteArray, offset: Int): FoundRuuviTag? {
        var result = FoundRuuviTag()
        result.dataFormat = DATA_FORMAT

        val dbaInstantFlag = isBitSet(data[FLAGS_POSITION + offset], 3)
        val dbaAvgFlag = isBitSet(data[FLAGS_POSITION + offset], 4)
        val dbaPeakFlag = isBitSet(data[FLAGS_POSITION + offset], 5)
        val vocFlag = isBitSet(data[FLAGS_POSITION + offset], 6)
        val noxFlag = isBitSet(data[FLAGS_POSITION + offset], 7)

        result.temperature = (((data[TEMPERATURE_POSITION + offset].toInt() shl 8) or
                (data[TEMPERATURE_POSITION + 1 + offset].toInt() and 0xFF)) / 200.0).roundHalfUp(4)
        result.humidity = (((data[HUMIDITY_POSITION + offset].toInt() shl 8) or
                (data[HUMIDITY_POSITION + 1 + offset].toInt() and 0xFF)) / 400.0).roundHalfUp(4)
        result.pressure = ((data[PRESSURE_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[PRESSURE_POSITION + 1 + offset].toInt() and 0xFF)).toDouble() + 50000
        result.pm25 = (((data[PM25_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[PM25_POSITION + 1 + offset].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(1)
        result.co2 = ((data[CO2_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[CO2_POSITION + 1 + offset].toInt() and 0xFF))
        result.voc = ((data[VOC_POSITION + offset].toInt() and 0xFF) shl 1) or if (vocFlag) 1 else 0
        result.nox = ((data[NOX_POSITION + offset].toInt() and 0xFF) shl 1) or if (noxFlag) 1 else 0

        result.luminosity = decodeLogarithmic(data[LUMINOSITY_POSITION + offset].toInt() and 0xFF,
            LUMINOSITY_DECODE_BASE
        )

        result.dBaAvg = ((((data[DBA_AVG_POSITION + offset].toInt() and 0xFF) shl 1) or if (dbaAvgFlag) 1 else 0) / 5.0 + 18).roundHalfUp(2)
        result.measurementSequenceNumber = data[SEQUENCE_POSITION + offset].toInt() and 0xFF

        result = validateValues6(result)
        Timber.d("DecodeFormat${DATA_FORMAT} $result from bytes [${data.joinToString(", ") { (it.toInt() and 0xff).toString() }}]")
        return result
    }

    fun decodeLogarithmic(encoded: Int, base: Double): Double {
        val delta = ln(base+1) / 254
        return exp(encoded*delta).roundHalfUp(0) - 1
    }

    fun isBitSet(byte: Byte, bitIndex: Int): Boolean {
        require(bitIndex in 0..7) { "bitIndex must be in 0..7" }
        return (byte.toInt() shr bitIndex and 1) == 1
    }

    fun validateValues6(sensor: FoundRuuviTag) : FoundRuuviTag {
        sensor.temperature?.let {
            if (it !in TEMPERATURE_MINIMUM..TEMPERATURE_MAXIMUM) sensor.temperature = null
        }

        sensor.humidity?.let {
            if (it !in HUMIDITY_MINIMUM..HUMIDITY_MAXIMUM) sensor.humidity = null
        }

        sensor.pressure?.let {
            if (it !in PRESSURE_MINIMUM..PRESSURE_MAXIMUM) sensor.pressure = null
        }

        sensor.accelX?.let {
            if (it !in ACCELERATION_MINIMUM..ACCELERATION_MAXIMUM) sensor.accelX = null
        }

        sensor.accelY?.let {
            if (it !in ACCELERATION_MINIMUM..ACCELERATION_MAXIMUM) sensor.accelY = null
        }

        sensor.accelZ?.let {
            if (it !in ACCELERATION_MINIMUM..ACCELERATION_MAXIMUM) sensor.accelZ = null
        }

        sensor.txPower?.let {
            if (it !in TX_POWER_MINIMUM..TX_POWER_MAXIMUM) sensor.txPower = null
        }

        sensor.voltage?.let {
            if (it !in VOLTAGE_MINIMUM..VOLTAGE_MAXIMUM) sensor.voltage = null
        }

        sensor.movementCounter?.let {
            if (it !in MOVEMENT_MINIMUM..MOVEMENT_MAXIMUM) sensor.movementCounter = null
        }

        sensor.measurementSequenceNumber?.let {
            if (it !in MEASUREMENT_SEQUENCE_MINIMUM..MEASUREMENT_SEQUENCE_MAXIMUM) sensor.measurementSequenceNumber = null
        }

        sensor.pm1?.let {
            if (it !in PM_MINIMUM..PM_MAXIMUM) sensor.pm1 = null
        }

        sensor.pm25?.let {
            if (it !in PM_MINIMUM..PM_MAXIMUM) sensor.pm25 = null
        }

        sensor.pm4?.let {
            if (it !in PM_MINIMUM..PM_MAXIMUM) sensor.pm4 = null
        }

        sensor.pm10?.let {
            if (it !in PM_MINIMUM..PM_MAXIMUM) sensor.pm10 = null
        }

        sensor.co2?.let {
            if (it !in CO2_MINIMUM..CO2_MAXIMUM) sensor.co2 = null
        }

        sensor.voc?.let {
            if (it !in VOC_MINIMUM..VOC_MAXIMUM) sensor.voc = null
        }

        sensor.nox?.let {
            if (it !in NOX_MINIMUM..NOX_MAXIMUM) sensor.nox = null
        }

        sensor.luminosity?.let {
            if (it !in LUMINOSITY_MINIMUM..LUMINOSITY_MAXIMUM) sensor.luminosity = null
        }

        sensor.dBaAvg?.let {
            if (it !in DBA_MINIMUM..DBA_MAXIMUM) sensor.dBaAvg = null
        }

        sensor.dBaPeak?.let {
            if (it !in DBA_MINIMUM..DBA_MAXIMUM) sensor.dBaPeak = null
        }
        return sensor
    }
    companion object {
        const val DATA_FORMAT = 0x06
        const val TEMPERATURE_POSITION = 1
        const val HUMIDITY_POSITION = 3
        const val PRESSURE_POSITION = 5
        const val PM25_POSITION = 7
        const val CO2_POSITION = 9
        const val VOC_POSITION = 11
        const val NOX_POSITION = 12
        const val LUMINOSITY_POSITION = 13
        const val DBA_AVG_POSITION = 14
        const val SEQUENCE_POSITION = 15
        const val FLAGS_POSITION = 16
        const val LUMINOSITY_DECODE_BASE = 65535.0

        const val TEMPERATURE_MINIMUM = -163.835
        const val TEMPERATURE_MAXIMUM = 163.835

        const val HUMIDITY_MINIMUM = 0.0
        const val HUMIDITY_MAXIMUM = 100.0

        const val PRESSURE_MINIMUM = 50000.0
        const val PRESSURE_MAXIMUM = 115534.0

        const val ACCELERATION_MINIMUM = -32.767
        const val ACCELERATION_MAXIMUM = 32.767

        const val TX_POWER_MINIMUM = -40.0
        const val TX_POWER_MAXIMUM = 20.0

        const val VOLTAGE_MINIMUM = 1.600
        const val VOLTAGE_MAXIMUM = 7.620

        const val MOVEMENT_MINIMUM = 0
        const val MOVEMENT_MAXIMUM = 254

        const val MEASUREMENT_SEQUENCE_MINIMUM = 0
        const val MEASUREMENT_SEQUENCE_MAXIMUM = 255

        const val PM_MINIMUM = 0.0
        const val PM_MAXIMUM = 6553.4

        const val CO2_MINIMUM = 0
        const val CO2_MAXIMUM = 40000

        const val VOC_MINIMUM = 1
        const val VOC_MAXIMUM = 500

        const val NOX_MINIMUM = 1
        const val NOX_MAXIMUM = 500

        const val LUMINOSITY_MINIMUM = 0.0
        const val LUMINOSITY_MAXIMUM = 65535.0

        const val DBA_MINIMUM = 0.0
        const val DBA_MAXIMUM = 120.0
    }
}