package com.HyperLocalSystem.app.storage;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.security.Hashing;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * DEVELOPMENT DEFAULT. Files go under {@code app.storage.local-dir}; uploads go through a signed,
 * expiring URL on this app (PUT /api/v1/files/upload), which mimics an S3 pre-signed URL. Note this
 * only works on a single instance or with a shared volume; use object storage when scaling out.
 */
@Component
@Profile("!s3")
public class LocalFileStorage implements FileStorage {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    /** Keys under this prefix are readable without signing in (store / item photos). */
    public static final String PUBLIC_PREFIX = "public/";
    private static final String KEY_PATTERN = "^[A-Za-z0-9][A-Za-z0-9/_.-]{0,255}$";

    private final Path root;
    private final String publicBaseUrl;
    private final SecretKey key;

    public LocalFileStorage(AppProperties properties, SecretKey jwtSigningKey) {
        this.root = Path.of(properties.storage().localDir()).toAbsolutePath().normalize();
        this.publicBaseUrl = properties.storage().publicBaseUrl().replaceAll("/+$", "");
        this.key = jwtSigningKey;
    }

    @Override
    public UploadTarget createUpload(String fileKey, String contentType, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        long expires = expiresAt.getEpochSecond();
        String uploadUrl = publicBaseUrl + "/api/v1/files/upload?key=" + encode(fileKey) + "&expires=" + expires
                + "&contentType=" + encode(contentType) + "&sig=" + sign(fileKey, expires, contentType);
        return new UploadTarget(uploadUrl, publicBaseUrl + "/api/v1/files/" + fileKey, expiresAt);
    }

    /** Checks the signature, expiry and content type, then writes the body (max 10 MB). */
    public void store(String fileKey, long expires, String contentType, String signature, String actualContentType, InputStream body) {
        if (signature == null || !Hashing.constantTimeEquals(sign(fileKey, expires, contentType), signature)) {
            throw ApiException.forbidden("UPLOAD_URL_INVALID", "This upload link is not valid");
        }
        if (Instant.ofEpochSecond(expires).isBefore(Instant.now())) {
            throw ApiException.forbidden("UPLOAD_URL_EXPIRED", "This upload link has expired. Request a new one.");
        }
        if (actualContentType == null || !actualContentType.startsWith(contentType)) {
            throw ApiException.badRequest("CONTENT_TYPE_MISMATCH", "Upload the file as " + contentType);
        }
        Path target = resolve(fileKey);
        try {
            Files.createDirectories(target.getParent());
            long written;
            try (OutputStream out = Files.newOutputStream(target)) {
                written = copyLimited(body, out);
            }
            if (written > MAX_BYTES) {
                Files.deleteIfExists(target);
                throw new ApiException(HttpStatus.valueOf(413), "FILE_TOO_LARGE", "Files can be at most 10 MB");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + fileKey, e);
        }
    }

    /** Safe path for a key: rejects anything that could escape the storage directory. */
    public Path resolve(String fileKey) {
        if (fileKey == null || !fileKey.matches(KEY_PATTERN) || fileKey.contains("..")) {
            throw ApiException.notFound("File");
        }
        Path path = root.resolve(fileKey).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.notFound("File");
        }
        return path;
    }

    private static long copyLimited(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                return total;
            }
            out.write(buffer, 0, read);
        }
        return total;
    }

    private String sign(String fileKey, long expires, String contentType) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(("file-upload|" + fileKey + "|" + expires + "|" + contentType).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
