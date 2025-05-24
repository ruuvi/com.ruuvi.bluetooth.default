package com.ruuvi.station.bluetooth.contract

import java.util.*

data class LogReading (
    var date: Date = Date(),
    var id: String = "",
    var dataFormat: Int? = null,
    var temperature: Double? = null,
    var humidity: Double? = null,
    var pressure: Double? = null,
    var pm1: Double? = null,
    var pm25: Double? = null,
    var pm4: Double? = null,
    var pm10: Double? = null,
    var co2: Int? = null,
    var voc: Int? = null,
    var nox: Int? = null,
    var luminosity: Int? = null,
    var dBaAvg: Double? = null,
    var dBaPeak: Double? = null,
    var voltage: Double? = null,
) {
    constructor(timestamp: Date, ruuviTag: FoundRuuviTag): this(
        date = timestamp,
        id = ruuviTag.id ?: "",
        dataFormat = ruuviTag.dataFormat,
        temperature = ruuviTag.temperature,
        humidity = ruuviTag.humidity,
        pressure = ruuviTag.pressure,
        pm1 = ruuviTag.pm1,
        pm25 = ruuviTag.pm25,
        pm4 = ruuviTag.pm4,
        pm10 = ruuviTag.pm10,
        co2 = ruuviTag.co2,
        voc = ruuviTag.voc,
        nox = ruuviTag.nox,
        luminosity = ruuviTag.luminosity,
        dBaAvg = ruuviTag.dBaAvg,
        dBaPeak = ruuviTag.dBaPeak,
        voltage = ruuviTag.voltage
    )
}