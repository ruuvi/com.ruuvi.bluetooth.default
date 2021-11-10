package com.ruuvi.station.bluetooth.util.extensions

import java.nio.ByteBuffer

fun Int.toBytes(): ByteArray {
    return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()
}