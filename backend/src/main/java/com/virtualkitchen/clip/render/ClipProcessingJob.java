package com.virtualkitchen.clip.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.virtualkitchen.clip.ClipLifecycle;
import com.virtualkitchen.clip.ClipProperties;
import com.virtualkitchen.common.jobs.JobHandler;
import com.virtualkitchen.common.jobs.PermanentJobFailure;
import com.virtualkitchen.common.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * CLIP_PROCESS job: raw chunks → stamped MP4 + thumbnail in storage. Idempotent: outputs are
 * written to fixed keys and the clip row is only advanced if it isn't already READY.
 */
@Component
public class ClipProcessingJob implements JobHandler {

    public static final String TYPE = "CLIP_PROCESS";
    private static final Logger log = LoggerFactory.getLogger(ClipProcessingJob.class);

    private final ClipLifecycle lifecycle;
    private final ObjectStorage storage;
    private final ClipRenderer renderer;
    private final ClipOverlayRenderer overlay;
    private final ClipProperties properties;

    public ClipProcessingJob(ClipLifecycle lifecycle, ObjectStorage storage, ClipRenderer renderer, ClipOverlayRenderer overlay,
                             ClipProperties properties) {
        this.lifecycle = lifecycle;
        this.storage = storage;
        this.renderer = renderer;
        this.overlay = overlay;
        this.properties = properties;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JsonNode payload) throws Exception {
        UUID orderId = UUID.fromString(payload.path("orderId").asText());
        ClipLifecycle.Work work = lifecycle.startProcessing(orderId).orElse(null);
        if (work == null) {
            log.info("Clip for order {} needs no processing", orderId);
            return;
        }
        Path dir = Files.createTempDirectory("clip-" + orderId + "-");
        try {
            List<Path> chunks = new ArrayList<>();
            for (int i = 0; i < work.chunks().size(); i++) {
                ClipLifecycle.Chunk chunk = work.chunks().get(i);
                Path file = dir.resolve(String.format("chunk-%03d.%s", i, chunk.extension()));
                storage.downloadClip(chunk.key(), file);
                chunks.add(file);
            }
            Path overlayPng = dir.resolve("overlay.png");
            overlay.render(new ClipOverlayRenderer.Overlay(work.restaurantName(), logo(work.logoKey(), dir), work.displayNumber(),
                    work.chefName(), work.date(), properties.watermark()), overlayPng);

            ClipRenderer.Output output = renderer.render(new ClipRenderer.Input(chunks, work.offsetS(), work.windowS(), overlayPng, dir));

            String base = "clips/" + work.restaurantId() + "/" + orderId;
            storage.uploadClip(base + ".mp4", output.video(), "video/mp4");
            storage.uploadClip(base + ".jpg", output.thumbnail(), "image/jpeg");
            lifecycle.finish(orderId, base + ".mp4", base + ".jpg", output.durationS());
            log.info("Clip for order {} ready: {} s", orderId, String.format("%.1f", output.durationS()));

            // Raw footage is no longer needed (best effort; the retention job catches leftovers).
            try {
                storage.deleteClips(work.chunks().stream().map(ClipLifecycle.Chunk::key).toList());
                lifecycle.rawDeleted(orderId);
            } catch (RuntimeException e) {
                log.warn("Couldn't delete raw footage for order {}: {}", orderId, e.getMessage());
            }
        } catch (PermanentJobFailure e) {
            if (e.getMessage() != null && e.getMessage().startsWith("NO_FOOTAGE")) {
                lifecycle.noFootage(orderId);
                return; // nothing to retry; the order simply has no clip
            }
            throw e;
        } finally {
            deleteRecursively(dir);
        }
    }

    @Override
    public void onExhausted(JsonNode payload, String lastError) {
        lifecycle.failed(UUID.fromString(payload.path("orderId").asText()), lastError);
    }

    private BufferedImage logo(String logoKey, Path dir) {
        if (logoKey == null) {
            return null;
        }
        try {
            Path raw = dir.resolve("logo-source");
            storage.downloadMedia(logoKey, raw);
            return ImageIO.read(renderer.normaliseLogo(raw, dir).toFile());
        } catch (RuntimeException | IOException e) {
            log.warn("Logo {} unusable, rendering without it: {}", logoKey, e.getMessage());
            return null;
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.warn("Couldn't clean up {}: {}", dir, e.getMessage());
        }
    }
}
