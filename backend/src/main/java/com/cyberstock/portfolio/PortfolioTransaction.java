package com.cyberstock.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portfolio_transactions")
public class PortfolioTransaction {

    public enum Side { BUY, SELL, DIVIDEND }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    /** Shares traded; for DIVIDEND the number of shares the dividend was paid on. */
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    /** Price per share; for DIVIDEND the dividend per share. */
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal price;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal fee;

    @Column(nullable = false)
    private String currency;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected PortfolioTransaction() {}

    public PortfolioTransaction(UUID userId, String symbol, Side side, BigDecimal quantity, BigDecimal price,
                                BigDecimal fee, String currency, Instant executedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
        this.currency = currency;
        this.executedAt = executedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getFee() { return fee; }
    public String getCurrency() { return currency; }
    public Instant getExecutedAt() { return executedAt; }
}
