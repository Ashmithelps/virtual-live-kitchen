package com.virtualkitchen.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Arrays;
import java.util.List;
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
    private final List<String> webOrigins;

    public ObjectStorage(StorageProperties properties, S3Client s3Client, S3Presigner presigner,
                         @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.webOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::strip).filter(o -> !o.isEmpty()).toList();
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

    /**
     * Upload URL for raw kitchen footage in the private clips bucket. The exact size is part of
     * the signature, so the URL can't be used to upload anything bigger (browsers send
     * Content-Length automatically).
     */
    public PresignedUpload presignClipUpload(String key, String contentType, long contentLength) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(b -> b
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(properties.clipsBucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build()));
        return new PresignedUpload(presigned.url().toString(), presigned.expiration(), Map.of("Content-Type", contentType));
    }

    /** @return true if the object exists in the clips bucket */
    public boolean clipExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(properties.clipsBucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /** Downloads an object from the clips bucket (raw footage) to a local file. */
    public void downloadClip(String key, Path target) {
        s3Client.getObject(GetObjectRequest.builder().bucket(properties.clipsBucket()).key(key).build(), target);
    }

    /** Downloads an object from the media bucket (e.g. a restaurant logo) to a local file. */
    public void downloadMedia(String key, Path target) {
        s3Client.getObject(GetObjectRequest.builder().bucket(properties.mediaBucket()).key(key).build(), target);
    }

    public void uploadClip(String key, Path file, String contentType) {
        s3Client.putObject(PutObjectRequest.builder().bucket(properties.clipsBucket()).key(key).contentType(contentType)
                .cacheControl("private, max-age=3600").build(), RequestBody.fromFile(file));
    }

    /** Deletes objects from the clips bucket (missing keys are fine). */
    public void deleteClips(Collection<String> keys) {
        List<String> all = List.copyOf(keys);
        for (int i = 0; i < all.size(); i += 1000) { // S3 batch limit
            List<ObjectIdentifier> batch = all.subList(i, Math.min(i + 1000, all.size())).stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build()).toList();
            s3Client.deleteObjects(b -> b.bucket(properties.clipsBucket()).delete(d -> d.objects(batch).quiet(true)));
        }
    }

    /** Temporary GET URL for a processed clip or its thumbnail (private clips bucket). */
    public String clipUrl(String key, java.time.Duration ttl) {
        if (key == null) {
            return null;
        }
        return presigner.presignGetObject(b -> b
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder().bucket(properties.clipsBucket()).key(key).build()))
                .url().toString();
    }

    private final java.util.Map<String, S3Presigner> presignersByEndpoint = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Like {@link #clipUrl} but signed for another address of the same storage (e.g. {@code http://minio:9000} for a
     * service inside docker-compose). SigV4 signs the host, so the URL must be presigned for the host the caller uses.
     */
    public String clipUrlVia(String endpoint, String key, java.time.Duration ttl) {
        if (endpoint == null || endpoint.isBlank()) {
            return clipUrl(key, ttl);
        }
        S3Presigner other = presignersByEndpoint.computeIfAbsent(endpoint.strip(), e -> S3Presigner.builder()
                .endpointOverride(java.net.URI.create(e))
                .region(software.amazon.awssdk.regions.Region.of(properties.region()))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build());
        return other.presignGetObject(b -> b.signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder().bucket(properties.clipsBucket()).key(key).build()))
                .url().toString();
    }

    /**
     * Temporary GET URL that makes browsers save the clip as {@code filename} (the signed request carries
     * {@code response-content-disposition: attachment}).
     */
    public String clipAttachmentUrl(String key, java.time.Duration ttl, String filename) {
        String safe = filename.replaceAll("[^A-Za-z0-9._-]", "-");
        return presigner.presignGetObject(b -> b
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder().bucket(properties.clipsBucket()).key(key)
                                .responseContentDisposition("attachment; filename=\"" + safe + "\"")
                                .responseContentType("video/mp4")
                                .build()))
                .url().toString();
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
        createBucketIfMissing(properties.mediaBucket());
        createBucketIfMissing(properties.clipsBucket());
        allowBrowserUploads(properties.mediaBucket());
        allowBrowserUploads(properties.clipsBucket());
    }

    /**
     * Browsers PUT straight to presigned URLs (station phones, owner image uploads), which needs a
     * CORS rule on the bucket for the web app's origins. Real S3 needs this too; set it there as
     * part of provisioning if the backend doesn't manage buckets.
     */
    private void allowBrowserUploads(String bucket) {
        try {
            s3Client.putBucketCors(b -> b.bucket(bucket).corsConfiguration(c -> c.corsRules(CORSRule.builder()
                    .allowedOrigins(webOrigins)
                    .allowedMethods("PUT", "GET", "HEAD")
                    .allowedHeaders("*")
                    .exposeHeaders("ETag")
                    .maxAgeSeconds(3600)
                    .build())));
        } catch (S3Exception e) {
            log.warn("Could not set CORS on bucket '{}' ({}); browser uploads will fail until it allows {}",
                    bucket, e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorCode(), webOrigins);
        }
    }

    private void createBucketIfMissing(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created bucket '{}'", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created bucket '{}'", bucket);
        }
    }

    public record PresignedUpload(String url, Instant expiresAt, Map<String, String> headers) {
    }
}
