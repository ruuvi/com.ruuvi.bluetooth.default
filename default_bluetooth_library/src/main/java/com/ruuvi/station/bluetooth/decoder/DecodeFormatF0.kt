package com.ruuvi.station.bluetooth.decoder

import com.ruuvi.station.bluetooth.FoundRuuviTag
import com.ruuvi.station.bluetooth.util.extensions.roundHalfUp
import timber.log.Timber
import kotlin.math.log10
import kotlin.math.pow

class DecodeFormatF0: RuuviTagDecoder {
    override fun decode(data: ByteArray, offset: Int): FoundRuuviTag? {
        var result = FoundRuuviTag()
        result.dataFormat = DATA_FORMAT

        result.temperature = data[TEMPERATURE_POSITION + offset].toInt().toDouble().roundHalfUp(4)
        result.humidity = ((data[HUMIDITY_POSITION + offset].toInt() and 0xFF) / 2.0).roundHalfUp(4)
        result.pressure = ((data[PRESSURE_POSITION + offset].toInt() and 0xFF) * 100.0 + 90000.0)
        result.pm1 = decodeLogarithmic(data[PM1_POSITION + offset].toInt() and 0xFF, PM_DECODE_BASE).roundHalfUp(2)
        result.pm25 = decodeLogarithmic(data[PM25_POSITION + offset].toInt() and 0xFF, PM_DECODE_BASE).roundHalfUp(2)
        result.pm4 = decodeLogarithmic(data[PM4_POSITION + offset].toInt() and 0xFF, PM_DECODE_BASE).roundHalfUp(2)
        result.pm10 = decodeLogarithmic(data[PM10_POSITION + offset].toInt() and 0xFF, PM_DECODE_BASE).roundHalfUp(2)
        result.co2 = decodeLogarithmic(data[CO2_POSITION + offset].toInt() and 0xFF, CO2_DECODE_BASE).roundHalfUp(0).toInt()
        result.voc = decodeLogarithmic(data[VOC_POSITION + offset].toInt() and 0xFF, VOC_DECODE_BASE).roundHalfUp(0).toInt() + 1
        result.nox = decodeLogarithmic(data[NOX_POSITION + offset].toInt() and 0xFF, NOX_DECODE_BASE).roundHalfUp(0).toInt() + 1
        result.luminosity = decodeLogarithmic(data[LUMINOSITY_POSITION + offset].toInt() and 0xFF, LUMINOSITY_DECODE_BASE).toInt()
        result.dBaAvg = ((data[DBA_AVG_POSITION + offset].toInt() and 0xFF) / 2.0).roundHalfUp(2)

        result = validateValues(result)
        Timber.d("DecodeFormat${DATA_FORMAT} $result from bytes [${data.joinToString(", ") { (it.toInt() and 0xff).toString() }}]")
        return result
    }

    fun decodeLogarithmic(encoded: Int, base: Double): Double {
        val scale = 254 / log10(base)
        return 10.0.pow(encoded/scale) - 1
    }


    companion object {
        const val DATA_FORMAT = 0xF0
        const val TEMPERATURE_POSITION = 1
        const val HUMIDITY_POSITION = 2
        const val PRESSURE_POSITION = 3
        const val PM1_POSITION = 4
        const val PM25_POSITION = 5
        const val PM4_POSITION = 6
        const val PM10_POSITION = 7
        const val CO2_POSITION = 8
        const val VOC_POSITION = 9
        const val NOX_POSITION = 10
        const val LUMINOSITY_POSITION = 11
        const val DBA_AVG_POSITION = 12

        const val DBA_PEAK_POSITION = 24
        const val SEQUENCE_POSITION = 25
        const val VOLTAGE_POSITION = 27

        const val PM_DECODE_BASE = 1001.0
        const val CO2_DECODE_BASE = 40001.0
        const val VOC_DECODE_BASE = 501.0
        const val NOX_DECODE_BASE = 501.0
        const val LUMINOSITY_DECODE_BASE = 40001.0
    }
}