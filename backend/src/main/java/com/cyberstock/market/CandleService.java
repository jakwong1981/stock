package com.cyberstock.market;

import com.cyberstock.instrument.Instrument;
import com.cyberstock.instrument.InstrumentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Builds OHLCV candles from live ticks for every supported interval. When no stored
 * history exists yet, a deterministic synthetic backfill ending at the current price is
 * generated so charts are usable in development (mock mode).
 */
@Service
public class CandleService {

    static final int MAX_CANDLES = 1000;
    static final int BACKFILL = 300;

    /** Real provider history is re-fetched after this long, so daily/weekly bars stay accurate. */
    static final long REFRESH_MS = 5 * 60_000L;

    private record Key(String symbol, Interval interval) {}

    private static final class Series {
        final Deque<Candle> candles;
        final long loadedAt;
        final boolean real;

        Series(List<Candle> candles, boolean real) {
            this.candles = new ArrayDeque<>(candles);
            this.loadedAt = System.currentTimeMillis();
            this.real = real;
        }
    }

    private final Map<Key, Series> series = new ConcurrentHashMap<>();
    private final InstrumentService instruments;
    private final QuoteService quotes;

    public CandleService(InstrumentService instruments, QuoteService quotes) {
        this.instruments = instruments;
        this.quotes = quotes;
        quotes.addListener(this::onQuote);
    }

    /** Applies live ticks only to series a client has already loaded (history is lazy). */
    void onQuote(Quote q) {
        Instrument inst = instruments.find(q.symbol()).orElse(null);
        if (inst == null) {
            return;
        }
        ZoneId zone = inst.zoneId();
        Instant ts = Instant.ofEpochMilli(q.ts());
        for (Interval interval : Interval.values()) {
            Series s = series.get(new Key(q.symbol(), interval));
            if (s != null) {
                apply(s.candles, interval.bucketStart(ts, zone), q.price(), q.lastSize());
            }
        }
    }

    static void apply(Deque<Candle> d, long bucket, BigDecimal price, BigDecimal size) {
        synchronized (d) {
            Candle last = d.peekLast();
            if (last != null && last.time() == bucket) {
                d.pollLast();
                d.addLast(last.merge(price, size));
            } else if (last == null || bucket > last.time()) {
                BigDecimal open = last != null ? last.close() : price;
                d.addLast(new Candle(bucket, open, open.max(price), open.min(price), price, size));
                while (d.size() > MAX_CANDLES) {
                    d.pollFirst();
                }
            }
            // Out-of-order ticks older than the latest bucket are ignored
        }
    }

    public List<Candle> history(String symbol, Interval interval, int limit) {
        Instrument inst = instruments.require(symbol);
        Key key = new Key(inst.getSymbol(), interval);
        Series s = series.get(key);
        boolean stale = s != null && s.real
                && (s.candles.isEmpty() || System.currentTimeMillis() - s.loadedAt > REFRESH_MS);
        if (s == null || stale) {
            s = load(inst, interval, s);
            series.put(key, s);
        }
        synchronized (s.candles) {
            List<Candle> all = new ArrayList<>(s.candles);
            if (all.isEmpty()) {
                return List.of();
            }
            int n = Math.max(1, Math.min(limit, all.size()));
            return all.subList(all.size() - n, all.size());
        }
    }

    /**
     * Loads real history from the provider (Yahoo). Mock mode synthesises history ending at
     * the current price. If a real provider fails, the previous series is kept; with no
     * previous series an empty one is returned rather than inventing prices.
     */
    private Series load(Instrument inst, Interval interval, Series previous) {
        MarketDataProvider provider = quotes.provider();
        if (provider instanceof MockMarketDataProvider) {
            BigDecimal price = quotes.latest(inst.getSymbol()).map(Quote::price)
                    .orElse(new BigDecimal(MockMarketDataProvider.BASE_PRICES.getOrDefault(inst.getSymbol(), "100.00")));
            return new Series(synthesize(inst.getSymbol(), interval, price, inst.zoneId(), Instant.now()), false);
        }
        return provider.history(inst, interval, MAX_CANDLES)
                .map(c -> new Series(c, true))
                .orElseGet(() -> previous != null ? previous : new Series(List.of(), true));
    }

    /** Deterministic random-walk history ending at {@code endPrice}. */
    static List<Candle> synthesize(String symbol, Interval interval, BigDecimal endPrice, ZoneId zone, Instant now) {
        Random rnd = new Random((symbol + interval.code()).hashCode());
        double vol = 0.02 * Math.sqrt(interval.seconds() / 86400.0); // ~2% daily volatility
        long current = interval.bucketStart(now, zone);
        List<Candle> out = new ArrayList<>(BACKFILL);
        BigDecimal close = endPrice;
        for (int i = 0; i < BACKFILL; i++) {
            long t = interval.minus(current, i, zone);
            double r = rnd.nextGaussian() * vol;
            BigDecimal open = close.divide(BigDecimal.valueOf(1 + r), 2, RoundingMode.HALF_UP);
            BigDecimal hi = open.max(close).multiply(BigDecimal.valueOf(1 + Math.abs(rnd.nextGaussian()) * vol / 2))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal lo = open.min(close).multiply(BigDecimal.valueOf(1 - Math.abs(rnd.nextGaussian()) * vol / 2))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal volume = BigDecimal.valueOf(1000L + rnd.nextInt(100_000)).multiply(BigDecimal.valueOf(Math.max(1, interval.seconds() / 60)));
            out.add(new Candle(t, open, hi, lo, close, volume));
            close = open;
        }
        java.util.Collections.reverse(out);
        return out;
    }
}
