package com.ruuvi.station.bluetooth.decoder

import android.bluetooth.BluetoothDevice
import com.neovisionaries.bluetooth.ble.advertising.ADManufacturerSpecific
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser
import com.neovisionaries.bluetooth.ble.advertising.EddystoneURL
import com.ruuvi.station.bluetooth.contract.FoundRuuviTag
import timber.log.Timber

class BleScanResult (
    val device: BluetoothDevice,
    val rssi: Int,
    val scanData: ByteArray?,
    private val isLeExtendedAdvertisingSupported: Boolean
){
    fun parse(): FoundRuuviTag? {
        var tag: FoundRuuviTag? = null

        var skipLegacy = isLeExtendedAdvertisingSupported

        try {
            val structures = ADPayloadParser.getInstance().parse(scanData)

            for (structure in structures) {
                when (structure) {
                    is EddystoneURL -> {
                        val url = structure.url.toString()
                        if (url.startsWith("https://ruu.vi/#") || url.startsWith("https://r/")) {
                            tag = from(device.address, url, null, rssi)
                        }
                    }
                    is ADManufacturerSpecific -> {
                        if (structure.companyId == 0x0499) {
                            tag = from(device.address, null, scanData, rssi, skipLegacy)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Parsing BLE data failed")
        }

        return tag
    }

    companion object {
        fun from(id: String?, url: String?, rawData: ByteArray?, rssi: Int, skipLegacy: Boolean = false): FoundRuuviTag? {
            var offset: Int? = null
            var decoder: RuuviTagDecoder? = null

            if (rawData != null) {
                offset = DecoderUtils.getActualDataOffset(rawData)
                if (offset != null) {
                    val protocolVersion = rawData[offset].toInt() and 0xff
                    decoder = when (protocolVersion) {
                        3 -> DecodeFormat3()
                        5 -> DecodeFormat5()
                        0xC5 -> DecodeFormatC5()
                        0xE0 -> DecodeFormatE0()
                        0xF0 -> if (skipLegacy) null else DecodeFormatF0()
                        else -> {
                            Timber.d("Unknown tag protocol version: $protocolVersion (PROTOCOL_OFFSET: $offset) sensor $id")
                            Timber.d("Bytes for $id: ${rawData.joinToString(" ") { (it.toInt() and 0xff).toString() }}")
                            null
                        }
                    }
                } else {
                    Timber.d("Offset not found")
                }
            }

            return if (decoder != null && offset != null && rawData != null) {
                val tag = decoder.decode(rawData, offset)
                tag?.apply {
                    this.id = id
                    this.url = url
                    this.rssi = rssi
                }
                tag
            } else null
        }
    }
}
