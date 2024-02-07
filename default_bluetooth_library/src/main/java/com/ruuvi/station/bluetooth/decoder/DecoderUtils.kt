package com.ruuvi.station.bluetooth.decoder

class DecoderUtils {
    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        fun getActualDataOffset(rawData: ByteArray): Int? {
            var byte0Find = false
            var byte1Find = false
            rawData.toUByteArray().forEachIndexed { index, byte ->
                if (byte0Find) {
                    if (byte1Find) {
                        if (byte == byte2) {
                            return index + 1
                        } else {
                            byte0Find = false
                            byte1Find = false
                        }
                    } else {
                        if (byte == byte1) {
                            byte1Find = true
                        } else {
                            byte0Find = false
                        }
                    }
                } else {
                    if (byte == byte0) {
                        byte0Find = true
                    }
                }
            }
            return null
        }

        const val byte0: UByte = 255u // 0xFF
        const val byte1: UByte = 153u // 0x99
        const val byte2: UByte = 4u   // 0x04
    }
}