package com.virtualkitchen.common.jobs;

/** A failure retrying won't fix (bad input, missing data): the job is not retried. */
public class PermanentJobFailure extends RuntimeException {

    public PermanentJobFailure(String message) {
        super(message);
    }
}
