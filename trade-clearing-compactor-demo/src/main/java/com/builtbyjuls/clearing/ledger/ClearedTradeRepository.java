package com.builtbyjuls.clearing.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClearedTradeRepository extends JpaRepository<ClearedTrade, Long> {

    Optional<ClearedTrade> findByTradeId(String tradeId);

    List<ClearedTrade> findByMemberCodeOrderByClearedAtDesc(String memberCode);

    @Query("SELECT t FROM ClearedTrade t WHERE t.memberCode = :memberCode AND t.status = :status ORDER BY t.clearedAt DESC")
    List<ClearedTrade> findByMemberCodeAndStatus(@Param("memberCode") String memberCode, @Param("status") String status);
}
