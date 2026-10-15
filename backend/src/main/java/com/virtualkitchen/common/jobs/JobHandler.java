package com.virtualkitchen.common.jobs;

import com.fasterxml.jackson.databind.JsonNode;

/** Processes one job type. Handlers must be idempotent: a job can run again after a crash or timeout. */
public interface JobHandler {

    String DEFAULT_LANE = "default";

    String type();

    /**
     * Throw to fail the attempt (it's retried with backoff); throw {@link PermanentJobFailure}
     * when retrying can't help.
     */
    void handle(JsonNode payload) throws Exception;

    /** Which worker threads run this type (see {@code app.jobs.lanes}). */
    default String lane() {
        return DEFAULT_LANE;
    }

    /** Called once when the job gives up (permanent failure or attempts exhausted). */
    default void onExhausted(JsonNode payload, String lastError) {
    }
}
