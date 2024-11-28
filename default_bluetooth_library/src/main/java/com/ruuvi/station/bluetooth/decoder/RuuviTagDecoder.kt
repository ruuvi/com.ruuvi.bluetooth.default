package com.ruuvi.station.bluetooth.decoder

import com.ruuvi.station.bluetooth.FoundRuuviTag

interface RuuviTagDecoder {
    fun decode(data: ByteArray, offset: Int): FoundRuuviTag?
}