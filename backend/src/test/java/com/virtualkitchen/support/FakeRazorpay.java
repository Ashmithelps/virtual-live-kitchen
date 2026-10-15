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
    private static final Pattern ACCOUNT = Pattern.compile("^/v2/accounts/([^/]+)/(stakeholders|products)(?:/([^/]+))?$");

    private final HttpServer server;
    private final AtomicInteger ids = new AtomicInteger();
    public final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> refunds = new ConcurrentHashMap<>();
    /** Route products by id → activation_status ("under_review" until {@link #activate}). */
    private final Map<String, String> productStatus = new ConcurrentHashMap<>();
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

    /** What the Razorpay Dashboard does when a linked account passes review. */
    public void activate(String productId) {
        productStatus.put(productId, "activated");
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
        if (method.equals("POST") && path.equals("/v1/plans")) {
            respond(exchange, 200, Map.of("id", "plan_fake" + ids.incrementAndGet(), "entity", "plan", "period", body.path("period").asText(),
                    "item", Map.of("amount", body.path("item").path("amount").asLong())));
            return;
        }
        if (method.equals("POST") && path.equals("/v1/subscriptions")) {
            String id = "sub_fake" + ids.incrementAndGet();
            respond(exchange, 200, Map.of("id", id, "entity", "subscription", "status", "created", "short_url", "https://rzp.io/i/" + id,
                    "plan_id", body.path("plan_id").asText()));
            return;
        }
        if (path.startsWith("/v1/subscriptions/")) {
            String id = path.substring("/v1/subscriptions/".length()).replace("/cancel", "");
            respond(exchange, 200, Map.of("id", id, "entity", "subscription", "status", path.endsWith("/cancel") ? "cancelled" : "active"));
            return;
        }
        if (method.equals("POST") && path.equals("/v2/accounts")) {
            respond(exchange, 200, Map.of("id", "acc_fake" + ids.incrementAndGet(), "type", "route", "status", "created"));
            return;
        }
        Matcher a = ACCOUNT.matcher(path);
        if (a.matches()) {
            String accountId = a.group(1);
            if (a.group(2).equals("stakeholders") && method.equals("POST")) {
                respond(exchange, 200, Map.of("id", "sth_fake" + ids.incrementAndGet()));
            } else if (a.group(3) == null && method.equals("POST")) {
                String productId = "acc_prd_fake" + ids.incrementAndGet();
                productStatus.put(productId, "requested");
                respond(exchange, 200, Map.of("id", productId, "account_id", accountId, "activation_status", "requested"));
            } else if (a.group(3) != null && productStatus.containsKey(a.group(3))) {
                String productId = a.group(3);
                if (method.equals("PATCH") && !"activated".equals(productStatus.get(productId))) {
                    productStatus.put(productId, "under_review");
                }
                respond(exchange, 200, Map.of("id", productId, "account_id", accountId, "activation_status", productStatus.get(productId)));
            } else {
                respond(exchange, 404, Map.of());
            }
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
