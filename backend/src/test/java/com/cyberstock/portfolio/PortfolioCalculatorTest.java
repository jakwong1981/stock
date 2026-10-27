package com.cyberstock.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cyberstock.portfolio.PortfolioTransaction.Side;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioCalculatorTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-01-02T15:00:00Z");

    private static PortfolioTransaction tx(String sym, Side side, String qty, String price, String fee, int dayOffset,
                                           String ccy) {
        return new PortfolioTransaction(USER, sym, side, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fee),
                ccy, T0.plusSeconds(86400L * dayOffset));
    }

    private static Optional<BigDecimal> prices(String s) {
        return Optional.ofNullable(Map.of("AAPL", new BigDecimal("150"), "0700.HK", new BigDecimal("500")).get(s));
    }

    @Test
    void averageCostWithPartialSellAndFees() {
        var r = PortfolioCalculator.calculate(List.of(
                tx("AAPL", Side.BUY, "10", "100", "1", 0, "USD"),
                tx("AAPL", Side.BUY, "10", "120", "1", 1, "USD"),
                tx("AAPL", Side.SELL, "5", "130", "1", 2, "USD")), PortfolioCalculatorTest::prices);

        var p = r.positions().getFirst();
        // cost = 1000+1 + 1200+1 = 2202 for 20 shares → avg 110.1
        assertThat(p.quantity()).isEqualByComparingTo("15");
        assertThat(p.averageCost()).isEqualByComparingTo("110.1");
        // realised = 5*130 - 1 - 5*110.1 = 98.5
        assertThat(p.realizedPnl()).isEqualByComparingTo("98.5");
        assertThat(p.costBasis()).isEqualByComparingTo("1651.5");
        assertThat(p.marketValue()).isEqualByComparingTo("2250");
        assertThat(p.unrealizedPnl()).isEqualByComparingTo("598.5");
    }

    @Test
    void decimalPrecisionHasNoFloatingPointDrift() {
        var r = PortfolioCalculator.calculate(List.of(
                tx("AAPL", Side.BUY, "0.1", "0.2", "0", 0, "USD"),
                tx("AAPL", Side.BUY, "0.2", "0.1", "0", 1, "USD")), s -> Optional.empty());
        // 0.1*0.2 + 0.2*0.1 = 0.04 exactly (double arithmetic would give 0.04000000000000001)
        assertThat(r.positions().getFirst().costBasis()).isEqualByComparingTo("0.04");
    }

    @Test
    void fullSellClosesPositionWithoutResidue() {
        var r = PortfolioCalculator.calculate(List.of(
                tx("AAPL", Side.BUY, "3", "10", "0", 0, "USD"),
                tx("AAPL", Side.SELL, "1", "10", "0", 1, "USD"),
                tx("AAPL", Side.SELL, "2", "10", "0", 2, "USD")), PortfolioCalculatorTest::prices);
        var p = r.positions().getFirst();
        assertThat(p.quantity()).isEqualByComparingTo("0");
        assertThat(p.costBasis()).isEqualByComparingTo("0");
        assertThat(p.realizedPnl()).isEqualByComparingTo("0");
    }

    @Test
    void dividendsNetOfWithholding() {
        var r = PortfolioCalculator.calculate(List.of(
                tx("0700.HK", Side.BUY, "100", "400", "50", 0, "HKD"),
                tx("0700.HK", Side.DIVIDEND, "100", "3.4", "0", 30, "HKD")), PortfolioCalculatorTest::prices);
        assertThat(r.positions().getFirst().dividends()).isEqualByComparingTo("340");
    }

    @Test
    void summariesAreGroupedPerCurrency() {
        var r = PortfolioCalculator.calculate(List.of(
                tx("AAPL", Side.BUY, "1", "100", "0", 0, "USD"),
                tx("0700.HK", Side.BUY, "100", "400", "0", 0, "HKD")), PortfolioCalculatorTest::prices);
        assertThat(r.summaries()).extracting(PortfolioCalculator.CurrencySummary::currency)
                .containsExactlyInAnyOrder("USD", "HKD");
    }

    @Test
    void sellingMoreThanHeldIsRejected() {
        assertThatThrownBy(() -> PortfolioCalculator.calculate(List.of(
                tx("AAPL", Side.BUY, "1", "100", "0", 0, "USD"),
                tx("AAPL", Side.SELL, "2", "100", "0", 1, "USD")), s -> Optional.empty()))
                .isInstanceOf(PortfolioCalculator.OversellException.class);
    }

    @Test
    void transactionsAreProcessedChronologically() {
        // SELL recorded first in the list but executed after the BUY → valid
        var r = PortfolioCalculator.calculate(List.of(
                tx("AAPL", Side.SELL, "1", "110", "0", 5, "USD"),
                tx("AAPL", Side.BUY, "1", "100", "0", 0, "USD")), s -> Optional.empty());
        assertThat(r.positions().getFirst().realizedPnl()).isEqualByComparingTo("10");
    }
}
