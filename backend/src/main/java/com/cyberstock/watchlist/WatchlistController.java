package com.cyberstock.watchlist;

import com.cyberstock.auth.UserController;
import com.cyberstock.instrument.InstrumentService;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per-user ordered watchlist, synced across devices. */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    public record Watchlist(@Size(max = 200) List<String> symbols) {}

    private final JdbcTemplate jdbc;
    private final InstrumentService instruments;

    public WatchlistController(JdbcTemplate jdbc, InstrumentService instruments) {
        this.jdbc = jdbc;
        this.instruments = instruments;
    }

    @GetMapping
    public Watchlist get(@AuthenticationPrincipal Jwt jwt) {
        return new Watchlist(jdbc.queryForList(
                "SELECT symbol FROM watchlist_items WHERE user_id = ? ORDER BY position, symbol",
                String.class, UserController.userId(jwt)));
    }

    @PutMapping
    @Transactional
    public Watchlist replace(@AuthenticationPrincipal Jwt jwt, @RequestBody Watchlist body) {
        UUID uid = UserController.userId(jwt);
        // Keep only known instruments, de-duplicated, in the given order
        List<String> symbols = new LinkedHashSet<>(body.symbols() == null ? List.<String>of() : body.symbols())
                .stream()
                .limit(200)
                .map(instruments::find)
                .flatMap(java.util.Optional::stream)
                .map(i -> i.getSymbol())
                .distinct()
                .toList();
        jdbc.update("DELETE FROM watchlist_items WHERE user_id = ?", uid);
        for (int i = 0; i < symbols.size(); i++) {
            jdbc.update("INSERT INTO watchlist_items (user_id, symbol, position) VALUES (?, ?, ?)", uid, symbols.get(i), i);
        }
        return new Watchlist(symbols);
    }
}
