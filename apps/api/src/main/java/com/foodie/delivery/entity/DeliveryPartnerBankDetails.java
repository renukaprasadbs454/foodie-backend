package com.foodie.delivery.entity;

import com.foodie.common.entity.BaseEntity;
import com.foodie.common.enums.DocumentVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_partner_bank_details")
public class DeliveryPartnerBankDetails extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_partner_id", nullable = false, unique = true, updatable = false)
    private DeliveryPartner deliveryPartner;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "ifsc_code", nullable = false, length = 20)
    private String ifscCode;

    @Column(name = "bank_name", nullable = false, length = 150)
    private String bankName;

    @Column(name = "branch_name", length = 150)
    private String branchName;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private DocumentVerificationStatus verificationStatus;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    protected DeliveryPartnerBankDetails() {
    }

    public static DeliveryPartnerBankDetails create(
            DeliveryPartner deliveryPartner,
            String accountHolderName,
            String accountNumber,
            String ifscCode,
            String bankName,
            String branchName,
            String accountType
    ) {
        DeliveryPartnerBankDetails details = new DeliveryPartnerBankDetails();
        details.deliveryPartner = deliveryPartner;
        details.accountHolderName = accountHolderName;
        details.accountNumber = accountNumber;
        details.ifscCode = ifscCode != null ? ifscCode.toUpperCase().trim() : null;
        details.bankName = bankName;
        details.branchName = branchName;
        details.accountType = (accountType != null && !accountType.isBlank()) ? accountType.toUpperCase().trim() : "SAVINGS";
        details.verificationStatus = DocumentVerificationStatus.PENDING;
        return details;
    }

    public void updateDetails(
            String accountHolderName,
            String accountNumber,
            String ifscCode,
            String bankName,
            String branchName,
            String accountType
    ) {
        this.accountHolderName = accountHolderName;
        this.accountNumber = accountNumber;
        this.ifscCode = ifscCode != null ? ifscCode.toUpperCase().trim() : null;
        this.bankName = bankName;
        this.branchName = branchName;
        if (accountType != null && !accountType.isBlank()) {
            this.accountType = accountType.toUpperCase().trim();
        }
        // Reset status to PENDING upon update
        this.verificationStatus = DocumentVerificationStatus.PENDING;
        this.verifiedBy = null;
        this.verifiedAt = null;
        this.rejectionReason = null;
    }

    public void verify(UUID adminUserId) {
        this.verificationStatus = DocumentVerificationStatus.VERIFIED;
        this.verifiedBy = adminUserId;
        this.verifiedAt = Instant.now();
        this.rejectionReason = null;
    }

    public void reject(UUID adminUserId, String reason) {
        this.verificationStatus = DocumentVerificationStatus.REJECTED;
        this.verifiedBy = adminUserId;
        this.verifiedAt = Instant.now();
        this.rejectionReason = reason;
    }

    public DeliveryPartner getDeliveryPartner() {
        return deliveryPartner;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getAccountType() {
        return accountType;
    }

    public DocumentVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
