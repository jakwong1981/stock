package com.cyberstock.instrument;

import com.cyberstock.common.ApiException;
import com.cyberstock.common.Locales;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Instrument master data. The catalogue is small and changes rarely, so it is cached in
 * memory after the first load; call {@link #reload()} after admin changes.
 */
@Service
public class InstrumentService {

    private final InstrumentRepository instruments;
    private final InstrumentNameRepository names;

    private volatile Map<String, Instrument> bySymbol = Map.of();
    private volatile Map<String, Map<String, InstrumentName>> namesBySymbol = Map.of();

    public InstrumentService(InstrumentRepository instruments, InstrumentNameRepository names) {
        this.instruments = instruments;
        this.names = names;
    }

    public synchronized void reload() {
        Map<String, Instrument> map = new HashMap<>();
        instruments.findByActiveTrueOrderBySymbol().forEach(i -> map.put(i.getSymbol(), i));
        Map<String, Map<String, InstrumentName>> nm = new ConcurrentHashMap<>();
        names.findAllNames().forEach(n ->
                nm.computeIfAbsent(n.getSymbol(), k -> new HashMap<>()).put(n.getLocale(), n));
        this.bySymbol = Map.copyOf(map);
        this.namesBySymbol = Map.copyOf(nm);
    }

    private void ensureLoaded() {
        if (bySymbol.isEmpty()) {
            reload();
        }
    }

    public List<Instrument> activeInstruments() {
        ensureLoaded();
        return bySymbol.values().stream().sorted((a, b) -> a.getSymbol().compareTo(b.getSymbol())).toList();
    }

    public Optional<Instrument> find(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        return Optional.ofNullable(bySymbol.get(normalize(symbol)));
    }

    public Instrument require(String symbol) {
        return find(symbol).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", Map.of("symbol", String.valueOf(symbol))));
    }

    public List<InstrumentDto> list(String locale, boolean marqueeOnly) {
        return activeInstruments().stream()
                .filter(i -> !marqueeOnly || i.isMarquee())
                .map(i -> toDto(i, locale))
                .toList();
    }

    public List<InstrumentDto> search(String query, String locale) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.trim();
        if (q.length() > 40) {
            q = q.substring(0, 40);
        }
        // Escape LIKE wildcards supplied by the user
        String escaped = q.replace("%", "").replace("_", "");
        return names.searchSymbols(escaped).stream()
                .map(this::find)
                .flatMap(Optional::stream)
                .distinct()
                .limit(20)
                .map(i -> toDto(i, locale))
                .toList();
    }

    public InstrumentDto toDto(Instrument i, String locale) {
        ensureLoaded();
        Map<String, InstrumentName> localized = namesBySymbol.getOrDefault(i.getSymbol(), Map.of());
        InstrumentName n = localized.get(locale);
        if (n == null && Locales.ZH_HK.equals(locale)) {
            n = localized.get(Locales.ZH_TW); // fallback chain zh-HK -> zh-TW -> en-US
        }
        if (n == null) {
            n = localized.get(Locales.EN_US);
        }
        String name = n != null ? n.getName() : i.getName();
        String shortName = n != null && n.getShortName() != null ? n.getShortName() : name;
        return new InstrumentDto(i.getSymbol(), name, shortName, i.getExchange(), i.getCurrency(),
                i.getTimezone(), i.getLogoUrl(), i.isMarquee());
    }

    public static String normalize(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
