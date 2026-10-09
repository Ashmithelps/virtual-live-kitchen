package com.virtualkitchen.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Instant;
import java.util.Map;

/**
 * Presigned access to the private media bucket. The backend never proxies image bytes: clients
 * upload with a short-lived PUT URL and read through short-lived GET URLs. Only object keys are
 * stored in the database.
 */
@Service
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    private final StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public ObjectStorage(StorageProperties properties, S3Client s3Client, S3Presigner presigner) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

    /** The client must send exactly the returned headers (Content-Type is part of the signature). */
    public PresignedUpload presignUpload(String key, String contentType) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(b -> b
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(properties.mediaBucket())
                        .key(key)
                        .contentType(contentType)
                        .build()));
        return new PresignedUpload(presigned.url().toString(), presigned.expiration(),
                Map.of("Content-Type", contentType));
    }

    /** @return a temporary download URL, or {@code null} when {@code key} is null */
    public String downloadUrl(String key) {
        if (key == null) {
            return null;
        }
        return presigner.presignGetObject(b -> b
                        .signatureDuration(properties.downloadUrlTtl())
                        .getObjectRequest(GetObjectRequest.builder().bucket(properties.mediaBucket()).key(key).build()))
                .url().toString();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createBucketsIfMissing() {
        if (!properties.createBuckets()) {
            return;
        }
        String bucket = properties.mediaBucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created media bucket '{}'", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created media bucket '{}'", bucket);
        }
    }

    public record PresignedUpload(String url, Instant expiresAt, Map<String, String> headers) {
    }
}
