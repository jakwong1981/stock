package com.cyberstock.portfolio;

import com.cyberstock.auth.UserController;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Portfolio ledger. Every query is scoped to the authenticated user's id. */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    /** Decimal fields are accepted as JSON strings (or numbers) and parsed exactly. */
    public record TransactionRequest(
            @NotBlank String symbol,
            @NotNull PortfolioTransaction.Side side,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal price,
            @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal fee,
            Instant executedAt) {}

    public record TransactionDto(
            UUID id, String symbol, PortfolioTransaction.Side side,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal fee,
            String currency, Instant executedAt) {
        static TransactionDto from(PortfolioTransaction t) {
            return new TransactionDto(t.getId(), t.getSymbol(), t.getSide(), t.getQuantity(), t.getPrice(),
                    t.getFee(), t.getCurrency(), t.getExecutedAt());
        }
    }

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping("/transactions")
    public List<TransactionDto> transactions(@AuthenticationPrincipal Jwt jwt) {
        return service.transactions(UserController.userId(jwt)).stream().map(TransactionDto::from).toList();
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDto add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransactionRequest req) {
        PortfolioTransaction t = service.add(UserController.userId(jwt), new PortfolioService.NewTransaction(
                req.symbol(), req.side(), req.quantity(), req.price(), req.fee(), req.executedAt()));
        return TransactionDto.from(t);
    }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(UserController.userId(jwt), id);
    }

    @GetMapping("/positions")
    public PortfolioCalculator.Result positions(@AuthenticationPrincipal Jwt jwt) {
        return service.positions(UserController.userId(jwt));
    }
}
