package com.virtualkitchen.payment.razorpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.virtualkitchen.payment.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
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
