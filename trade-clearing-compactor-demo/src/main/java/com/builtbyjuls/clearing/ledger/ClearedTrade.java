package com.builtbyjuls.clearing.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "cleared_trades")
public class ClearedTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false, unique = true, length = 64)
    private String tradeId;

    @Column(name = "member_code", nullable = false, length = 32)
    private String memberCode;

    @Column(name = "instrument_symbol", nullable = false, length = 32)
    private String instrumentSymbol;

    @Column(name = "asset_class", nullable = false, length = 32)
    private String assetClass;

    @Column(name = "notional_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal notionalAmount;

    @Column(name = "required_margin", nullable = false, precision = 19, scale = 4)
    private BigDecimal requiredMargin;

    @Column(name = "margin_engine_used", nullable = false, length = 32)
    private String marginEngineUsed;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "cleared_at", nullable = false)
    private Instant clearedAt;

    protected ClearedTrade() {
    }

    public ClearedTrade(
            String tradeId,
            String memberCode,
            String instrumentSymbol,
            String assetClass,
            BigDecimal notionalAmount,
            BigDecimal requiredMargin,
            String marginEngineUsed,
            String status,
            LocalDate tradeDate,
            Instant clearedAt) {
        this.tradeId = tradeId;
        this.memberCode = memberCode;
        this.instrumentSymbol = instrumentSymbol;
        this.assetClass = assetClass;
        this.notionalAmount = notionalAmount;
        this.requiredMargin = requiredMargin;
        this.marginEngineUsed = marginEngineUsed;
        this.status = status;
        this.tradeDate = tradeDate;
        this.clearedAt = clearedAt;
    }

    public ClearedTrade(
            Long id,
            String tradeId,
            String memberCode,
            String instrumentSymbol,
            String assetClass,
            BigDecimal notionalAmount,
            BigDecimal requiredMargin,
            String marginEngineUsed,
            String status,
            LocalDate tradeDate,
            Instant clearedAt) {
        this.id = id;
        this.tradeId = tradeId;
        this.memberCode = memberCode;
        this.instrumentSymbol = instrumentSymbol;
        this.assetClass = assetClass;
        this.notionalAmount = notionalAmount;
        this.requiredMargin = requiredMargin;
        this.marginEngineUsed = marginEngineUsed;
        this.status = status;
        this.tradeDate = tradeDate;
        this.clearedAt = clearedAt;
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

    public String getMemberCode() {
        return memberCode;
    }

    public void setMemberCode(String memberCode) {
        this.memberCode = memberCode;
    }

    public String getInstrumentSymbol() {
        return instrumentSymbol;
    }

    public void setInstrumentSymbol(String instrumentSymbol) {
        this.instrumentSymbol = instrumentSymbol;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public BigDecimal getNotionalAmount() {
        return notionalAmount;
    }

    public void setNotionalAmount(BigDecimal notionalAmount) {
        this.notionalAmount = notionalAmount;
    }

    public BigDecimal getRequiredMargin() {
        return requiredMargin;
    }

    public void setRequiredMargin(BigDecimal requiredMargin) {
        this.requiredMargin = requiredMargin;
    }

    public String getMarginEngineUsed() {
        return marginEngineUsed;
    }

    public void setMarginEngineUsed(String marginEngineUsed) {
        this.marginEngineUsed = marginEngineUsed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(Instant clearedAt) {
        this.clearedAt = clearedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClearedTrade that = (ClearedTrade) o;
        return Objects.equals(tradeId, that.tradeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId);
    }

    @Override
    public String toString() {
        return "ClearedTrade{" +
                "id=" + id +
                ", tradeId='" + tradeId + '\'' +
                ", memberCode='" + memberCode + '\'' +
                ", instrumentSymbol='" + instrumentSymbol + '\'' +
                ", assetClass='" + assetClass + '\'' +
                ", notionalAmount=" + notionalAmount +
                ", requiredMargin=" + requiredMargin +
                ", marginEngineUsed='" + marginEngineUsed + '\'' +
                ", status='" + status + '\'' +
                ", tradeDate=" + tradeDate +
                ", clearedAt=" + clearedAt +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String tradeId;
        private String memberCode;
        private String instrumentSymbol;
        private String assetClass;
        private BigDecimal notionalAmount;
        private BigDecimal requiredMargin;
        private String marginEngineUsed;
        private String status;
        private LocalDate tradeDate;
        private Instant clearedAt;

        public Builder id(Long id) {
            this.id = id;
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

        public Builder instrumentSymbol(String instrumentSymbol) {
            this.instrumentSymbol = instrumentSymbol;
            return this;
        }

        public Builder assetClass(String assetClass) {
            this.assetClass = assetClass;
            return this;
        }

        public Builder notionalAmount(BigDecimal notionalAmount) {
            this.notionalAmount = notionalAmount;
            return this;
        }

        public Builder requiredMargin(BigDecimal requiredMargin) {
            this.requiredMargin = requiredMargin;
            return this;
        }

        public Builder marginEngineUsed(String marginEngineUsed) {
            this.marginEngineUsed = marginEngineUsed;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder tradeDate(LocalDate tradeDate) {
            this.tradeDate = tradeDate;
            return this;
        }

        public Builder clearedAt(Instant clearedAt) {
            this.clearedAt = clearedAt;
            return this;
        }

        public ClearedTrade build() {
            return new ClearedTrade(
                    id,
                    tradeId,
                    memberCode,
                    instrumentSymbol,
                    assetClass,
                    notionalAmount,
                    requiredMargin,
                    marginEngineUsed,
                    status,
                    tradeDate,
                    clearedAt
            );
        }
    }
}
