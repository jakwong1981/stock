package com.cyberstock.market;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Determines the current trading session for an exchange using its local time zone.
 * Exchange holidays are not modelled yet; on holidays the session is reported as if it
 * were a normal weekday.
 */
@Service
public class MarketSessionService {

    public enum Session { PRE_MARKET, REGULAR, LUNCH_BREAK, POST_MARKET, CLOSED }

    public record MarketStatus(String exchange, String timezone, Session session, String localTime) {}

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");

    public MarketStatus status(String exchange, Instant now) {
        String ex = exchange.toUpperCase(Locale.ROOT);
        ZoneId zone = switch (ex) {
            case "HKEX" -> HONG_KONG;
            case "NASDAQ", "NYSE" -> NEW_YORK;
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };
        ZonedDateTime local = now.atZone(zone);
        Session session = "HKEX".equals(ex) ? hongKong(local) : unitedStates(local);
        return new MarketStatus(ex, zone.getId(), session, local.toLocalDateTime().toString());
    }

    static Session unitedStates(ZonedDateTime t) {
        if (isWeekend(t)) {
            return Session.CLOSED;
        }
        LocalTime lt = t.toLocalTime();
        if (between(lt, "04:00", "09:30")) return Session.PRE_MARKET;
        if (between(lt, "09:30", "16:00")) return Session.REGULAR;
        if (between(lt, "16:00", "20:00")) return Session.POST_MARKET;
        return Session.CLOSED;
    }

    static Session hongKong(ZonedDateTime t) {
        if (isWeekend(t)) {
            return Session.CLOSED;
        }
        LocalTime lt = t.toLocalTime();
        if (between(lt, "09:00", "09:30")) return Session.PRE_MARKET;      // pre-opening session
        if (between(lt, "09:30", "12:00")) return Session.REGULAR;
        if (between(lt, "12:00", "13:00")) return Session.LUNCH_BREAK;
        if (between(lt, "13:00", "16:00")) return Session.REGULAR;
        if (between(lt, "16:00", "16:10")) return Session.POST_MARKET;     // closing auction session
        return Session.CLOSED;
    }

    private static boolean isWeekend(ZonedDateTime t) {
        DayOfWeek d = t.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    private static boolean between(LocalTime t, String from, String to) {
        return !t.isBefore(LocalTime.parse(from)) && t.isBefore(LocalTime.parse(to));
    }
}
