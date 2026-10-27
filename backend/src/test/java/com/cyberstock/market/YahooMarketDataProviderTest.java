package com.cyberstock.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Parses recorded Yahoo chart responses (no network access). */
class YahooMarketDataProviderTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final YahooMarketDataProvider provider = new YahooMarketDataProvider(15, mapper);
    private static final ZoneId HK = ZoneId.of("Asia/Hong_Kong");

    // Shape of https://query1.finance.yahoo.com/v8/finance/chart/0005.HK?interval=1d&range=1d
    private static final String HSBC_QUOTE = """
            {"chart":{"result":[{"meta":{"currency":"HKD","symbol":"0005.HK","regularMarketTime":1793088499,
             "exchangeTimezoneName":"Asia/Hong_Kong","regularMarketPrice":134.3,"regularMarketDayHigh":143.3,
             "regularMarketDayLow":132.6,"regularMarketVolume":36513272,"chartPreviousClose":142.2,"priceHint":3},
             "timestamp":[1793064600],
             "indicators":{"quote":[{"close":[134.3000030517578],"open":[141.39999389648438],
               "volume":[36512872],"high":[143.3000030517578],"low":[132.60000610351562]}]}}],"error":null}}
            """;

    @Test
    void parsesQuoteWithExactDecimals() {
        Quote q = provider.parseQuote(mapper.readTree(HSBC_QUOTE).path("chart").path("result").path(0));
        assertThat(q.symbol()).isEqualTo("0005.HK");
        assertThat(q.price()).isEqualByComparingTo("134.30");
        assertThat(q.price().toPlainString()).isEqualTo("134.300"); // priceHint 3
        assertThat(q.prevClose()).isEqualByComparingTo("142.2");
        // float32 artefact 141.39999389648438 is restored to the exchange price
        assertThat(q.open().toPlainString()).isEqualTo("141.400");
        assertThat(q.high()).isEqualByComparingTo("143.3");
        assertThat(q.low()).isEqualByComparingTo("132.6");
        assertThat(q.volume()).isEqualByComparingTo("36513272");
        assertThat(q.change()).isEqualByComparingTo("-7.9");
        assertThat(q.changePercent()).isEqualByComparingTo("-5.5556");
        assertThat(q.ts()).isEqualTo(1793088499000L);
    }

    @Test
    void aggregatesHourlyBarsIntoFourHourCandlesAndSkipsNulls() {
        long t0 = ZonedDateTime.of(2026, 10, 27, 9, 0, 0, 0, HK).toEpochSecond(); // 01:00 UTC
        String json = """
                {"meta":{"priceHint":2},"timestamp":[%d,%d,%d,%d],
                 "indicators":{"quote":[{"open":[10.0,11.0,null,12.0],"high":[11.5,12.5,null,13.0],
                   "low":[9.5,10.5,null,11.0],"close":[11.0,12.0,null,12.5],"volume":[100,200,null,300]}]}}
                """.formatted(t0, t0 + 3600, t0 + 7200, t0 + 10800);
        List<Candle> c = provider.parseCandles(mapper.readTree(json), Interval.H4, HK);
        // 4h buckets are UTC-aligned: 01:00 and 02:00 UTC fall in 00:00–04:00, 04:00 UTC starts a new one
        assertThat(c).hasSize(2);
        assertThat(c.get(0).open()).isEqualByComparingTo("10");
        assertThat(c.get(0).high()).isEqualByComparingTo("12.5");
        assertThat(c.get(0).close()).isEqualByComparingTo("12");
        assertThat(c.get(0).volume()).isEqualByComparingTo("300");
        assertThat(c.get(1).open()).isEqualByComparingTo("12");
        assertThat(c.get(1).time()).isGreaterThan(c.get(0).time());
    }

    @Test
    void dailyBarsAlignToExchangeLocalMidnight() {
        long open = ZonedDateTime.of(2026, 10, 27, 9, 30, 0, 0, HK).toEpochSecond();
        String json = """
                {"meta":{"priceHint":3},"timestamp":[%d],
                 "indicators":{"quote":[{"open":[141.4],"high":[143.3],"low":[132.6],"close":[134.3],"volume":[1]}]}}
                """.formatted(open);
        List<Candle> c = provider.parseCandles(mapper.readTree(json), Interval.D1, HK);
        assertThat(c.getFirst().time()).isEqualTo(ZonedDateTime.of(2026, 10, 27, 0, 0, 0, 0, HK).toEpochSecond());
    }

    @Test
    void everyIntervalHasAYahooSource() {
        for (Interval i : Interval.values()) {
            assertThat(YahooMarketDataProvider.SOURCES).containsKey(i);
        }
    }
}
