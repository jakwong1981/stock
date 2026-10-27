package com.cyberstock.portfolio;

import com.cyberstock.common.ApiException;
import com.cyberstock.instrument.Instrument;
import com.cyberstock.instrument.InstrumentService;
import com.cyberstock.market.Quote;
import com.cyberstock.market.QuoteService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    public record NewTransaction(String symbol, PortfolioTransaction.Side side, BigDecimal quantity,
                                 BigDecimal price, BigDecimal fee, Instant executedAt) {}

    private final PortfolioTransactionRepository repo;
    private final InstrumentService instruments;
    private final QuoteService quotes;

    public PortfolioService(PortfolioTransactionRepository repo, InstrumentService instruments, QuoteService quotes) {
        this.repo = repo;
        this.instruments = instruments;
        this.quotes = quotes;
    }

    public List<PortfolioTransaction> transactions(UUID userId) {
        return repo.findByUserIdOrderByExecutedAtAscIdAsc(userId);
    }

    public PortfolioCalculator.Result positions(UUID userId) {
        return calculate(transactions(userId));
    }

    @Transactional
    public PortfolioTransaction add(UUID userId, NewTransaction req) {
        Instrument inst = instruments.require(req.symbol());
        if (req.quantity().signum() <= 0 || req.price().signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION");
        }
        BigDecimal fee = req.fee() == null ? BigDecimal.ZERO : req.fee();
        if (fee.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION");
        }
        Instant at = req.executedAt() == null ? Instant.now() : req.executedAt();
        if (at.isAfter(Instant.now().plusSeconds(60))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRANSACTION_IN_FUTURE");
        }
        PortfolioTransaction tx = new PortfolioTransaction(userId, inst.getSymbol(), req.side(), req.quantity(),
                req.price(), fee, inst.getCurrency(), at);
        List<PortfolioTransaction> all = new ArrayList<>(transactions(userId));
        all.add(tx);
        calculate(all); // validates that no SELL exceeds holdings
        return repo.save(tx);
    }

    @Transactional
    public void delete(UUID userId, UUID txId) {
        PortfolioTransaction tx = repo.findByIdAndUserId(txId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND"));
        List<PortfolioTransaction> remaining = new ArrayList<>(transactions(userId));
        remaining.removeIf(t -> t.getId().equals(tx.getId()));
        calculate(remaining); // deleting a BUY must not leave a later SELL oversold
        repo.delete(tx);
    }

    private PortfolioCalculator.Result calculate(List<PortfolioTransaction> txs) {
        try {
            return PortfolioCalculator.calculate(txs, s -> quotes.latest(s).map(Quote::price));
        } catch (PortfolioCalculator.OversellException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_POSITION", Map.of("symbol", e.symbol()));
        }
    }
}
