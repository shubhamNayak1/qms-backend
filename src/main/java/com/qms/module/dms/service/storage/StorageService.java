package com.qms.module.dms.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Storage abstraction — swappable between local, S3, Azure, GCP.
 *
 * Contract:
 * ─────────
 * • store()    — persist a file, return an opaque storage key
 * • retrieve() — open an InputStream from a storage key (caller closes it)
 * • delete()   — remove a file by its storage key
 * • exists()   — check whether a key resolves to an actual file
 * • getSignedUrl() — for cloud backends: return a time-limited pre-signed URL
 *                    for direct client download (bypasses the application server)
 *
 * The storage key is opaque to the caller — it may be a relative path,
 * an S3 object key, or an Azure blob name. Never expose it in API responses.
 *
 * All implementations must be thread-safe.
 */
public interface StorageService {

    /**
     * Persist the uploaded file.
     *
     * @param file       the multipart upload from the HTTP request
     * @param docNumber  document number used to build a logical directory path
     * @param version    document version e.g. "1.0"
     * @return an opaque key that uniquely identifies this stored file
     */
    String store(MultipartFile file, String docNumber, String version);

    /**
     * Open a readable stream for the given storage key.
     * The caller is responsible for closing the stream.
     */
    InputStream retrieve(String storageKey);

    /**
     * Remove the file associated with the given storage key.
     * Must be idempotent — calling on a non-existent key must not throw.
     */
    void delete(String storageKey);

    /**
     * Check whether the storage key resolves to an existing file.
     */
    boolean exists(String storageKey);

    /**
     * For cloud backends: generate a time-limited signed URL that allows
     * the client to download directly from the storage backend, bypassing
     * the application server. For local storage, returns null.
     *
     * @param storageKey  the opaque file identifier
     * @param ttlSeconds  how long the URL should be valid
     */
    default String getSignedUrl(String storageKey, int ttlSeconds) {
        return null;   // local storage does not support signed URLs
    }

    /**
     * Return the strategy name for logging and health checks.
     */
    String getStrategyName();
}
