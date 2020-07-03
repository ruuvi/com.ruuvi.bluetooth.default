package com.ruuvi.station.bluetooth.decoder

import com.ruuvi.station.bluetooth.FoundRuuviTag

const val TEMPERATURE_MINIMUM = -163.835
const val TEMPERATURE_MAXIMUM = 163.835

const val HUMIDITY_MINIMUM = 0.0
const val HUMIDITY_MAXIMUM = 163.8350

const val PRESSURE_MINIMUM = 50000.0
const val PRESSURE_MAXIMUM = 115534.0

const val ACCELERATION_MINIMUM = -32.767
const val ACCELERATION_MAXIMUM = 32.767

const val TX_POWER_MINIMUM = -40.0
const val TX_POWER_MAXIMUM = 20.0

const val VOLTAGE_MINIMUM = 1.600
const val VOLTAGE_MAXIMUM = 3.646

const val MOVEMENT_MINIMUM = 0
const val MOVEMENT_MAXIMUM = 254

const val MEASUREMENT_SEQUENCE_MINIMUM = 0
const val MEASUREMENT_SEQUENCE_MAXIMUM = 65534

fun validateValues(tag: FoundRuuviTag) : FoundRuuviTag{
    tag.temperature?.let {
        if (it !in TEMPERATURE_MINIMUM..TEMPERATURE_MAXIMUM) tag.temperature = null
    }

    tag.humidity?.let {
        if (it !in HUMIDITY_MINIMUM..HUMIDITY_MAXIMUM) tag.humidity = null
    }

    tag.pressure?.let {
        if (it !in PRESSURE_MINIMUM..PRESSURE_MAXIMUM) tag.pressure = null
    }

    tag.accelX?.let {
        if (it !in ACCELERATION_MINIMUM..ACCELERATION_MAXIMUM) tag.accelX = null
    }

    tag.accelY?.let {
        if (it !in ACCELERATION_MINIMUM..ACCELERATION_MAXIMUM) tag.accelY = null
    }

    tag.accelZ?.let {
        if (it !in ACCELERATION_MINIMUM..ACCELERATION_MAXIMUM) tag.accelZ = null
    }

    tag.txPower?.let {
        if (it !in TX_POWER_MINIMUM..TX_POWER_MAXIMUM) tag.txPower = null
    }

    tag.voltage?.let {
        if (it !in VOLTAGE_MINIMUM..VOLTAGE_MAXIMUM) tag.voltage = null
    }

    tag.movementCounter?.let {
        if (it !in MOVEMENT_MINIMUM..MOVEMENT_MAXIMUM) tag.movementCounter = null
    }

    tag.measurementSequenceNumber?.let {
        if (it !in MEASUREMENT_SEQUENCE_MINIMUM..MEASUREMENT_SEQUENCE_MAXIMUM) tag.measurementSequenceNumber = null
    }
    return tag
}