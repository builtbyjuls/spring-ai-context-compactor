package com.builtbyjuls.clearing.ledger;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClearingAuditService {

    private final ClearingAuditLogRepository clearingAuditLogRepository;

    public ClearingAuditService(ClearingAuditLogRepository clearingAuditLogRepository) {
        this.clearingAuditLogRepository = clearingAuditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClearingAuditLog recordAuditEvent(String tradeId, String eventType, String detail) {
        ClearingAuditLog log = new ClearingAuditLog(tradeId, eventType, detail, Instant.now());
        return clearingAuditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClearingAuditLog recordComplianceStamp(String tradeId, String engineName) {
        return recordAuditEvent(tradeId, "INTERNAL_COMPLIANCE_STAMP", "Engine: " + engineName);
    }
}
