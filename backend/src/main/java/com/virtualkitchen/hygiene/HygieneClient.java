package com.virtualkitchen.hygiene;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.virtualkitchen.common.jobs.PermanentJobFailure;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Calls the AI service's POST /analyze. Only the summary is kept; per-frame detections aren't stored. */
@Component
class HygieneClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(Double compliancePct, List<Double> violationExamples) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summary(int sampledFrames, int personFrames, Map<String, Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Analysis(String modelVersion, double durationSeconds, Summary summary) {
    }

    private final RestClient http;
    private final HygieneProperties properties;

    HygieneClient(HygieneProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(properties.requestTimeout());
        this.http = RestClient.builder().baseUrl(properties.serviceUrl().toString()).requestFactory(factory).build();
    }

    /**
     * @throws PermanentJobFailure when the service says the video can't be analysed (4xx other than auth): retrying
     *                             won't help. Anything else (timeouts, 5xx, no model yet) is thrown as retryable.
     */
    Analysis analyze(String videoUrl) {
        try {
            Analysis analysis = http.post().uri("/analyze").contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (properties.apiKey() != null && !properties.apiKey().isBlank()) h.set("X-Api-Key", properties.apiKey());
                    })
                    .body(Map.of("videoUrl", videoUrl))
                    .retrieve().body(Analysis.class);
            if (analysis == null || analysis.summary() == null || analysis.modelVersion() == null) {
                throw new IllegalStateException("AI service returned an empty analysis");
            }
            return analysis;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            String detail = "AI service answered " + status.value() + ": " + abbreviate(e.getResponseBodyAsString());
            if (status.is4xxClientError() && status.value() != 401 && status.value() != 408 && status.value() != 429) {
                throw new PermanentJobFailure(detail);
            }
            throw new IllegalStateException(detail);
        }
    }

    private static String abbreviate(String body) {
        return body == null ? "" : body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }
}
