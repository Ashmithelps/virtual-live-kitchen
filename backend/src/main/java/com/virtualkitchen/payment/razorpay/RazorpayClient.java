package com.virtualkitchen.payment.razorpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.virtualkitchen.payment.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Thin client for the Razorpay REST API (Basic auth with key id/secret). */
class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);

    private final RestClient http;

    RazorpayClient(RazorpayProperties properties) {
        // java.net.http client: supports PATCH (Route settlement updates), unlike HttpURLConnection.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .baseUrl(properties.apiBaseUrl().toString())
                .requestFactory(factory)
                .defaultHeaders(h -> h.setBasicAuth(properties.keyId(), properties.keySecret()))
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpOrder(String id, long amount, String currency, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpPayment(String id, @JsonProperty("order_id") String orderId, long amount, String currency, String status,
                      @JsonProperty("error_description") String errorDescription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpRefund(String id, @JsonProperty("payment_id") String paymentId, long amount, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpCollection<T>(List<T> items) {
    }

    RzpOrder createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        return call("create order", () -> http.post().uri("/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes))
                .retrieve().body(RzpOrder.class));
    }

    RzpPayment fetchPayment(String paymentId) {
        return call("fetch payment", () -> http.get().uri("/v1/payments/{id}", paymentId).retrieve().body(RzpPayment.class));
    }

    RzpPayment capture(String paymentId, long amountPaise) {
        return call("capture payment", () -> http.post().uri("/v1/payments/{id}/capture", paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "currency", "INR"))
                .retrieve().body(RzpPayment.class));
    }

    RzpRefund refund(String paymentId, long amountPaise, String receipt) {
        return call("refund payment", () -> http.post().uri("/v1/payments/{id}/refund", paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "speed", "normal", "receipt", receipt))
                .retrieve().body(RzpRefund.class));
    }

    List<RzpRefund> refunds(String paymentId) {
        RzpCollection<RzpRefund> page = call("list refunds", () -> http.get().uri("/v1/payments/{id}/refunds", paymentId)
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<RzpCollection<RzpRefund>>() { }));
        return page == null || page.items() == null ? List.of() : page.items();
    }

    // ── Route (linked accounts, transfers) ───────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpAccount(String id, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpProduct(String id, @JsonProperty("activation_status") String activationStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpStakeholder(String id) {
    }

    RzpAccount createAccount(Map<String, Object> body) {
        return call("create linked account", () -> http.post().uri("/v2/accounts").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(RzpAccount.class));
    }

    RzpStakeholder createStakeholder(String accountId, Map<String, Object> body) {
        return call("create stakeholder", () -> http.post().uri("/v2/accounts/{id}/stakeholders", accountId)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(RzpStakeholder.class));
    }

    RzpProduct requestRouteProduct(String accountId) {
        return call("request route product", () -> http.post().uri("/v2/accounts/{id}/products", accountId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("product_name", "route", "tnc_accepted", true))
                .retrieve().body(RzpProduct.class));
    }

    RzpProduct updateProduct(String accountId, String productId, Map<String, Object> body) {
        return call("update route product", () -> http.patch().uri("/v2/accounts/{id}/products/{pid}", accountId, productId)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(RzpProduct.class));
    }

    RzpProduct fetchProduct(String accountId, String productId) {
        return call("fetch route product", () -> http.get().uri("/v2/accounts/{id}/products/{pid}", accountId, productId)
                .retrieve().body(RzpProduct.class));
    }

    /** An order whose capture triggers a transfer to {@code linkedAccountId} (Route "transfers via orders"). */
    RzpOrder createTransferOrder(long amountPaise, String receipt, Map<String, String> notes, String linkedAccountId, long transferPaise) {
        Map<String, Object> transfer = Map.of("account", linkedAccountId, "amount", transferPaise, "currency", "INR",
                "notes", notes, "on_hold", false);
        return call("create transfer order", () -> http.post().uri("/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes, "transfers", List.of(transfer)))
                .retrieve().body(RzpOrder.class));
    }

    // ── Subscriptions ─────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpPlan(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RzpSubscription(String id, String status, @JsonProperty("short_url") String shortUrl) {
    }

    RzpPlan createPlan(String name, long amountPaise, String description) {
        return call("create plan", () -> http.post().uri("/v1/plans").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("period", "monthly", "interval", 1,
                        "item", Map.of("name", name, "amount", amountPaise, "currency", "INR", "description", description)))
                .retrieve().body(RzpPlan.class));
    }

    RzpSubscription createSubscription(Map<String, Object> body) {
        return call("create subscription", () -> http.post().uri("/v1/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(RzpSubscription.class));
    }

    RzpSubscription updateSubscription(String id, Map<String, Object> body) {
        return call("update subscription", () -> http.patch().uri("/v1/subscriptions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(RzpSubscription.class));
    }

    RzpSubscription cancelSubscription(String id, boolean atCycleEnd) {
        return call("cancel subscription", () -> http.post().uri("/v1/subscriptions/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("cancel_at_cycle_end", atCycleEnd ? 1 : 0)).retrieve().body(RzpSubscription.class));
    }

    private static <T> T call(String what, Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) {
                throw new PaymentProviderException("Payment provider returned an empty response");
            }
            return result;
        } catch (RestClientResponseException e) {
            // Razorpay error bodies describe the problem (never contain our secret).
            log.warn("Razorpay {} failed: HTTP {} {}", what, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new PaymentProviderException("Payment provider rejected the request (" + what + ")");
        } catch (RestClientException e) {
            log.warn("Razorpay {} failed: {}", what, e.getMessage());
            throw new PaymentProviderException("Payment provider is unavailable; please try again");
        }
    }
}
