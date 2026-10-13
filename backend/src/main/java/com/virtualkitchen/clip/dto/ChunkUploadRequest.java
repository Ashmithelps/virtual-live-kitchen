package com.virtualkitchen.clip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Chunks the phone holds for the window; it gets one presigned PUT per chunk back. */
public record ChunkUploadRequest(@NotEmpty @Size(max = 90) List<@NotNull @Valid Chunk> chunks) {

    /** Times are server time (the phone corrects its clock). */
    public record Chunk(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String chunkId,
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @NotBlank String contentType,
            @NotNull @Positive Long sizeBytes) {
    }
}
