package com.cyberstock.portfolio;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Pure average-cost portfolio calculator. All arithmetic uses {@link BigDecimal}
 * (AGENT.MD §4.1); no floating point is involved.
 *
 * <ul>
 *   <li>BUY: cost basis += qty × price + fee</li>
 *   <li>SELL: realised P&L += qty × price − fee − avgCost × qty; cost basis reduced pro rata</li>
 *   <li>DIVIDEND: income += qty × dividendPerShare − fee (withholding tax can be entered as fee)</li>
 * </ul>
 */
public final class PortfolioCalculator {

    static final int MONEY_SCALE = 6;
    static final int AVG_SCALE = 8;
    private static final RoundingMode RM = RoundingMode.HALF_EVEN;

    private PortfolioCalculator() {}

    public record Position(
            String symbol,
            String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageCost,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal marketPrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal marketValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unrealizedPnl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unrealizedPnlPercent,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal realizedPnl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dividends) {}

    public record CurrencySummary(
            String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal marketValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unrealizedPnl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal realizedPnl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dividends) {}

    /** Result of a full recalculation; {@code positions} includes closed positions (qty 0). */
    public record Result(List<Position> positions, List<CurrencySummary> summaries) {}

    /** Thrown when a SELL exceeds the quantity held at that point in time. */
    public static class OversellException extends RuntimeException {
        private final String symbol;

        public OversellException(String symbol) {
            super("Oversell: " + symbol);
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    private static final class Acc {
        final String symbol;
        final String currency;
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal dividends = BigDecimal.ZERO;

        Acc(String symbol, String currency) {
            this.symbol = symbol;
            this.currency = currency;
        }
    }

    public static Result calculate(List<PortfolioTransaction> txs, Function<String, Optional<BigDecimal>> priceLookup) {
        List<PortfolioTransaction> sorted = new ArrayList<>(txs);
        sorted.sort(Comparator.comparing(PortfolioTransaction::getExecutedAt).thenComparing(PortfolioTransaction::getId));

        Map<String, Acc> accs = new LinkedHashMap<>();
        for (PortfolioTransaction t : sorted) {
            Acc a = accs.computeIfAbsent(t.getSymbol(), s -> new Acc(s, t.getCurrency()));
            BigDecimal gross = t.getQuantity().multiply(t.getPrice());
            switch (t.getSide()) {
                case BUY -> {
                    a.cost = a.cost.add(gross).add(t.getFee());
                    a.qty = a.qty.add(t.getQuantity());
                }
                case SELL -> {
                    if (t.getQuantity().compareTo(a.qty) > 0) {
                        throw new OversellException(t.getSymbol());
                    }
                    BigDecimal avg = a.cost.divide(a.qty, AVG_SCALE, RM);
                    BigDecimal costOut = t.getQuantity().compareTo(a.qty) == 0
                            ? a.cost // close out exactly, avoiding rounding residue
                            : avg.multiply(t.getQuantity()).setScale(MONEY_SCALE, RM);
                    a.realized = a.realized.add(gross.subtract(t.getFee()).subtract(costOut));
                    a.cost = a.cost.subtract(costOut);
                    a.qty = a.qty.subtract(t.getQuantity());
                }
                case DIVIDEND -> a.dividends = a.dividends.add(gross.subtract(t.getFee()));
            }
        }

        List<Position> positions = new ArrayList<>();
        Map<String, BigDecimal[]> totals = new LinkedHashMap<>();
        for (Acc a : accs.values()) {
            BigDecimal avg = a.qty.signum() == 0 ? BigDecimal.ZERO : a.cost.divide(a.qty, AVG_SCALE, RM);
            BigDecimal mkt = priceLookup.apply(a.symbol).orElse(null);
            BigDecimal value = mkt == null ? null : mkt.multiply(a.qty).setScale(MONEY_SCALE, RM);
            BigDecimal unreal = value == null ? null : value.subtract(a.cost).setScale(MONEY_SCALE, RM);
            BigDecimal pct = unreal == null || a.cost.signum() == 0 ? null
                    : unreal.multiply(BigDecimal.valueOf(100)).divide(a.cost, 4, RM);
            positions.add(new Position(a.symbol, a.currency, a.qty, avg, scale(a.cost), mkt, value, unreal, pct,
                    scale(a.realized), scale(a.dividends)));

            BigDecimal[] t = totals.computeIfAbsent(a.currency, c -> new BigDecimal[]{
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(a.cost);
            t[1] = t[1].add(value == null ? a.cost : value); // unknown price: carry at cost
            t[2] = t[2].add(unreal == null ? BigDecimal.ZERO : unreal);
            t[3] = t[3].add(a.realized);
            t[4] = t[4].add(a.dividends);
        }
        List<CurrencySummary> summaries = totals.entrySet().stream()
                .map(e -> new CurrencySummary(e.getKey(), scale(e.getValue()[0]), scale(e.getValue()[1]),
                        scale(e.getValue()[2]), scale(e.getValue()[3]), scale(e.getValue()[4])))
                .toList();
        return new Result(positions, summaries);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RM);
    }
}
