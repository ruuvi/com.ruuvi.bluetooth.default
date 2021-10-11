package com.ruuvi.station.bluetooth.util.extensions

fun ByteArray.toHexString() =
    asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }

fun ByteArray.toInt(): Int {
    var result = 0.toUInt()
    var shift = 0
    for (byte in this.reversed()) {
        val uByte = byte.toUByte()
        result = result or (uByte.toUInt() shl shift)
        shift += 8
    }
    return result.toInt()
}

fun ByteArray.toLong(): Long {
    var result = 0.toULong()
    var shift = 0
    for (byte in this.reversed()) {
        val uByte = byte.toUByte()
        result = result or (uByte.toULong() shl shift)
        shift += 8
    }
    return result.toLong()
}