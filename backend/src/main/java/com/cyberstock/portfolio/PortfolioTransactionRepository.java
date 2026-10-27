package com.cyberstock.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, UUID> {

    List<PortfolioTransaction> findByUserIdOrderByExecutedAtAscIdAsc(UUID userId);

    Optional<PortfolioTransaction> findByIdAndUserId(UUID id, UUID userId);
}
