package com.virtualkitchen.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process stand-in for the hygiene AI service's POST /analyze. Fetches the video it's given, like the real one. */
public class FakeAiService {

    public record Call(String apiKey, String videoUrl, int videoStatus) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;
    public final List<Call> calls = new CopyOnWriteArrayList<>();
    public volatile int status = 200;
    public volatile String body = response(80.0, 100.0, null, List.of(3.0, 9.0, 15.0));

    public FakeAiService() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/analyze", exchange -> {
            JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
            String url = request.path("videoUrl").asText();
            int videoStatus;
            try {
                videoStatus = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (Exception e) {
                videoStatus = -1;
            }
            calls.add(new Call(exchange.getRequestHeaders().getFirst("X-Api-Key"), url, videoStatus));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** An /analyze response; null percentages = item not evaluated. Violations are attached to gloves. */
    public static String response(Double gloves, Double headCover, Double mask, List<Double> gloveViolations) {
        try {
            return JSON.writeValueAsString(java.util.Map.of(
                    "modelVersion", "fake-ppe.pt@abc123", "durationSeconds", 42.5,
                    "frames", List.of(),
                    "summary", java.util.Map.of("sampledFrames", 43, "personFrames", 40, "items", java.util.Map.of(
                            "gloves", item(gloves, gloveViolations), "headCover", item(headCover, List.of()), "mask", item(mask, List.of())))));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static java.util.Map<String, Object> item(Double pct, List<Double> violations) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("compliancePct", pct);
        m.put("compliantFrames", 0);
        m.put("violationFrames", violations.size());
        m.put("notEvaluatedFrames", 0);
        m.put("violationExamples", violations);
        return m;
    }
}
