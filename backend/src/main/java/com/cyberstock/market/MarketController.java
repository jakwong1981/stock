package com.cyberstock.market;

import com.cyberstock.common.ApiException;
import com.cyberstock.instrument.InstrumentService;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MarketController {

    private final QuoteService quotes;
    private final CandleService candles;
    private final MarketSessionService sessions;

    public MarketController(QuoteService quotes, CandleService candles, MarketSessionService sessions) {
        this.quotes = quotes;
        this.candles = candles;
        this.sessions = sessions;
    }

    /** Latest quotes, e.g. {@code /api/quotes?symbols=AAPL,0700.HK}. Omit symbols for all. */
    @GetMapping("/quotes")
    public List<Quote> quotes(@RequestParam(required = false) String symbols) {
        if (symbols == null || symbols.isBlank()) {
            return List.copyOf(quotes.all());
        }
        List<String> list = Arrays.stream(symbols.split(","))
                .map(InstrumentService::normalize)
                .filter(s -> !s.isEmpty())
                .limit(100)
                .toList();
        return quotes.latest(list);
    }

    @GetMapping("/candles")
    public List<Candle> candles(@RequestParam String symbol,
                                @RequestParam(defaultValue = "1d") String interval,
                                @RequestParam(defaultValue = "300") int limit) {
        Interval iv;
        try {
            iv = Interval.fromCode(interval);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INTERVAL", Map.of("interval", interval));
        }
        return candles.history(symbol, iv, Math.clamp(limit, 1, CandleService.MAX_CANDLES));
    }

    @GetMapping("/market/status")
    public List<MarketSessionService.MarketStatus> status() {
        Instant now = Instant.now();
        return List.of(sessions.status("NASDAQ", now), sessions.status("NYSE", now), sessions.status("HKEX", now));
    }

    @GetMapping("/market/provider")
    public Map<String, String> provider() {
        return Map.of("provider", quotes.providerName());
    }
}
