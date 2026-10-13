package com.virtualkitchen.common.jobs;

import com.fasterxml.jackson.databind.JsonNode;

/** Processes one job type. Handlers must be idempotent: a job can run again after a crash or timeout. */
public interface JobHandler {

    String type();

    /**
     * Throw to fail the attempt (it's retried with backoff); throw {@link PermanentJobFailure}
     * when retrying can't help.
     */
    void handle(JsonNode payload) throws Exception;

    /** Called once when the job gives up (permanent failure or attempts exhausted). */
    default void onExhausted(JsonNode payload, String lastError) {
    }
}
