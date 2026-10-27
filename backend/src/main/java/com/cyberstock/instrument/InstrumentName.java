package com.cyberstock.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "instrument_names")
public class InstrumentName {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "symbol")
        private String symbol;
        @Column(name = "locale")
        private String locale;

        protected Key() {}

        public Key(String symbol, String locale) {
            this.symbol = symbol;
            this.locale = locale;
        }

        public String getSymbol() { return symbol; }
        public String getLocale() { return locale; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(symbol, k.symbol) && Objects.equals(locale, k.locale);
        }

        @Override
        public int hashCode() {
            return Objects.hash(symbol, locale);
        }
    }

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    protected InstrumentName() {}

    public String getSymbol() { return id.getSymbol(); }
    public String getLocale() { return id.getLocale(); }
    public String getName() { return name; }
    public String getShortName() { return shortName; }
}
