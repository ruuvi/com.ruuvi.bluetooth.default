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

        result = validateValues(result)
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
    }
}