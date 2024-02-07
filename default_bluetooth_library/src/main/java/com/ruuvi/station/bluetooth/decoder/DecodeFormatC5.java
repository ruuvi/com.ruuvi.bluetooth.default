package com.ruuvi.station.bluetooth.decoder;

import static com.ruuvi.station.bluetooth.decoder.FoundRuuviTagKt.validateValues;

import com.ruuvi.station.bluetooth.FoundRuuviTag;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecodeFormatC5 implements LeScanResult.RuuviTagDecoder {
    @Override
    public FoundRuuviTag decode(byte[] data, int offset) {
        FoundRuuviTag tag = new FoundRuuviTag();
        tag.setDataFormat(0xC5);
        tag.setTemperature((data[1 + offset] << 8 | data[2 + offset] & 0xFF) / 200d);
        tag.setHumidity(((data[3 + offset] & 0xFF) << 8 | data[4 + offset] & 0xFF) / 400d);
        tag.setPressure((double) ((data[5 + offset] & 0xFF) << 8 | data[6 + offset] & 0xFF) + 50000);
        tag.setPressure(tag.getPressure() != null ? tag.getPressure() : 0.0);

        int powerInfo = (data[7 + offset] & 0xFF) << 8 | data[8 + offset] & 0xFF;
        if ((powerInfo >>> 5) != 0b11111111111) {
            tag.setVoltage((powerInfo >>> 5) / 1000d + 1.6d);
        }
        if ((powerInfo & 0b11111) != 0b11111) {
            tag.setTxPower((powerInfo & 0b11111) * 2 - 40.0);
        }
        tag.setMovementCounter(data[9 + offset] & 0xFF);
        tag.setMeasurementSequenceNumber((data[10 + offset] & 0xFF) << 8 | data[11 + offset] & 0xFF);

        // make it pretty
        tag.setTemperature(round(tag.getTemperature() != null ? tag.getTemperature() : 0.0, 4));
        tag.setHumidity(round(tag.getHumidity() != null ? tag.getHumidity() : 0.0, 4));
        tag.setPressure(round(tag.getPressure(), 2));
        tag.setVoltage(round(tag.getVoltage() != null ? tag.getVoltage() : 0.0, 4));
        tag.setAccelX(round(tag.getAccelX() != null ? tag.getAccelX() : 0.0, 4));
        tag.setAccelY(round(tag.getAccelY() != null ? tag.getAccelY() : 0.0, 4));
        tag.setAccelZ(round(tag.getAccelZ() != null ? tag.getAccelZ() : 0.0, 4));
        return validateValues(tag);
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}