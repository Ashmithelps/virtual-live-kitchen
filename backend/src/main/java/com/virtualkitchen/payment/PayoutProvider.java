package com.virtualkitchen.payment;

import java.util.UUID;

/**
 * Sends money straight to a third party (a chef) instead of the platform: Razorpay Route linked accounts, and
 * orders whose captured amount is transferred to such an account. Implementations make network calls; never hold
 * database locks while calling them.
 */
public interface PayoutProvider {

    String name();

    /** Creates the linked account, its KYC stakeholder and the Route product with the bank settlement details. */
    LinkedAccount createLinkedAccount(NewLinkedAccount request);

    /** Replaces the settlement bank account of an existing linked account. */
    LinkedAccount updateBankAccount(String accountId, String productId, BankAccount bank);

    /** Current activation state, straight from the gateway. */
    LinkedAccount fetch(String accountId, String productId);

    /**
     * A gateway order for {@code amountPaise} whose {@code transferPaise} is moved to {@code linkedAccountId} as soon
     * as the payment is captured. Checkout and verification then work like any other order.
     */
    PaymentProvider.ProviderOrder createTransferOrder(UUID referenceId, long amountPaise, String linkedAccountId,
                                                      long transferPaise, String receipt, String description);

    /** True when transfers complete with the capture (mock); false when a transfer webhook confirms them later. */
    boolean transfersSettleImmediately();

    /** Gateway-side state of a linked account, mapped to ours. */
    record LinkedAccount(String accountId, String productId, Activation activation, String providerStatus) {
    }

    enum Activation {
        /** Submitted; the gateway is checking KYC / bank details (or needs more information). */
        PENDING,
        ACTIVE
    }

    record BankAccount(String accountNumber, String ifsc, String beneficiaryName) {

        @Override
        public String toString() {
            return "BankAccount[accountNumber=****, ifsc=" + ifsc.substring(0, 4) + "…, beneficiaryName=" + beneficiaryName + "]";
        }
    }

    record Address(String street, String city, String state, String postalCode) {
    }

    /** {@code referenceId}: our id for the account (max 20 chars at Razorpay). */
    record NewLinkedAccount(String referenceId, String legalName, String email, String phone, String pan, Address address,
                            BankAccount bank) {

        @Override
        public String toString() {
            return "NewLinkedAccount[referenceId=" + referenceId + ", legalName=" + legalName + ", pan=****, bank=" + bank + "]";
        }
    }
}
