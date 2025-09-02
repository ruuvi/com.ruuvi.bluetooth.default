package com.ruuvi.station.bluetooth.decoder

import com.ruuvi.station.bluetooth.contract.FoundRuuviTag
import com.ruuvi.station.bluetooth.util.extensions.roundHalfUp
import timber.log.Timber

class DecodeFormatE1: RuuviTagDecoder {
    override fun decode(data: ByteArray, offset: Int): FoundRuuviTag? {
        var result = FoundRuuviTag()
        result.dataFormat = DATA_FORMAT

        val dbaInstantFlag = isBitSet(data[FLAGS_POSITION + offset], 3)
        val dbaAvgFlag = isBitSet(data[FLAGS_POSITION + offset], 4)
        val dbaPeakFlag = isBitSet(data[FLAGS_POSITION + offset], 5)
        val vocFlag = isBitSet(data[FLAGS_POSITION + offset], 6)
        val noxFlag = isBitSet(data[FLAGS_POSITION + offset], 7)

        result.temperature = (((data[TEMPERATURE_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[TEMPERATURE_POSITION + 1 + offset].toInt() and 0xFF)) / 200.0).roundHalfUp(4)
        result.humidity = (((data[HUMIDITY_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[HUMIDITY_POSITION + 1 + offset].toInt() and 0xFF)) / 400.0).roundHalfUp(4)
        result.pressure = ((data[PRESSURE_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[PRESSURE_POSITION + 1 + offset].toInt() and 0xFF)).toDouble() + 50000
        result.pm1 = (((data[PM1_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[PM1_POSITION + 1 + offset].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(1)
        result.pm25 = (((data[PM25_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[PM25_POSITION + 1 + offset].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(1)
        result.pm4 = (((data[PM4_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[PM4_POSITION + 1 + offset].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(1)
        result.pm10 = (((data[PM10_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[PM10_POSITION + 1 + offset].toInt() and 0xFF)).toDouble() / 10).roundHalfUp(1)
        result.co2 = ((data[CO2_POSITION + offset].toInt() and 0xFF) shl 8 or
                (data[CO2_POSITION + 1 + offset].toInt() and 0xFF))
        result.voc = ((data[VOC_POSITION + offset].toInt() and 0xFF) shl 1) or if (vocFlag) 1 else 0
        result.nox = ((data[NOX_POSITION + offset].toInt() and 0xFF) shl 1) or if (noxFlag) 1 else 0
        result.luminosity = ((((data[LUMINOSITY_POSITION + offset].toInt() and 0xFF) shl 16) or
                ((data[LUMINOSITY_POSITION + 1 + offset].toInt() and 0xFF) shl 8) or
                (data[LUMINOSITY_POSITION + 2 + offset].toInt() and 0xFF)) / 100.0).roundHalfUp(2)
        result.dBaInst = ((((data[DBA_INST_POSITION + offset].toInt() and 0xFF) shl 1) or if (dbaInstantFlag) 1 else 0) / 5.0 + 18).roundHalfUp(2)
        result.dBaAvg = ((((data[DBA_AVG_POSITION + offset].toInt() and 0xFF) shl 1) or if (dbaAvgFlag) 1 else 0) / 5.0 + 18).roundHalfUp(2)
        result.dBaPeak = ((((data[DBA_PEAK_POSITION + offset].toInt() and 0xFF) shl 1) or if (dbaPeakFlag) 1 else 0) / 5.0 + 18).roundHalfUp(2)
        result.measurementSequenceNumber = ((data[SEQUENCE_POSITION + offset].toInt() and 0xFF) shl 16) or
                ((data[SEQUENCE_POSITION + 1 + offset].toInt() and 0xFF) shl 8) or
                (data[SEQUENCE_POSITION + 2 + offset].toInt() and 0xFF)
        result.voltage = null

        result = validateValues(result)

        Timber.d("DecodeFormat$DATA_FORMAT $result from bytes [${data.joinToString(", ") { (it.toInt() and 0xff).toString() }}]")
        return result
    }

    fun isBitSet(byte: Byte, bitIndex: Int): Boolean {
        require(bitIndex in 0..7) { "bitIndex must be in 0..7" }
        return (byte.toInt() shr bitIndex and 1) == 1
    }

    companion object {
        const val DATA_FORMAT = 0xE0
        const val TEMPERATURE_POSITION = 1
        const val HUMIDITY_POSITION = 3
        const val PRESSURE_POSITION = 5
        const val PM1_POSITION = 7
        const val PM25_POSITION = 9
        const val PM4_POSITION = 11
        const val PM10_POSITION = 13
        const val CO2_POSITION = 15
        const val VOC_POSITION = 17
        const val NOX_POSITION = 18
        const val LUMINOSITY_POSITION = 19
        const val DBA_INST_POSITION = 22
        const val DBA_AVG_POSITION = 23
        const val DBA_PEAK_POSITION = 24
        const val SEQUENCE_POSITION = 25
        const val FLAGS_POSITION = 28
    }
}