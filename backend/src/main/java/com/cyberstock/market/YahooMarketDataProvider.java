package com.cyberstock.market;

import com.cyberstock.instrument.Instrument;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real prices from Yahoo Finance's public chart endpoint (no API key). Symbols use the
 * same format as the app ({@code AAPL}, {@code 0005.HK}).
 *
 * <p>This is an unofficial endpoint: it has no SLA, may change or throttle without notice,
 * HK quotes are typically delayed ~15 minutes, and Yahoo's terms only allow personal use.
 * Quotes are polled (default every 15 s, one request per symbol) rather than streamed.
 */
public class YahooMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooMarketDataProvider.class);
    static final String BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; CyberStockPulse/0.1)";

    /** Yahoo interval + range used to build each app interval (coarser buckets are aggregated). */
    record Source(String interval, String range) {}

    static final Map<Interval, Source> SOURCES = Map.of(
            Interval.M1, new Source("1m", "5d"),
            Interval.M5, new Source("5m", "1mo"),
            Interval.M15, new Source("15m", "1mo"),
            Interval.M30, new Source("30m", "1mo"),
            Interval.H1, new Source("60m", "6mo"),
            Interval.H4, new Source("60m", "1y"),
            Interval.D1, new Source("1d", "2y"),
            Interval.W1, new Source("1wk", "10y"),
            Interval.MN1, new Source("1mo", "max"));

    private final long pollSeconds;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Map<String, Quote> last = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;

    public YahooMarketDataProvider(long pollSeconds, ObjectMapper mapper) {
        this(pollSeconds, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    YahooMarketDataProvider(long pollSeconds, ObjectMapper mapper, HttpClient http) {
        this.pollSeconds = Math.max(5, pollSeconds);
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public String name() {
        return "yahoo";
    }

    @Override
    public synchronized void start(Collection<Instrument> instruments, Consumer<Quote> sink) {
        List<String> symbols = instruments.stream().map(Instrument::getSymbol).toList();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "yahoo-poller");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(() -> {
            for (String s : symbols) {
                try {
                    fetchQuote(s).ifPresent(q -> {
                        Quote prev = last.put(s, q);
                        // Only publish real changes, so unchanged polls don't trigger UI updates
                        if (prev == null || prev.ts() != q.ts() || prev.price().compareTo(q.price()) != 0
                                || prev.volume().compareTo(q.volume()) != 0) {
                            sink.accept(q);
                        }
                    });
                } catch (IOException e) {
                    log.warn("Yahoo quote failed for {}: {}", s, e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException e) {
                    log.warn("Yahoo quote parse failed for {}: {}", s, e.toString());
                }
            }
        }, 0, pollSeconds, TimeUnit.SECONDS);
        log.info("Yahoo Finance provider polling {} symbols every {}s (unofficial endpoint, personal use)",
                symbols.size(), pollSeconds);
    }

    Optional<Quote> fetchQuote(String symbol) throws IOException, InterruptedException {
        return get(symbol, "1d", "1d").map(this::parseQuote);
    }

    @Override
    public Optional<List<Candle>> history(Instrument instrument, Interval interval, int limit) {
        Source src = SOURCES.get(interval);
        try {
            return get(instrument.getSymbol(), src.interval(), src.range())
                    .map(r -> parseCandles(r, interval, instrument.zoneId()))
                    .filter(c -> !c.isEmpty())
                    .map(c -> c.size() > limit ? List.copyOf(c.subList(c.size() - limit, c.size())) : c);
        } catch (IOException | RuntimeException e) {
            log.warn("Yahoo history failed for {} {}: {}", instrument.getSymbol(), interval.code(), e.toString());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Returns {@code chart.result[0]}, or empty for unknown symbols / HTTP errors. */
    private Optional<JsonNode> get(String symbol, String interval, String range) throws IOException, InterruptedException {
        URI uri = URI.create(BASE + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                + "?interval=" + interval + "&range=" + range + "&includePrePost=false");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.warn("Yahoo returned HTTP {} for {}", res.statusCode(), symbol);
            return Optional.empty();
        }
        JsonNode result = mapper.readTree(res.body()).path("chart").path("result").path(0);
        return result.isMissingNode() || result.isNull() ? Optional.empty() : Optional.of(result);
    }

    Quote parseQuote(JsonNode result) {
        JsonNode m = result.path("meta");
        int scale = priceScale(m);
        BigDecimal price = dec(m.path("regularMarketPrice"), scale);
        if (price == null) {
            throw new IllegalStateException("no regularMarketPrice");
        }
        BigDecimal prev = orElse(dec(m.path("chartPreviousClose"), scale), price);
        JsonNode q = result.path("indicators").path("quote").path(0);
        BigDecimal open = orElse(dec(q.path("open").path(0), scale), prev);
        BigDecimal high = orElse(dec(m.path("regularMarketDayHigh"), scale), price.max(open));
        BigDecimal low = orElse(dec(m.path("regularMarketDayLow"), scale), price.min(open));
        BigDecimal volume = orElse(dec(m.path("regularMarketVolume"), 0), BigDecimal.ZERO);
        long t = m.path("regularMarketTime").asLong(0);
        long ts = t > 0 ? t * 1000 : System.currentTimeMillis();
        return Quote.of(m.path("symbol").asString(""), price, prev, open, high, low, volume, BigDecimal.ZERO, ts);
    }

    /** Parses Yahoo bars and re-buckets them into the app's interval (exchange-local calendar). */
    List<Candle> parseCandles(JsonNode result, Interval interval, ZoneId zone) {
        int scale = priceScale(result.path("meta"));
        JsonNode times = result.path("timestamp");
        JsonNode q = result.path("indicators").path("quote").path(0);
        Map<Long, Candle> buckets = new LinkedHashMap<>();
        for (int i = 0; i < times.size(); i++) {
            BigDecimal o = dec(q.path("open").path(i), scale);
            BigDecimal h = dec(q.path("high").path(i), scale);
            BigDecimal l = dec(q.path("low").path(i), scale);
            BigDecimal c = dec(q.path("close").path(i), scale);
            if (o == null || h == null || l == null || c == null) {
                continue; // Yahoo emits nulls for bars without trades
            }
            BigDecimal v = orElse(dec(q.path("volume").path(i), 0), BigDecimal.ZERO);
            long bucket = interval.bucketStart(Instant.ofEpochSecond(times.path(i).asLong()), zone);
            buckets.merge(bucket, new Candle(bucket, o, h, l, c, v), (a, b) ->
                    new Candle(a.time(), a.open(), a.high().max(b.high()), a.low().min(b.low()), b.close(),
                            a.volume().add(b.volume())));
        }
        List<Candle> out = new ArrayList<>(buckets.values());
        out.sort((a, b) -> Long.compare(a.time(), b.time()));
        return out;
    }

    /** Decimal places for prices: Yahoo's priceHint (2 for US, 3 for HK), clamped to 2–4. */
    private static int priceScale(JsonNode meta) {
        return Math.clamp(meta.path("priceHint").asInt(2), 2, 4);
    }

    /**
     * Converts a JSON number to BigDecimal at the given scale. Yahoo bar values are float32
     * artefacts (134.3000030517578), so rounding to the instrument's price precision restores
     * the exchange price exactly.
     */
    static BigDecimal dec(JsonNode v, int scale) {
        if (v == null || v.isMissingNode() || v.isNull() || !v.isNumber()) {
            return null;
        }
        return new BigDecimal(v.asString()).setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal orElse(BigDecimal v, BigDecimal fallback) {
        return v != null ? v : fallback;
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
