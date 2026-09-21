package com.ruuvi.station.bluetooth.decoder

import com.ruuvi.station.bluetooth.contract.FoundRuuviTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecodeAirQualityIndexTest {
    @Test
    fun format6DecodesZeroAndFlagLeastSignificantBits() {
        assertIndices(decodeFormat6(vocByte = 0x00, noxByte = 0x00, flags = 0x00), 0, 0)
        assertIndices(decodeFormat6(vocByte = 0x00, noxByte = 0x00, flags = 0xC0), 1, 1)
        assertIndices(decodeFormat6(vocByte = 0x74, noxByte = 0x74, flags = 0x00), 232, 232)
        assertIndices(decodeFormat6(vocByte = 0x74, noxByte = 0x74, flags = 0xC0), 233, 233)
    }

    @Test
    fun formatE1DecodesZeroAndFlagLeastSignificantBits() {
        assertIndices(decodeFormatE1(vocByte = 0x00, noxByte = 0x00, flags = 0x00), 0, 0)
        assertIndices(decodeFormatE1(vocByte = 0x00, noxByte = 0x00, flags = 0xC0), 1, 1)
        assertIndices(decodeFormatE1(vocByte = 0x74, noxByte = 0x74, flags = 0x00), 232, 232)
        assertIndices(decodeFormatE1(vocByte = 0x74, noxByte = 0x74, flags = 0xC0), 233, 233)
    }

    @Test
    fun decodersAcceptMaximumAndRejectInvalidSentinel() {
        assertIndices(decodeFormat6(vocByte = 0xFA, noxByte = 0xFA, flags = 0x00), 500, 500)
        assertIndices(decodeFormatE1(vocByte = 0xFA, noxByte = 0xFA, flags = 0x00), 500, 500)

        assertInvalidIndices(decodeFormat6(vocByte = 0xFF, noxByte = 0xFF, flags = 0xC0))
        assertInvalidIndices(decodeFormatE1(vocByte = 0xFF, noxByte = 0xFF, flags = 0xC0))
    }

    @Test
    fun sharedValidationAcceptsZeroAndRejectsOutOfRangeValues() {
        assertIndices(validateValues(FoundRuuviTag(voc = 0, nox = 0)), 0, 0)
        assertInvalidIndices(validateValues(FoundRuuviTag(voc = 501, nox = 501)))
    }

    private fun decodeFormat6(vocByte: Int, noxByte: Int, flags: Int): FoundRuuviTag =
        DecodeFormat6().decode(
            ByteArray(17).apply {
                this[0] = DecodeFormat6.DATA_FORMAT.toByte()
                this[DecodeFormat6.VOC_POSITION] = vocByte.toByte()
                this[DecodeFormat6.NOX_POSITION] = noxByte.toByte()
                this[DecodeFormat6.FLAGS_POSITION] = flags.toByte()
            },
            offset = 0
        )!!

    private fun decodeFormatE1(vocByte: Int, noxByte: Int, flags: Int): FoundRuuviTag =
        DecodeFormatE1().decode(
            ByteArray(29).apply {
                this[0] = DecodeFormatE1.DATA_FORMAT.toByte()
                this[DecodeFormatE1.VOC_POSITION] = vocByte.toByte()
                this[DecodeFormatE1.NOX_POSITION] = noxByte.toByte()
                this[DecodeFormatE1.FLAGS_POSITION] = flags.toByte()
            },
            offset = 0
        )!!

    private fun assertIndices(sensor: FoundRuuviTag, expectedVoc: Int, expectedNox: Int) {
        assertEquals(expectedVoc, sensor.voc)
        assertEquals(expectedNox, sensor.nox)
    }

    private fun assertInvalidIndices(sensor: FoundRuuviTag) {
        assertNull(sensor.voc)
        assertNull(sensor.nox)
    }
}
