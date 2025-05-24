package com.ruuvi.station.bluetooth.decoder

import com.ruuvi.station.bluetooth.contract.FoundRuuviTag

interface RuuviTagDecoder {
    fun decode(data: ByteArray, offset: Int): FoundRuuviTag?
}