package com.virtualkitchen.clip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClipShareRepository extends JpaRepository<ClipShare, UUID> {

    Optional<ClipShare> findByClipIdAndRevokedAtIsNull(UUID clipId);

    Optional<ClipShare> findByTokenAndRevokedAtIsNull(String token);
}
