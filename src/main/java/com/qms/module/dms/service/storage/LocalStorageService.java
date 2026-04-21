package com.qms.module.dms.service.storage;

import com.qms.common.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Local filesystem storage implementation.
 *
 * Active when: dms.storage.strategy = LOCAL (default)
 *
 * Directory layout:
 *   {base-path}/{year}/{month}/{doc-number}/{uuid}-{sanitized-filename}
 *
 * Example:
 *   ./dms-storage/2024/04/DOC-SOP-202404-0001/a3b9c1d2-cleaning-sop.pdf
 *
 * Best practices implemented here:
 *   ✓ Filenames are sanitized to prevent path traversal (CVE-2018-1261 class)
 *   ✓ Files are stored with a UUID prefix to prevent name collisions
 *   ✓ Directory tree is created atomically (createDirectories is idempotent)
 *   ✓ ATOMIC_MOVE is attempted on write to prevent partial files on crash
 *   ✓ The base directory is validated at startup — fails fast if not writable
 *   ✓ Streams are opened via Files.newInputStream (no buffering allocation risk)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "dms.storage.strategy", havingValue = "LOCAL", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter YEAR_FMT  = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MM");

    @Value("${dms.storage.local.base-path:./dms-storage}")
    private String basePath;

    private Path baseDir;

    @PostConstruct
    public void init() {
        baseDir = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
            // Fail fast: verify the directory is writable at startup
            if (!Files.isWritable(baseDir)) {
                throw new IllegalStateException(
                        "DMS storage directory is not writable: " + baseDir);
            }
            log.info("Local storage initialised at: {}", baseDir);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create DMS storage directory: " + baseDir, e);
        }
    }

    @Override
    public String store(MultipartFile file, String docNumber, String version) {
        try {
            // Build the directory path: {year}/{month}/{docNumber}/
            LocalDate today = LocalDate.now();
            Path directory = baseDir
                    .resolve(today.format(YEAR_FMT))
                    .resolve(today.format(MONTH_FMT))
                    .resolve(sanitize(docNumber));

            Files.createDirectories(directory);

            // Build a unique, safe filename
            String sanitizedOriginal = sanitize(file.getOriginalFilename());
            String storedFilename    = UUID.randomUUID() + "-" + sanitizedOriginal;
            Path   target            = directory.resolve(storedFilename);

            // Prevent path traversal: ensure target is inside baseDir
            assertWithinBase(target);

            // Write to a temp file first, then move atomically
            Path temp = Files.createTempFile(directory, "upload-", ".tmp");
            try {
                Files.copy(file.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    // Fall back on non-atomic move for cross-device filesystems
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                Files.deleteIfExists(temp);
                throw ex;
            }

            // Return a relative key (relative to baseDir) — portable if basePath changes
            String storageKey = baseDir.relativize(target).toString();
            log.debug("File stored: key={} size={}B", storageKey, file.getSize());
            return storageKey;

        } catch (IOException e) {
            throw AppException.internalError("Failed to store file: " + e.getMessage());
        }
    }

    @Override
    public InputStream retrieve(String storageKey) {
        Path file = resolve(storageKey);
        if (!Files.exists(file)) {
            throw AppException.notFound("File not found in storage: " + storageKey);
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw AppException.internalError("Failed to retrieve file: " + e.getMessage());
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path file = resolve(storageKey);
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("File deleted from storage: {}", storageKey);
                // Clean up empty parent directories up to baseDir
                pruneEmptyDirectories(file.getParent());
            }
        } catch (IOException e) {
            log.error("Failed to delete file '{}': {}", storageKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    @Override
    public String getStrategyName() {
        return "LOCAL";
    }

    // ── Helpers ───────────────────────────────────────────────

    private Path resolve(String storageKey) {
        Path resolved = baseDir.resolve(storageKey).normalize();
        assertWithinBase(resolved);
        return resolved;
    }

    private void assertWithinBase(Path path) {
        if (!path.startsWith(baseDir)) {
            throw AppException.forbidden(
                    "Path traversal attempt detected: " + path);
        }
    }

    /**
     * Remove path separators and dangerous characters from a filename.
     * Keeps alphanumerics, dots, hyphens, underscores.
     */
    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed";
        // Strip any directory components
        String name = FilenameUtils.getName(filename);
        // Replace non-safe characters
        name = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        // Prevent hidden files (leading dots)
        if (name.startsWith(".")) name = "_" + name;
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private void pruneEmptyDirectories(Path dir) {
        try {
            while (dir != null && !dir.equals(baseDir)) {
                try (var stream = Files.list(dir)) {
                    if (stream.findAny().isPresent()) break;  // not empty
                }
                Files.deleteIfExists(dir);
                dir = dir.getParent();
            }
        } catch (IOException ignored) {
            // Directory pruning is best-effort — not critical
        }
    }
}
