package com.cyberstock.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.cyberstock.common.Locales;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketUnitTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final ZoneId HK = ZoneId.of("Asia/Hong_Kong");

    @Test
    void intradayBucketsAlignToEpochBoundaries() {
        Instant t = Instant.parse("2026-03-10T14:37:42Z");
        assertThat(Interval.M5.bucketStart(t, NY)).isEqualTo(Instant.parse("2026-03-10T14:35:00Z").getEpochSecond());
        assertThat(Interval.H1.bucketStart(t, NY)).isEqualTo(Instant.parse("2026-03-10T14:00:00Z").getEpochSecond());
    }

    @Test
    void dailyBucketsUseExchangeLocalMidnight() {
        // 2026-03-10 01:00 UTC is still 2026-03-09 in New York but already 2026-03-10 in Hong Kong
        Instant t = Instant.parse("2026-03-10T01:00:00Z");
        assertThat(Interval.D1.bucketStart(t, NY))
                .isEqualTo(ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, NY).toEpochSecond());
        assertThat(Interval.D1.bucketStart(t, HK))
                .isEqualTo(ZonedDateTime.of(2026, 3, 10, 0, 0, 0, 0, HK).toEpochSecond());
    }

    @Test
    void weeklyAndMonthlyBuckets() {
        Instant t = ZonedDateTime.of(2026, 3, 12, 10, 0, 0, 0, HK).toInstant(); // Thursday
        assertThat(Interval.W1.bucketStart(t, HK))
                .isEqualTo(ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, HK).toEpochSecond());
        assertThat(Interval.MN1.bucketStart(t, HK))
                .isEqualTo(ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, HK).toEpochSecond());
    }

    @Test
    void usSessionsAcrossDaylightSavingChange() {
        // US DST starts 2026-03-08. 14:00 UTC is 09:00 EST (pre-market) before and 10:00 EDT (regular) after.
        assertThat(MarketSessionService.unitedStates(Instant.parse("2026-03-06T14:00:00Z").atZone(NY)))
                .isEqualTo(MarketSessionService.Session.PRE_MARKET);
        assertThat(MarketSessionService.unitedStates(Instant.parse("2026-03-09T14:00:00Z").atZone(NY)))
                .isEqualTo(MarketSessionService.Session.REGULAR);
    }

    @Test
    void usSessionBoundaries() {
        assertThat(MarketSessionService.unitedStates(ZonedDateTime.of(2026, 3, 10, 9, 29, 0, 0, NY)))
                .isEqualTo(MarketSessionService.Session.PRE_MARKET);
        assertThat(MarketSessionService.unitedStates(ZonedDateTime.of(2026, 3, 10, 9, 30, 0, 0, NY)))
                .isEqualTo(MarketSessionService.Session.REGULAR);
        assertThat(MarketSessionService.unitedStates(ZonedDateTime.of(2026, 3, 10, 16, 0, 0, 0, NY)))
                .isEqualTo(MarketSessionService.Session.POST_MARKET);
        assertThat(MarketSessionService.unitedStates(ZonedDateTime.of(2026, 3, 14, 11, 0, 0, 0, NY)))
                .isEqualTo(MarketSessionService.Session.CLOSED); // Saturday
    }

    @Test
    void hongKongLunchBreak() {
        assertThat(MarketSessionService.hongKong(ZonedDateTime.of(2026, 3, 10, 12, 30, 0, 0, HK)))
                .isEqualTo(MarketSessionService.Session.LUNCH_BREAK);
        assertThat(MarketSessionService.hongKong(ZonedDateTime.of(2026, 3, 10, 13, 0, 0, 0, HK)))
                .isEqualTo(MarketSessionService.Session.REGULAR);
    }

    @Test
    void candleApplyMergesAndRolls() {
        var d = new ArrayDeque<Candle>();
        CandleService.apply(d, 60, new BigDecimal("10"), BigDecimal.ONE);
        CandleService.apply(d, 60, new BigDecimal("12"), BigDecimal.ONE);
        CandleService.apply(d, 60, new BigDecimal("9"), BigDecimal.ONE);
        CandleService.apply(d, 120, new BigDecimal("11"), BigDecimal.ONE);
        CandleService.apply(d, 60, new BigDecimal("99"), BigDecimal.ONE); // late tick ignored
        List<Candle> c = List.copyOf(d);
        assertThat(c).hasSize(2);
        assertThat(c.get(0).high()).isEqualByComparingTo("12");
        assertThat(c.get(0).low()).isEqualByComparingTo("9");
        assertThat(c.get(0).close()).isEqualByComparingTo("9");
        assertThat(c.get(0).volume()).isEqualByComparingTo("3");
        assertThat(c.get(1).open()).isEqualByComparingTo("9");
        assertThat(c.get(1).close()).isEqualByComparingTo("11");
    }

    @Test
    void synthesizedHistoryEndsAtCurrentPriceAndIsAscending() {
        List<Candle> c = CandleService.synthesize("AAPL", Interval.D1, new BigDecimal("230.00"), NY, Instant.now());
        assertThat(c).hasSize(CandleService.BACKFILL);
        assertThat(c.getLast().close()).isEqualByComparingTo("230.00");
        for (int i = 1; i < c.size(); i++) {
            assertThat(c.get(i).time()).isGreaterThan(c.get(i - 1).time());
            assertThat(c.get(i).high()).isGreaterThanOrEqualTo(c.get(i).low());
        }
    }

    @Test
    void quoteDerivesChangePercent() {
        Quote q = Quote.of("X", new BigDecimal("110"), new BigDecimal("100"), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        assertThat(q.change()).isEqualByComparingTo("10");
        assertThat(q.changePercent()).isEqualByComparingTo("10");
    }

    @Test
    void acceptLanguageResolution() {
        assertThat(Locales.resolve("zh-HK,zh;q=0.9")).isEqualTo("zh-HK");
        assertThat(Locales.resolve("zh-TW")).isEqualTo("zh-TW");
        assertThat(Locales.resolve("zh-Hant")).isEqualTo("zh-HK");
        assertThat(Locales.resolve("zh-Hans-CN")).isEqualTo("zh-CN");
        assertThat(Locales.resolve("zh-SG")).isEqualTo("zh-CN");
        assertThat(Locales.resolve("zh-MO")).isEqualTo("zh-HK");
        assertThat(Locales.resolve("fr-FR,en-GB;q=0.8")).isEqualTo("en-US");
        assertThat(Locales.resolve("ja-JP")).isEqualTo("en-US");
        assertThat(Locales.resolve(null)).isEqualTo("en-US");
    }
}
