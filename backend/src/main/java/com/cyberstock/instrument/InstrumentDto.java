package com.cyberstock.instrument;

/** Instrument as exposed to clients, with the name localised to the request locale. */
public record InstrumentDto(
        String symbol,
        String name,
        String shortName,
        String exchange,
        String currency,
        String timezone,
        String logoUrl,
        boolean marquee) {}
