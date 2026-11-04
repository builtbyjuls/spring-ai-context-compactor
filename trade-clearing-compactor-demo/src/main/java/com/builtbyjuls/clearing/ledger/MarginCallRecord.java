package com.builtbyjuls.clearing.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "margin_call_records")
public class MarginCallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_reference", nullable = false, unique = true, length = 64)
    private String callReference;

    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    @Column(name = "member_code", nullable = false, length = 32)
    private String memberCode;

    @Column(name = "deficit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal deficitAmount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MarginCallRecord() {
    }

    public MarginCallRecord(
            String callReference,
            String tradeId,
            String memberCode,
            BigDecimal deficitAmount,
            String status,
            Instant createdAt) {
        this.callReference = callReference;
        this.tradeId = tradeId;
        this.memberCode = memberCode;
        this.deficitAmount = deficitAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public MarginCallRecord(
            Long id,
            String callReference,
            String tradeId,
            String memberCode,
            BigDecimal deficitAmount,
            String status,
            Instant createdAt) {
        this.id = id;
        this.callReference = callReference;
        this.tradeId = tradeId;
        this.memberCode = memberCode;
        this.deficitAmount = deficitAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCallReference() {
        return callReference;
    }

    public void setCallReference(String callReference) {
        this.callReference = callReference;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getMemberCode() {
        return memberCode;
    }

    public void setMemberCode(String memberCode) {
        this.memberCode = memberCode;
    }

    public BigDecimal getDeficitAmount() {
        return deficitAmount;
    }

    public void setDeficitAmount(BigDecimal deficitAmount) {
        this.deficitAmount = deficitAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MarginCallRecord that = (MarginCallRecord) o;
        return Objects.equals(callReference, that.callReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callReference);
    }

    @Override
    public String toString() {
        return "MarginCallRecord{" +
                "id=" + id +
                ", callReference='" + callReference + '\'' +
                ", tradeId='" + tradeId + '\'' +
                ", memberCode='" + memberCode + '\'' +
                ", deficitAmount=" + deficitAmount +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String callReference;
        private String tradeId;
        private String memberCode;
        private BigDecimal deficitAmount;
        private String status;
        private Instant createdAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder callReference(String callReference) {
            this.callReference = callReference;
            return this;
        }

        public Builder tradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder memberCode(String memberCode) {
            this.memberCode = memberCode;
            return this;
        }

        public Builder deficitAmount(BigDecimal deficitAmount) {
            this.deficitAmount = deficitAmount;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public MarginCallRecord build() {
            return new MarginCallRecord(id, callReference, tradeId, memberCode, deficitAmount, status, createdAt);
        }
    }
}
