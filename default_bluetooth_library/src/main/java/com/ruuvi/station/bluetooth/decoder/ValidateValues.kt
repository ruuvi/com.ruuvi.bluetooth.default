package com.ruuvi.station.bluetooth.decoder

import com.ruuvi.station.bluetooth.contract.FoundRuuviTag

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
const val MEASUREMENT_SEQUENCE_MAXIMUM = 65534

const val PM_MINIMUM = 0.0
const val PM_MAXIMUM = 1000.0

const val CO2_MINIMUM = 0
const val CO2_MAXIMUM = 40000

const val VOC_MINIMUM = 1
const val VOC_MAXIMUM = 500

const val NOX_MINIMUM = 1
const val NOX_MAXIMUM = 500

const val LUMINOSITY_MINIMUM = 0.0
const val LUMINOSITY_MAXIMUM = 167772.14

const val DBA_MINIMUM = 0.0
const val DBA_MAXIMUM = 127.0

fun validateValues(sensor: FoundRuuviTag) : FoundRuuviTag {
    sensor.temperature?.let {
        if (it !in TEMPERATURE_MINIMUM..TEMPERATURE_MAXIMUM) sensor.temperature = null
    }

    sensor.humidity?.let {
        if (it !in HUMIDITY_MINIMUM..HUMIDITY_MAXIMUM) sensor.humidity = null
    }

    sensor.pressure?.let {
        if (it !in PRESSURE_MINIMUM .. PRESSURE_MAXIMUM) sensor.pressure = null
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
        if (it !in LUMINOSITY_MINIMUM .. LUMINOSITY_MAXIMUM) sensor.luminosity = null
    }

    sensor.dBaAvg?.let {
        if (it !in DBA_MINIMUM..DBA_MAXIMUM) sensor.dBaAvg = null
    }

    sensor.dBaPeak?.let {
        if (it !in DBA_MINIMUM..DBA_MAXIMUM) sensor.dBaPeak = null
    }
    return sensor
}