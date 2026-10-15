package com.virtualkitchen.common.jobs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.jobs")
public record JobProperties(
        @Min(0) int workerThreads,
        @NotNull Duration pollInterval,
        @NotNull Duration lease,
        @Min(1) int maxAttempts,
        @NotNull Duration retryBaseDelay,
        /**
         * Extra lanes: lane name → threads. A handler in a lane (see {@link JobHandler#lane()}) runs only on that lane's
         * threads, so slow work (e.g. AI analysis) can't hold up the default lane (clip rendering). Lanes not listed
         * get one thread when {@code workerThreads > 0}, none otherwise.
         */
        Map<String, Integer> lanes) {

    public JobProperties {
        lanes = lanes == null ? Map.of() : Map.copyOf(lanes);
    }

    int threadsFor(String lane) {
        if (JobHandler.DEFAULT_LANE.equals(lane)) return workerThreads;
        return lanes.getOrDefault(lane, workerThreads > 0 ? 1 : 0);
    }
}
