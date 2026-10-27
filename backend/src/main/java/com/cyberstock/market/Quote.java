package com.cyberstock.market;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Real-time quote snapshot. All monetary values are {@link BigDecimal} and serialised as
 * JSON strings so clients never lose precision by parsing them as floating-point numbers.
 */
public record Quote(
        String symbol,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal prevClose,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal open,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal high,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal low,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal change,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal changePercent,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal volume,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal lastSize,
        long ts) {

    /** Builds a quote, deriving change and change % from price and previous close. */
    public static Quote of(String symbol, BigDecimal price, BigDecimal prevClose, BigDecimal open,
                           BigDecimal high, BigDecimal low, BigDecimal volume, BigDecimal lastSize, long ts) {
        BigDecimal change = price.subtract(prevClose);
        BigDecimal pct = prevClose.signum() == 0
                ? BigDecimal.ZERO
                : change.multiply(BigDecimal.valueOf(100)).divide(prevClose, 4, RoundingMode.HALF_UP);
        return new Quote(symbol, price, prevClose, open, high, low, change, pct, volume, lastSize, ts);
    }
}
