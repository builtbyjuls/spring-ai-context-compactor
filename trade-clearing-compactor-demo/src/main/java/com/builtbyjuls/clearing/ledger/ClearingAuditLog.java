package com.builtbyjuls.clearing.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "clearing_audit_logs")
public class ClearingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "detail", nullable = false, length = 255)
    private String detail;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ClearingAuditLog() {
    }

    public ClearingAuditLog(String tradeId, String eventType, String detail, Instant recordedAt) {
        this.tradeId = tradeId;
        this.eventType = eventType;
        this.detail = detail;
        this.recordedAt = recordedAt;
    }

    public ClearingAuditLog(Long id, String tradeId, String eventType, String detail, Instant recordedAt) {
        this.id = id;
        this.tradeId = tradeId;
        this.eventType = eventType;
        this.detail = detail;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClearingAuditLog that = (ClearingAuditLog) o;
        return Objects.equals(id, that.id)
                && Objects.equals(tradeId, that.tradeId)
                && Objects.equals(eventType, that.eventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tradeId, eventType);
    }

    @Override
    public String toString() {
        return "ClearingAuditLog{" +
                "id=" + id +
                ", tradeId='" + tradeId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", detail='" + detail + '\'' +
                ", recordedAt=" + recordedAt +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String tradeId;
        private String eventType;
        private String detail;
        private Instant recordedAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder tradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder recordedAt(Instant recordedAt) {
            this.recordedAt = recordedAt;
            return this;
        }

        public ClearingAuditLog build() {
            return new ClearingAuditLog(id, tradeId, eventType, detail, recordedAt);
        }
    }
}
