package com.gamecenter.app.ai.model;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * Security boundary for remote AI model metadata and local model paths.
 *
 * <p>Model metadata is fetched from a remote manifest and must therefore be
 * treated as untrusted input at every use site.  This class keeps the filename,
 * checksum, size, URL-origin and canonical-path checks in one place so callers
 * cannot accidentally validate one representation and use another.</p>
 */
final class AiModelDownloadValidator {

    /** Keep one model from consuming unbounded private application storage. */
    static final long MAX_MODEL_SIZE_BYTES = 2L * 1024L * 1024L * 1024L;

    /** Android/Linux filesystems support 255-byte names; leave room for .download. */
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,245}");
    private static final Pattern SHA256 = Pattern.compile("[A-Fa-f0-9]{64}");
    private static final int DEFAULT_HTTPS_PORT = 443;

    private AiModelDownloadValidator() {
    }

    static void validateModelMetadata(String fileName, String sha256, long sizeBytes) {
        validateFileName(fileName);
        validateSha256(sha256);
        validateSize(sizeBytes);
    }

    static void validateFileName(String fileName) {
        if (fileName == null || !SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Invalid model file name");
        }
    }

    static void validateSha256(String sha256) {
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Model SHA-256 must be exactly 64 hexadecimal characters");
        }
    }

    static void validateSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > MAX_MODEL_SIZE_BYTES) {
            throw new IllegalArgumentException("Invalid model size");
        }
    }

    /**
     * Accept only HTTPS URLs whose origin is explicitly configured for the app.
     * The second origin is optional and is used for a configured CDN fallback.
     */
    static void validateDownloadUrl(String rawUrl, String primaryBaseUrl, String fallbackBaseUrl) {
        if (rawUrl == null || !rawUrl.equals(rawUrl.trim()) || rawUrl.isEmpty()) {
            throw new IllegalArgumentException("Model URL is empty or contains whitespace");
        }
        final URI candidate;
        try {
            candidate = URI.create(rawUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid model URL", error);
        }
        if (!"https".equalsIgnoreCase(candidate.getScheme())
                || candidate.getHost() == null
                || candidate.getRawUserInfo() != null
                || candidate.getRawFragment() != null
                || candidate.getPort() == 0
                || candidate.getPort() > 65535) {
            throw new IllegalArgumentException("Model URL must use HTTPS without userinfo or fragments");
        }
        if (!matchesConfiguredOrigin(candidate, primaryBaseUrl)
                && !matchesConfiguredOrigin(candidate, fallbackBaseUrl)) {
            throw new IllegalArgumentException("Model URL host is not an approved download origin");
        }
    }

    /**
     * Resolve a model filename only when its canonical path remains below the
     * canonical model root.  This also catches an existing symlink that points
     * outside the app-private model directory.
     */
    static File resolveContainedFile(File root, String fileName) throws IOException {
        if (root == null) {
            throw new IOException("Model root is unavailable");
        }
        validateFileName(fileName);
        File canonicalRoot = root.getCanonicalFile();
        File candidate = new File(canonicalRoot, fileName).getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String candidatePath = candidate.getPath();
        String rootPrefix = rootPath.endsWith(File.separator)
                ? rootPath
                : rootPath + File.separator;
        if (!candidatePath.startsWith(rootPrefix)) {
            throw new IOException("Model path escapes private model directory");
        }
        return candidate;
    }

    private static boolean matchesConfiguredOrigin(URI candidate, String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.trim().isEmpty()) {
            return false;
        }
        try {
            URI configured = URI.create(configuredBaseUrl.trim());
            if (!"https".equalsIgnoreCase(configured.getScheme())
                    || configured.getHost() == null
                    || configured.getRawUserInfo() != null
                    || configured.getRawFragment() != null) {
                return false;
            }
            return candidate.getHost().equalsIgnoreCase(configured.getHost())
                    && effectivePort(candidate) == effectivePort(configured);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() < 0 ? DEFAULT_HTTPS_PORT : uri.getPort();
    }
}
