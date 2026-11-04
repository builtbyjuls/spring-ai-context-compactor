package com.builtbyjuls.clearing.ledger;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarginCallRecordRepository extends JpaRepository<MarginCallRecord, Long> {

    List<MarginCallRecord> findByMemberCode(String memberCode);

    @Query("SELECT m FROM MarginCallRecord m WHERE m.memberCode = :memberCode AND m.status = 'ISSUED'")
    List<MarginCallRecord> findActiveIssuedCallsByMemberCode(@Param("memberCode") String memberCode);
}
