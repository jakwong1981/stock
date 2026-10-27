package com.cyberstock.market;

import com.cyberstock.config.AppProperties;
import com.cyberstock.instrument.InstrumentService;
import jakarta.annotation.PreDestroy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Holds the latest quote per symbol and fans incoming ticks out to listeners. */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final Map<String, Quote> latest = new ConcurrentHashMap<>();
    private final List<Consumer<Quote>> listeners = new CopyOnWriteArrayList<>();
    private final InstrumentService instruments;
    private final MarketDataProvider provider;

    public QuoteService(InstrumentService instruments, AppProperties props, ObjectMapper mapper) {
        this.instruments = instruments;
        AppProperties.Market m = props.market();
        String p = m.provider() == null ? "mock" : m.provider().trim().toLowerCase(java.util.Locale.ROOT);
        this.provider = switch (p) {
            case "yahoo" -> new YahooMarketDataProvider(m.yahooPollSeconds(), mapper);
            case "finnhub" -> new FinnhubMarketDataProvider(m.finnhubApiKey(), m.finnhubPollSeconds(), mapper);
            case "mock" -> new MockMarketDataProvider(m.tickIntervalMs(), System.nanoTime());
            default -> throw new IllegalStateException("Unknown MARKET_DATA_PROVIDER: " + m.provider()
                    + " (expected mock | yahoo | finnhub)");
        };
    }

    public MarketDataProvider provider() {
        return provider;
    }

    public void addListener(Consumer<Quote> listener) {
        listeners.add(listener);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startStreaming() {
        log.info("Starting market data provider '{}'", provider.name());
        provider.start(instruments.activeInstruments(), this::publish);
    }

    void publish(Quote q) {
        latest.put(q.symbol(), q);
        for (Consumer<Quote> l : listeners) {
            try {
                l.accept(q);
            } catch (RuntimeException e) {
                log.warn("Quote listener failed", e);
            }
        }
    }

    public Optional<Quote> latest(String symbol) {
        return symbol == null ? Optional.empty() : Optional.ofNullable(latest.get(InstrumentService.normalize(symbol)));
    }

    public List<Quote> latest(Collection<String> symbols) {
        return symbols.stream().map(this::latest).flatMap(Optional::stream).toList();
    }

    public Collection<Quote> all() {
        return latest.values();
    }

    public String providerName() {
        return provider.name();
    }

    @PreDestroy
    public void shutdown() {
        provider.stop();
    }
}
