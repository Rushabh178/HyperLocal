package com.HyperLocalSystem.app.storage;

import java.time.Duration;
import java.time.Instant;

/**
 * Where uploaded files (KYC documents) live. The dev default is {@link LocalFileStorage} (local disk,
 * served by this app); an S3/GCS implementation returning pre-signed PUT URLs can replace it behind
 * the "s3" profile without touching callers.
 */
public interface FileStorage {

    /** {@code uploadUrl}: where the app PUTs the bytes; {@code fileUrl}: the stable link to store. */
    record UploadTarget(String uploadUrl, String fileUrl, Instant expiresAt) {
    }

    UploadTarget createUpload(String key, String contentType, Duration ttl);
}
