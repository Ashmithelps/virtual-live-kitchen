package com.virtualkitchen.clip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Keys of the chunks the phone uploaded. Empty = the phone had no footage for the window. */
public record ChunksUploadedRequest(@NotNull @Size(max = 90) List<@NotBlank @Size(max = 300) String> objectKeys) {
}
