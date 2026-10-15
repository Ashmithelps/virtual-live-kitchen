package com.virtualkitchen.tip;

import com.virtualkitchen.auth.user.UserAccountService;
import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.payment.PayoutProvider;
import com.virtualkitchen.payment.PayoutProvider.LinkedAccount;
import com.virtualkitchen.tip.dto.PayoutAccountRequest;
import com.virtualkitchen.tip.dto.PayoutAccountResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Chef payout onboarding through the gateway (Razorpay Route linked accounts). Gateway calls run outside database
 * transactions; bank account, IFSC and PAN are passed through, never stored or logged.
 */
@Service
public class PayoutAccountService {

    private static final Logger log = LoggerFactory.getLogger(PayoutAccountService.class);

    private final ChefPayoutAccountRepository accounts;
    private final ChefRepository chefs;
    private final UserAccountService users;
    private final PayoutProvider provider;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PayoutAccountService(ChefPayoutAccountRepository accounts, ChefRepository chefs, UserAccountService users,
                                PayoutProvider provider, PlatformTransactionManager transactions, Clock clock) {
        this.accounts = accounts;
        this.chefs = chefs;
        this.users = users;
        this.provider = provider;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    public PayoutAccountResponse get(UUID chefUserId) {
        Chef chef = chef(chefUserId);
        return tx.execute(s -> toResponse(accounts.findById(chef.getId())));
    }

    /**
     * First time: creates the linked account (PENDING_KYC until the gateway activates it). Afterwards: replaces the
     * bank account; the legal name and PAN can't change (they identify the account at the gateway).
     */
    public PayoutAccountResponse submit(UUID chefUserId, PayoutAccountRequest request) {
        Chef chef = chef(chefUserId);
        PayoutProvider.BankAccount bank = new PayoutProvider.BankAccount(request.bankAccountNumber(), request.ifsc(), request.legalName());
        Optional<ChefPayoutAccount> existing = tx.execute(s -> accounts.findById(chef.getId()));
        if (existing != null && existing.isPresent()) {
            ChefPayoutAccount account = existing.get();
            if (!account.getLegalName().equalsIgnoreCase(request.legalName().strip()) || !request.pan().endsWith(account.getPanLast4())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "The legal name and PAN can't be changed after set-up. Contact support to change them.");
            }
            LinkedAccount linked = provider.updateBankAccount(account.getProviderAccountId(), account.getProviderProductId(), bank);
            return tx.execute(s -> {
                ChefPayoutAccount fresh = accounts.findById(chef.getId()).orElseThrow();
                fresh.bank(request.bankAccountNumber(), request.ifsc(), clock.instant());
                fresh.sync(linked.productId(), linked.activation() == PayoutProvider.Activation.ACTIVE, linked.providerStatus(), clock.instant());
                return toResponse(Optional.of(fresh));
            });
        }

        String phone = users.getById(chefUserId).getPhone();
        LinkedAccount linked = provider.createLinkedAccount(new PayoutProvider.NewLinkedAccount(
                SecureTokens.random(12), request.legalName().strip(), request.email().strip(), phone, request.pan(),
                new PayoutProvider.Address(request.street().strip(), request.city().strip(), request.state().strip(), request.postalCode()),
                bank));
        log.info("Chef {} payout account {} created ({})", chef.getId(), linked.accountId(), linked.providerStatus());
        return tx.execute(s -> {
            ChefPayoutAccount account = new ChefPayoutAccount(chef.getId(), provider.name(), linked.accountId(), request.legalName().strip(),
                    request.bankAccountNumber(), request.ifsc(), request.pan(), clock.instant());
            account.sync(linked.productId(), linked.activation() == PayoutProvider.Activation.ACTIVE, linked.providerStatus(), clock.instant());
            return toResponse(Optional.of(accounts.save(account)));
        });
    }

    /** Asks the gateway for the current state (for when webhooks are slow or not configured). */
    public PayoutAccountResponse refresh(UUID chefUserId) {
        Chef chef = chef(chefUserId);
        ChefPayoutAccount account = tx.execute(s -> accounts.findById(chef.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Set up payouts first"));
        sync(account.getProviderAccountId(), account.getProviderProductId());
        return get(chefUserId);
    }

    /** Webhook (or refresh): re-reads one linked account from the gateway; false if it isn't ours. */
    public boolean sync(String accountId, String productId) {
        Optional<ChefPayoutAccount> known = tx.execute(s -> accountId != null ? accounts.findByProviderAccountId(accountId)
                : accounts.findByProviderProductId(productId));
        if (known == null || known.isEmpty()) {
            return false;
        }
        ChefPayoutAccount account = known.get();
        LinkedAccount linked = provider.fetch(account.getProviderAccountId(), account.getProviderProductId());
        tx.executeWithoutResult(s -> accounts.findById(account.getChefId()).ifPresent(a ->
                a.sync(linked.productId(), linked.activation() == PayoutProvider.Activation.ACTIVE, linked.providerStatus(), clock.instant())));
        return true;
    }

    /**
     * Webhook: a linked account's Route product changed state (activated, under_review, needs_clarification…).
     * Applied from the signed event, without calling the gateway. False if the account isn't ours.
     */
    public boolean applyActivation(String accountId, String productId, String providerStatus) {
        Boolean known = tx.execute(s -> {
            Optional<ChefPayoutAccount> found = Optional.<ChefPayoutAccount>empty();
            if (productId != null) found = accounts.findByProviderProductId(productId);
            if (found.isEmpty() && accountId != null) {
                found = accounts.findByProviderAccountId(accountId.startsWith("acc_") ? accountId : "acc_" + accountId);
            }
            found.ifPresent(a -> a.sync(productId != null ? productId : a.getProviderProductId(), "activated".equals(providerStatus),
                    providerStatus, clock.instant()));
            return found.isPresent();
        });
        return Boolean.TRUE.equals(known);
    }

    private Chef chef(UUID chefUserId) {
        return chefs.findByUserIdAndRemovedAtIsNull(chefUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));
    }

    static PayoutAccountResponse toResponse(Optional<ChefPayoutAccount> account) {
        return account.map(a -> new PayoutAccountResponse(a.getStatus(), a.getLegalName(), a.getBankCode() + " ••••" + a.getBankAccountLast4(),
                        "••••••" + a.getPanLast4(), a.getProviderStatus(), a.getUpdatedAt(), a.getActivatedAt()))
                .orElse(new PayoutAccountResponse(PayoutStatus.NOT_SET_UP, null, null, null, null, null, null));
    }
}
