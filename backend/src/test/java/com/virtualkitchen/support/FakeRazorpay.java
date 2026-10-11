package com.virtualkitchen.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process stand-in for the Razorpay REST API (orders, payments, capture, refunds) so the real
 * RazorpayPaymentProvider can be exercised over HTTP without network access or real keys.
 */
public class FakeRazorpay {

    public static final String KEY_ID = "rzp_test_fake";
    public static final String KEY_SECRET = "test_key_secret";
    public static final String WEBHOOK_SECRET = "test_webhook_secret";

    public record Call(String method, String path, JsonNode body) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PAYMENT = Pattern.compile("^/v1/payments/([^/]+)(/capture|/refund|/refunds)?$");

    private final HttpServer server;
    private final AtomicInteger ids = new AtomicInteger();
    public final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> refunds = new ConcurrentHashMap<>();
    /** 502 on refund creation. */
    public volatile boolean refundsFail;
    /** Creates the refund but answers 504, like a timeout after the gateway acted. */
    public volatile boolean refundResponsesLost;

    public FakeRazorpay() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A payment as Razorpay would hold it after the customer completed checkout. */
    public void addPayment(String paymentId, String orderId, long amount, String status) {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("id", paymentId);
        payment.put("entity", "payment");
        payment.put("order_id", orderId);
        payment.put("amount", amount);
        payment.put("currency", "INR");
        payment.put("status", status);
        payments.put(paymentId, payment);
    }

    public List<Call> calls(String method, String pathPrefix) {
        return calls.stream().filter(c -> c.method().equals(method) && c.path().startsWith(pathPrefix)).toList();
    }

    public static String sign(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        byte[] raw = exchange.getRequestBody().readAllBytes();
        JsonNode body = raw.length == 0 ? null : JSON.readTree(raw);
        calls.add(new Call(method, path, body));

        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString((KEY_ID + ":" + KEY_SECRET).getBytes(StandardCharsets.UTF_8));
        if (!expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, Map.of("error", Map.of("code", "BAD_REQUEST_ERROR", "description", "Authentication failed")));
            return;
        }

        if (method.equals("POST") && path.equals("/v1/orders")) {
            Map<String, Object> order = Map.of("id", "order_fake" + ids.incrementAndGet(), "entity", "order",
                    "amount", body.path("amount").asLong(), "currency", body.path("currency").asText(), "status", "created",
                    "receipt", body.path("receipt").asText());
            respond(exchange, 200, order);
            return;
        }
        Matcher m = PAYMENT.matcher(path);
        if (m.matches()) {
            String paymentId = m.group(1);
            String action = m.group(2);
            Map<String, Object> payment = payments.get(paymentId);
            if (payment == null) {
                respond(exchange, 400, Map.of("error", Map.of("code", "BAD_REQUEST_ERROR", "description", "The id provided does not exist")));
                return;
            }
            if (action == null && method.equals("GET")) {
                respond(exchange, 200, payment);
            } else if ("/capture".equals(action)) {
                payment.put("status", "captured");
                respond(exchange, 200, payment);
            } else if ("/refund".equals(action)) {
                if (refundsFail) {
                    respond(exchange, 502, Map.of("error", Map.of("code", "SERVER_ERROR", "description", "Try again")));
                    return;
                }
                Map<String, Object> refund = new LinkedHashMap<>(Map.of("id", "rfnd_fake" + ids.incrementAndGet(), "entity", "refund",
                        "payment_id", paymentId, "amount", body.path("amount").asLong(), "status", "pending"));
                refunds.computeIfAbsent(paymentId, k -> new ArrayList<>()).add(refund);
                respond(exchange, refundResponsesLost ? 504 : 200, refund);
            } else if ("/refunds".equals(action)) {
                respond(exchange, 200, Map.of("entity", "collection", "items", refunds.getOrDefault(paymentId, List.of())));
            } else {
                respond(exchange, 404, Map.of());
            }
            return;
        }
        respond(exchange, 404, Map.of());
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
