package com.builtbyjuls.clearing.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClearingMemberRepository extends JpaRepository<ClearingMember, Long> {

    Optional<ClearingMember> findByMemberCode(String memberCode);

    @Query("SELECT m FROM ClearingMember m WHERE m.tier = :tier AND m.active = true")
    List<ClearingMember> findActiveMembersByTier(@Param("tier") String tier);
}
