package com.virtualkitchen.clip;

import java.util.UUID;

/** Published in the transaction that marks a clip READY (listeners that do more work should run after commit). */
public record ClipReadyEvent(UUID clipId) {
}
