package com.cyberstock.market;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/** OHLCV candle. {@code time} is the bucket start in epoch seconds (UTC). */
public record Candle(
        long time,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal open,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal high,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal low,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal close,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal volume) {

    public Candle merge(BigDecimal price, BigDecimal addVolume) {
        return new Candle(time, open, high.max(price), low.min(price), price, volume.add(addVolume));
    }
}
