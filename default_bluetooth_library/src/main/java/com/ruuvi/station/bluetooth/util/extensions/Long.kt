package com.ruuvi.station.bluetooth.util.extensions

import java.nio.ByteBuffer

fun Long.toBytes(): ByteArray {
    return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(this).array()
}