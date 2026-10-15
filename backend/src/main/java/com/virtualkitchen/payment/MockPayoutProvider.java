package com.virtualkitchen.payment;

import com.virtualkitchen.common.crypto.SecureTokens;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Development stand-in for Razorpay Route. New linked accounts start "under_review" and are "activated" the next
 * time their status is fetched (the chef app's "Check status"); transfers settle instantly. Moves no money.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock")
public class MockPayoutProvider implements PayoutProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public LinkedAccount createLinkedAccount(NewLinkedAccount request) {
        return new LinkedAccount("acc_mock_" + SecureTokens.random(9), "acc_prd_mock_" + SecureTokens.random(9),
                Activation.PENDING, "under_review");
    }

    @Override
    public LinkedAccount updateBankAccount(String accountId, String productId, BankAccount bank) {
        return new LinkedAccount(accountId, productId, Activation.PENDING, "under_review");
    }

    @Override
    public LinkedAccount fetch(String accountId, String productId) {
        return new LinkedAccount(accountId, productId, Activation.ACTIVE, "activated");
    }

    @Override
    public PaymentProvider.ProviderOrder createTransferOrder(UUID referenceId, long amountPaise, String linkedAccountId,
                                                            long transferPaise, String receipt, String description) {
        return new PaymentProvider.ProviderOrder("mock_order_" + SecureTokens.random(12));
    }

    @Override
    public boolean transfersSettleImmediately() {
        return true;
    }
}
