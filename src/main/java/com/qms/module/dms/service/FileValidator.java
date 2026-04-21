package com.qms.module.dms.service;

import com.qms.common.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Validates uploaded files before they are stored.
 *
 * Best practices:
 * ───────────────
 * 1. MIME type is detected from file CONTENT using Apache Tika,
 *    NOT from the Content-Type header or filename extension.
 *    A malicious user can rename a .exe to .pdf — Tika catches this.
 *
 * 2. File size is checked against the configured maximum.
 *    Spring's max-file-size also enforces this at the request level,
 *    but we double-check here with a domain-level error message.
 *
 * 3. SHA-256 checksum is computed once at upload time and stored
 *    in the document metadata. It is verified on every download.
 *
 * 4. Filename is validated to prevent null bytes, path components,
 *    and excessively long names.
 *
 * 5. Virus scanning hook: when dms.upload.virus-scan-enabled = true,
 *    the file is streamed to a ClamAV / cloud AV API.
 *    The hook is a no-op stub here — integrate with your preferred AV.
 */
@Slf4j
@Component
public class FileValidator {

    private static final Tika TIKA = new Tika();

    @Value("#{'${dms.upload.allowed-mime-types}'.split(',')}")
    private List<String> allowedMimeTypes;

    @Value("${dms.upload.max-file-size-bytes:104857600}")
    private long maxFileSizeBytes;

    @Value("${dms.upload.virus-scan-enabled:false}")
    private boolean virusScanEnabled;

    /**
     * Validates the file and returns the detected MIME type.
     * Throws AppException on any violation.
     */
    public String validateAndDetectMimeType(MultipartFile file) {
        // 1. Null / empty check
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("File must not be empty");
        }

        // 2. Filename check
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw AppException.badRequest("File must have a name");
        }
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw AppException.badRequest("Invalid characters in filename");
        }
        if (originalName.length() > 255) {
            throw AppException.badRequest("Filename must not exceed 255 characters");
        }

        // 3. Size check
        if (file.getSize() > maxFileSizeBytes) {
            throw AppException.badRequest(String.format(
                    "File size %d bytes exceeds the maximum allowed %d bytes (%.1f MB)",
                    file.getSize(), maxFileSizeBytes, maxFileSizeBytes / 1_048_576.0));
        }

        // 4. MIME type detection from content (not extension or header)
        String detectedMime;
        try {
            detectedMime = TIKA.detect(file.getInputStream(), originalName);
        } catch (IOException e) {
            throw AppException.internalError("Failed to detect file type: " + e.getMessage());
        }

        if (!allowedMimeTypes.contains(detectedMime)) {
            throw AppException.badRequest(
                    "File type '" + detectedMime + "' is not permitted. " +
                    "Allowed types: " + String.join(", ", allowedMimeTypes));
        }

        log.debug("File validated: name='{}' mime='{}' size={}B",
                originalName, detectedMime, file.getSize());

        // 5. Virus scan (integrates with ClamAV / cloud AV)
        if (virusScanEnabled) {
            scanForViruses(file);
        }

        return detectedMime;
    }

    /**
     * Compute SHA-256 checksum of the file bytes.
     * Called after MIME validation to avoid double-reading the stream.
     */
    public String computeChecksum(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = file.getBytes();
            byte[] hash  = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("Checksum computation failed for '{}': {}",
                    file.getOriginalFilename(), e.getMessage());
            return null;
        }
    }

    /**
     * Verify a file's stored checksum against a freshly computed one.
     * Call this on download to detect storage-level tampering.
     */
    public void verifyChecksum(byte[] fileBytes, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isBlank()) return;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String actual = HexFormat.of().formatHex(digest.digest(fileBytes));
            if (!actual.equals(expectedChecksum)) {
                log.error("Checksum mismatch — expected={} actual={}", expectedChecksum, actual);
                throw AppException.internalError(
                        "File integrity check failed. The document may have been tampered with.");
            }
        } catch (NoSuchAlgorithmException e) {
            log.warn("Cannot verify checksum — SHA-256 not available");
        }
    }

    /**
     * Stub: integrate with ClamAV (clamd) or a cloud AV API.
     *
     * Example with clamd:
     *   ClamdVerifier clamd = new ClamdVerifier("localhost", 3310);
     *   ScanResult result = clamd.scan(file.getInputStream());
     *   if (!result.isClean()) throw AppException.badRequest("File rejected by virus scanner");
     */
    private void scanForViruses(MultipartFile file) {
        log.debug("Virus scan stub called for '{}' — integrate ClamAV / cloud AV here",
                file.getOriginalFilename());
        // TODO: implement AV integration
    }
}
