package com.virtualkitchen.payment.razorpay;

import com.virtualkitchen.payment.PaymentProvider;
import com.virtualkitchen.payment.PaymentProviderException;
import com.virtualkitchen.payment.PayoutProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Razorpay Route: chefs' linked accounts (Accounts v2 → Stakeholders → Route product with settlement bank details)
 * and tip orders that transfer the chef's share to that account on capture. In test mode, linked accounts are
 * activated from the Razorpay Dashboard (Route → Accounts).
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayPayoutProvider implements PayoutProvider {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPayoutProvider.class);

    private final RazorpayClient client;
    private final RazorpayRouteProperties route;

    public RazorpayPayoutProvider(RazorpayProperties properties, RazorpayRouteProperties route) {
        this.client = new RazorpayClient(properties);
        this.route = route;
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public LinkedAccount createLinkedAccount(NewLinkedAccount request) {
        Map<String, Object> registered = new LinkedHashMap<>();
        registered.put("street1", request.address().street());
        registered.put("city", request.address().city());
        registered.put("state", request.address().state().toUpperCase());
        registered.put("postal_code", request.address().postalCode());
        registered.put("country", "IN");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", request.email());
        body.put("phone", localPhone(request.phone()));
        body.put("type", "route");
        body.put("reference_id", request.referenceId());
        body.put("legal_business_name", request.legalName());
        body.put("business_type", "individual");
        body.put("contact_name", request.legalName());
        body.put("profile", Map.of("category", route.businessCategory(), "subcategory", route.businessSubcategory(),
                "addresses", Map.of("registered", registered)));
        body.put("legal_info", Map.of("pan", request.pan()));
        RazorpayClient.RzpAccount account = client.createAccount(body);
        try {
            client.createStakeholder(account.id(), Map.of("name", request.legalName(), "email", request.email(),
                    "kyc", Map.of("pan", request.pan())));
            RazorpayClient.RzpProduct product = client.requestRouteProduct(account.id());
            return toLinked(account.id(), client.updateProduct(account.id(), product.id(), settlements(request.bank())));
        } catch (PaymentProviderException e) {
            // The account exists at Razorpay but isn't usable; a resubmission creates a fresh one.
            log.warn("Razorpay linked account {} was created but its setup didn't finish: {}", account.id(), e.getMessage());
            throw e;
        }
    }

    @Override
    public LinkedAccount updateBankAccount(String accountId, String productId, BankAccount bank) {
        return toLinked(accountId, client.updateProduct(accountId, productId, settlements(bank)));
    }

    @Override
    public LinkedAccount fetch(String accountId, String productId) {
        return toLinked(accountId, client.fetchProduct(accountId, productId));
    }

    @Override
    public PaymentProvider.ProviderOrder createTransferOrder(UUID referenceId, long amountPaise, String linkedAccountId,
                                                            long transferPaise, String receipt, String description) {
        RazorpayClient.RzpOrder order = client.createTransferOrder(amountPaise, receipt,
                Map.of("tip_id", referenceId.toString(), "purpose", description), linkedAccountId, transferPaise);
        if (order.amount() != amountPaise) {
            throw new PaymentProviderException("Payment provider created an order for the wrong amount");
        }
        return new PaymentProvider.ProviderOrder(order.id());
    }

    @Override
    public boolean transfersSettleImmediately() {
        return false;
    }

    private static Map<String, Object> settlements(BankAccount bank) {
        return Map.of("settlements", Map.of("account_number", bank.accountNumber(), "ifsc_code", bank.ifsc(),
                "beneficiary_name", bank.beneficiaryName()), "tnc_accepted", true);
    }

    private static LinkedAccount toLinked(String accountId, RazorpayClient.RzpProduct product) {
        String status = product.activationStatus() == null ? "unknown" : product.activationStatus();
        return new LinkedAccount(accountId, product.id(), "activated".equals(status) ? Activation.ACTIVE : Activation.PENDING, status);
    }

    /** Razorpay wants a 10-digit Indian number without the country code. */
    private static String localPhone(String e164) {
        return e164 != null && e164.startsWith("+91") ? e164.substring(3) : e164;
    }
}
