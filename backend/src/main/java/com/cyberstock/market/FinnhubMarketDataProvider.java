package com.cyberstock.market;

import com.cyberstock.instrument.Instrument;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
 * Polls Finnhub's REST quote endpoint. The API key is sent in the {@code X-Finnhub-Token}
 * header (never in the URL) so it does not leak into access logs.
 */
public class FinnhubMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubMarketDataProvider.class);
    private static final String BASE = "https://finnhub.io/api/v1/quote?symbol=";

    private final String apiKey;
    private final long pollSeconds;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, BigDecimal> cumulativeVolume = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;

    public FinnhubMarketDataProvider(String apiKey, long pollSeconds, ObjectMapper mapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("FINNHUB_API_KEY is required when MARKET_DATA_PROVIDER=finnhub");
        }
        this.apiKey = apiKey;
        this.pollSeconds = Math.max(1, pollSeconds);
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "finnhub";
    }

    @Override
    public synchronized void start(Collection<Instrument> instruments, Consumer<Quote> sink) {
        List<String> symbols = instruments.stream().map(Instrument::getSymbol).toList();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "finnhub-poller");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(() -> symbols.forEach(s -> {
            try {
                fetch(s).ifPresent(sink);
            } catch (IOException e) {
                log.warn("Finnhub quote failed for {}: {}", s, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), 0, pollSeconds, TimeUnit.SECONDS);
        log.info("Finnhub provider polling {} symbols every {}s", symbols.size(), pollSeconds);
    }

    private java.util.Optional<Quote> fetch(String symbol) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + URLEncoder.encode(symbol, StandardCharsets.UTF_8)))
                .header("X-Finnhub-Token", apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.warn("Finnhub returned HTTP {} for {}", res.statusCode(), symbol);
            return java.util.Optional.empty();
        }
        JsonNode n = mapper.readTree(res.body());
        // Finnhub returns c=0 for unknown symbols
        BigDecimal price = decimal(n, "c");
        if (price.signum() == 0) {
            return java.util.Optional.empty();
        }
        BigDecimal vol = cumulativeVolume.getOrDefault(symbol, BigDecimal.ZERO);
        long ts = n.path("t").asLong(0) > 0 ? n.path("t").asLong() * 1000 : System.currentTimeMillis();
        return java.util.Optional.of(Quote.of(symbol, price, decimal(n, "pc"), decimal(n, "o"), decimal(n, "h"),
                decimal(n, "l"), vol, BigDecimal.ZERO, ts));
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        JsonNode v = n.path(field);
        // Parse from the textual representation to avoid binary floating-point artefacts
        return v.isMissingNode() || v.isNull() ? BigDecimal.ZERO : new BigDecimal(v.asString());
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
