package com.builtbyjuls.clearing.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "clearing_members")
public class ClearingMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_code", nullable = false, unique = true, length = 32)
    private String memberCode;

    @Column(name = "legal_name", nullable = false, length = 128)
    private String legalName;

    @Column(name = "tier", nullable = false, length = 32)
    private String tier;

    @Column(name = "initial_margin_collateral", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialMarginCollateral;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ClearingMember() {
    }

    public ClearingMember(String memberCode, String legalName, String tier, BigDecimal initialMarginCollateral, boolean active) {
        this.memberCode = memberCode;
        this.legalName = legalName;
        this.tier = tier;
        this.initialMarginCollateral = initialMarginCollateral;
        this.active = active;
    }

    public ClearingMember(Long id, String memberCode, String legalName, String tier, BigDecimal initialMarginCollateral, boolean active) {
        this.id = id;
        this.memberCode = memberCode;
        this.legalName = legalName;
        this.tier = tier;
        this.initialMarginCollateral = initialMarginCollateral;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemberCode() {
        return memberCode;
    }

    public void setMemberCode(String memberCode) {
        this.memberCode = memberCode;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public BigDecimal getInitialMarginCollateral() {
        return initialMarginCollateral;
    }

    public void setInitialMarginCollateral(BigDecimal initialMarginCollateral) {
        this.initialMarginCollateral = initialMarginCollateral;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClearingMember that = (ClearingMember) o;
        return Objects.equals(memberCode, that.memberCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberCode);
    }

    @Override
    public String toString() {
        return "ClearingMember{" +
                "id=" + id +
                ", memberCode='" + memberCode + '\'' +
                ", legalName='" + legalName + '\'' +
                ", tier='" + tier + '\'' +
                ", initialMarginCollateral=" + initialMarginCollateral +
                ", active=" + active +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String memberCode;
        private String legalName;
        private String tier;
        private BigDecimal initialMarginCollateral;
        private boolean active = true;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder memberCode(String memberCode) {
            this.memberCode = memberCode;
            return this;
        }

        public Builder legalName(String legalName) {
            this.legalName = legalName;
            return this;
        }

        public Builder tier(String tier) {
            this.tier = tier;
            return this;
        }

        public Builder initialMarginCollateral(BigDecimal initialMarginCollateral) {
            this.initialMarginCollateral = initialMarginCollateral;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public ClearingMember build() {
            return new ClearingMember(id, memberCode, legalName, tier, initialMarginCollateral, active);
        }
    }
}
