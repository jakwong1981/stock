package com.cyberstock.market;

import com.cyberstock.instrument.Instrument;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Source of real-time quotes and, optionally, historical candles. Implementations:
 * {@link MockMarketDataProvider} (development), {@link YahooMarketDataProvider} (no API key)
 * and {@link FinnhubMarketDataProvider} (API key, US stocks on the free plan).
 */
public interface MarketDataProvider {

    String name();

    /** Starts streaming quotes for the given instruments into {@code sink}. */
    void start(Collection<Instrument> instruments, Consumer<Quote> sink);

    void stop();

    /**
     * Real OHLCV history ending now, oldest first, or empty when the provider has none
     * (the candle service then falls back to synthetic history).
     */
    default Optional<List<Candle>> history(Instrument instrument, Interval interval, int limit) {
        return Optional.empty();
    }
}
