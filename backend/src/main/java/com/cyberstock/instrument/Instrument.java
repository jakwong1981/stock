package com.cyberstock.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZoneId;

@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String exchange;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_marquee", nullable = false)
    private boolean marquee;

    protected Instrument() {}

    public Instrument(String symbol, String name, String exchange, String currency, String timezone,
                      String logoUrl, boolean marquee) {
        this.symbol = symbol;
        this.name = name;
        this.exchange = exchange;
        this.currency = currency;
        this.timezone = timezone;
        this.logoUrl = logoUrl;
        this.marquee = marquee;
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public String getExchange() { return exchange; }
    public String getCurrency() { return currency; }
    public String getTimezone() { return timezone; }
    public ZoneId zoneId() { return ZoneId.of(timezone); }
    public String getLogoUrl() { return logoUrl; }
    public boolean isActive() { return active; }
    public boolean isMarquee() { return marquee; }
}
