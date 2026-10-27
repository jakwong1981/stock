package com.cyberstock.market;

import com.cyberstock.instrument.Instrument;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Random-walk quote generator for development and demos. Prices are clearly synthetic and
 * must never be presented as real market data.
 */
public class MockMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(MockMarketDataProvider.class);

    static final Map<String, String> BASE_PRICES = Map.ofEntries(
            Map.entry("AAPL", "230.00"), Map.entry("NVDA", "180.00"), Map.entry("TSLA", "250.00"),
            Map.entry("MSFT", "420.00"), Map.entry("AMZN", "190.00"), Map.entry("GOOGL", "170.00"),
            Map.entry("META", "600.00"), Map.entry("JPM", "240.00"), Map.entry("0700.HK", "480.00"),
            Map.entry("9988.HK", "120.00"), Map.entry("3690.HK", "130.00"), Map.entry("0005.HK", "95.00"),
            Map.entry("1810.HK", "45.00"));

    private static final class State {
        final BigDecimal prevClose;
        final BigDecimal open;
        BigDecimal price;
        BigDecimal high;
        BigDecimal low;
        BigDecimal volume = BigDecimal.ZERO;

        State(BigDecimal base) {
            this.prevClose = base;
            this.open = base;
            this.price = base;
            this.high = base;
            this.low = base;
        }
    }

    private final long tickIntervalMs;
    private final Random random;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;

    public MockMarketDataProvider(long tickIntervalMs, long seed) {
        this.tickIntervalMs = Math.max(50, tickIntervalMs);
        this.random = new Random(seed);
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public synchronized void start(Collection<Instrument> instruments, Consumer<Quote> sink) {
        List<String> symbols = instruments.stream().map(Instrument::getSymbol).toList();
        symbols.forEach(s -> states.put(s, new State(new BigDecimal(BASE_PRICES.getOrDefault(s, "100.00")))));
        // Emit an initial snapshot so every symbol has a quote immediately
        symbols.forEach(s -> sink.accept(toQuote(s, states.get(s), BigDecimal.ZERO)));

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mock-market-data");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(() -> {
            try {
                for (String s : symbols) {
                    if (random.nextDouble() < 0.35) {
                        sink.accept(step(s));
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Mock tick failed", e);
            }
        }, tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);
        log.info("Mock market data provider started for {} instruments", symbols.size());
    }

    /** Advances one symbol by a single random-walk step. Package-private for tests. */
    Quote step(String symbol) {
        State st = states.get(symbol);
        synchronized (st) {
            double ret = random.nextGaussian() * 0.0008;
            BigDecimal next = st.price.multiply(BigDecimal.valueOf(1 + ret)).setScale(2, RoundingMode.HALF_UP);
            if (next.signum() <= 0) {
                next = new BigDecimal("0.01");
            }
            BigDecimal size = BigDecimal.valueOf(100L * (1 + random.nextInt(50)));
            st.price = next;
            st.high = st.high.max(next);
            st.low = st.low.min(next);
            st.volume = st.volume.add(size);
            return toQuote(symbol, st, size);
        }
    }

    private Quote toQuote(String symbol, State st, BigDecimal size) {
        return Quote.of(symbol, st.price, st.prevClose, st.open, st.high, st.low, st.volume, size,
                System.currentTimeMillis());
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
