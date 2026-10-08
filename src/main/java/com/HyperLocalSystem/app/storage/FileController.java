package com.HyperLocalSystem.app.storage;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Upload / download endpoints of {@link LocalFileStorage} (object storage serves these itself). */
@RestController
@RequestMapping("/api/v1/files")
@Profile("!s3")
@RequiredArgsConstructor
public class FileController {

    private final LocalFileStorage storage;

    /** Public, but only with a valid signed link from POST /agent/me/documents/{type}/upload-url. */
    @PutMapping("/upload")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upload(@RequestParam String key, @RequestParam long expires, @RequestParam String contentType,
                       @RequestParam String sig, @RequestHeader(name = "Content-Type", required = false) String actualContentType,
                       HttpServletRequest request) throws IOException {
        storage.store(key, expires, contentType, sig, actualContentType, request.getInputStream());
    }

    /** KYC files are private: admins can read any, a partner only their own. */
    @GetMapping("/{*key}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal Jwt jwt, @PathVariable String key) {
        String fileKey = key.startsWith("/") ? key.substring(1) : key;
        String role = jwt.getClaimAsString(TokenService.ROLE_CLAIM);
        boolean allowed = Role.ADMIN.name().equals(role)
                || (Role.DELIVERY_AGENT.name().equals(role) && fileKey.startsWith("agents/" + jwt.getSubject() + "/"));
        if (!allowed) {
            throw ApiException.forbidden("FORBIDDEN", "You can't open this file");
        }
        Path path = storage.resolve(fileKey);
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("File");
        }
        MediaType type = MediaTypeFactory.getMediaType(path.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(new FileSystemResource(path));
    }
}
