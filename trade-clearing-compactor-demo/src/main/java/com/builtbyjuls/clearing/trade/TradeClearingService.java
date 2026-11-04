package com.builtbyjuls.clearing.trade;

import com.builtbyjuls.clearing.ledger.ClearedTrade;
import com.builtbyjuls.clearing.ledger.ClearedTradeRepository;
import com.builtbyjuls.clearing.ledger.ClearingAuditLog;
import com.builtbyjuls.clearing.ledger.ClearingAuditLogRepository;
import com.builtbyjuls.clearing.ledger.ClearingAuditService;
import com.builtbyjuls.clearing.ledger.ClearingMember;
import com.builtbyjuls.clearing.ledger.ClearingMemberRepository;
import com.builtbyjuls.clearing.ledger.MarginCallRecord;
import com.builtbyjuls.clearing.ledger.MarginCallRecordRepository;
import com.builtbyjuls.clearing.margin.MarginCalculator;
import com.builtbyjuls.clearing.messaging.MarginCallEvent;
import com.builtbyjuls.clearing.messaging.MarginCallPublisher;
import com.builtbyjuls.clearing.messaging.TradeSubmissionMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeClearingService {

    private final ClearingMemberRepository clearingMemberRepository;
    private final ClearedTradeRepository clearedTradeRepository;
    private final MarginCallRecordRepository marginCallRecordRepository;
    private final MarginCalculator marginCalculator;
    private final ClearingAuditService clearingAuditService;
    private final ClearingAuditLogRepository clearingAuditLogRepository;
    private final MarginCallPublisher marginCallPublisher;

    public TradeClearingService(
            ClearingMemberRepository clearingMemberRepository,
            ClearedTradeRepository clearedTradeRepository,
            MarginCallRecordRepository marginCallRecordRepository,
            MarginCalculator marginCalculator,
            ClearingAuditService clearingAuditService,
            ClearingAuditLogRepository clearingAuditLogRepository,
            MarginCallPublisher marginCallPublisher) {
        this.clearingMemberRepository = clearingMemberRepository;
        this.clearedTradeRepository = clearedTradeRepository;
        this.marginCallRecordRepository = marginCallRecordRepository;
        this.marginCalculator = marginCalculator;
        this.clearingAuditService = clearingAuditService;
        this.clearingAuditLogRepository = clearingAuditLogRepository;
        this.marginCallPublisher = marginCallPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public ClearedTradePositionDto novateAndClearTrade(TradeSubmissionMessage submission) {
        validateSubmission(submission);
        ClearingMember member = validateAndResolveActiveMember(submission.memberCode());

        BigDecimal requiredMargin = marginCalculator.calculateRequiredMargin(
                submission.instrumentSymbol(),
                submission.assetClass(),
                submission.notionalAmount()
        );

        boolean marginCallIssued = requiredMargin.compareTo(member.getInitialMarginCollateral()) > 0;
        String status = marginCallIssued ? "MARGIN_CALL_PENDING" : "CLEARED";

        ClearedTrade trade = ClearedTrade.builder()
                .tradeId(submission.tradeId())
                .memberCode(submission.memberCode())
                .instrumentSymbol(submission.instrumentSymbol())
                .assetClass(submission.assetClass())
                .notionalAmount(submission.notionalAmount())
                .requiredMargin(requiredMargin)
                .marginEngineUsed(marginCalculator.engineName())
                .status(status)
                .tradeDate(submission.tradeDate())
                .clearedAt(Instant.now())
                .build();
        clearedTradeRepository.save(trade);

        clearingAuditService.recordAuditEvent(
                submission.tradeId(),
                "TRADE_NOVATION",
                formatAuditSummary(submission.tradeId(), status, marginCalculator.engineName())
        );

        clearingAuditService.recordComplianceStamp(submission.tradeId(), marginCalculator.engineName());

        if (marginCallIssued) {
            BigDecimal deficit = requiredMargin.subtract(member.getInitialMarginCollateral());
            String callRef = formatCallReference(submission.tradeId());
            MarginCallRecord callRecord = MarginCallRecord.builder()
                    .callReference(callRef)
                    .tradeId(submission.tradeId())
                    .memberCode(submission.memberCode())
                    .deficitAmount(deficit)
                    .status("ISSUED")
                    .createdAt(Instant.now())
                    .build();
            marginCallRecordRepository.save(callRecord);

            MarginCallEvent event = new MarginCallEvent(
                    callRef,
                    submission.tradeId(),
                    submission.memberCode(),
                    deficit,
                    callRecord.getCreatedAt()
            );
            marginCallPublisher.publishMarginCall(event);
        }

        return new ClearedTradePositionDto(
                submission.tradeId(),
                submission.memberCode(),
                submission.notionalAmount(),
                requiredMargin,
                marginCalculator.engineName(),
                status,
                marginCallIssued
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInternalComplianceStamp(String tradeId, String engineName) {
        ClearingAuditLog log = ClearingAuditLog.builder()
                .tradeId(tradeId)
                .eventType("INTERNAL_COMPLIANCE_STAMP")
                .detail("Engine: " + engineName)
                .recordedAt(Instant.now())
                .build();
        clearingAuditLogRepository.save(log);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public List<ClearedTrade> inspectMemberPortfolioWithSnapshotTouch(String memberCode) {
        List<ClearedTrade> trades = clearedTradeRepository.findByMemberCodeOrderByClearedAtDesc(memberCode);
        this.recordInternalComplianceStamp(memberCode, "PORTFOLIO_INSPECTION");
        return trades;
    }

    @Transactional
    protected void archiveExpiredTradeNotice(String tradeId) {
        ClearingAuditLog log = ClearingAuditLog.builder()
                .tradeId(tradeId)
                .eventType("EXPIRED_TRADE_ARCHIVED")
                .detail("Archived notice for trade " + tradeId)
                .recordedAt(Instant.now())
                .build();
        clearingAuditLogRepository.save(log);
    }

    private void validateSubmission(TradeSubmissionMessage submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Trade submission must not be null");
        }
        if (submission.tradeId() == null || submission.tradeId().isBlank()) {
            throw new IllegalArgumentException("Trade ID must not be blank");
        }
    }

    private ClearingMember validateAndResolveActiveMember(String memberCode) {
        ClearingMember member = clearingMemberRepository.findByMemberCode(memberCode)
                .orElseThrow(() -> new IllegalArgumentException("Clearing member not found: " + memberCode));
        if (!member.isActive()) {
            throw new IllegalArgumentException("Clearing member is inactive: " + memberCode);
        }
        return member;
    }

    private String formatAuditSummary(String tradeId, String status, String engineName) {
        return "Trade " + tradeId + " novated with status " + status + " using engine " + engineName;
    }

    private String formatCallReference(String tradeId) {
        return "MC-" + tradeId;
    }
}
