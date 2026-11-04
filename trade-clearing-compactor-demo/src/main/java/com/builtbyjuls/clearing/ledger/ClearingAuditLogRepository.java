package com.builtbyjuls.clearing.ledger;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClearingAuditLogRepository extends JpaRepository<ClearingAuditLog, Long> {

    List<ClearingAuditLog> findByTradeIdOrderByIdAsc(String tradeId);

    @Query("SELECT a FROM ClearingAuditLog a WHERE a.eventType = :eventType ORDER BY a.recordedAt DESC")
    List<ClearingAuditLog> findRecentEventsByType(@Param("eventType") String eventType);
}
