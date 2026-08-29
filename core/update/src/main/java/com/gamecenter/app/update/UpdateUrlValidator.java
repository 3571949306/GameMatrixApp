package com.gamecenter.app.update;

import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared validation for update metadata and APK download URLs.
 *
 * <p>Update metadata is remote input.  Every consumer must therefore apply
 * the same transport and integrity rules instead of relying on the source
 * that happened to produce the value.</p>
 */
final class UpdateUrlValidator {

    /** Keep metadata responses bounded before parsing them as JSON. */
    static final long MAX_METADATA_SIZE_BYTES = 4L * 1024L * 1024L;

    /** APKs are expected to be far smaller than this; this caps streamed input. */
    static final long MAX_APK_SIZE_BYTES = 1L * 1024L * 1024L * 1024L;

    /** Do not follow an unbounded redirect chain supplied by a remote server. */
    static final int MAX_REDIRECTS = 5;

    private static final Pattern SHA256 = Pattern.compile("[A-Fa-f0-9]{64}");
    private static final Pattern MD5 = Pattern.compile("[A-Fa-f0-9]{32}");

    private UpdateUrlValidator() {
    }

    /**
     * Require an absolute HTTPS URL without URL-userinfo or a fragment.
     * Query parameters remain supported for signed/CDN URLs.
     */
    static void requireHttpsUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty() || !rawUrl.equals(rawUrl.trim())) {
            throw new IllegalArgumentException("Update URL is empty or contains whitespace");
        }
        final URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid update URL", error);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getPort() == 0
                || uri.getPort() > 65535) {
            throw new IllegalArgumentException(
                    "Update URL must use HTTPS without userinfo or fragments");
        }
    }

    static boolean isValidHttpsUrl(String rawUrl) {
        try {
            requireHttpsUrl(rawUrl);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Require an HTTPS URL on the configured source origin or on one of the
     * explicitly supported GitHub release delivery origins.  A valid
     * certificate alone is not sufficient: an update source must not be able
     * to redirect the client to an unrelated HTTPS host.
     */
    static void requireAllowedHttpsUrl(String rawUrl, String... configuredBases) {
        requireHttpsUrl(rawUrl);
        URI candidate = URI.create(rawUrl);
        if (isApprovedGitHubOrigin(candidate.getHost())) {
            return;
        }
        if (configuredBases != null) {
            for (String configuredBase : configuredBases) {
                if (sameOrigin(candidate, configuredBase)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Update URL is outside approved origins");
    }

    static boolean isAllowedHttpsUrl(String rawUrl, String... configuredBases) {
        try {
            requireAllowedHttpsUrl(rawUrl, configuredBases);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void requireHttpsUrl(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("Update URL is null");
        }
        requireHttpsUrl(url.toExternalForm());
    }

    /**
     * Resolve a server-supplied redirect and require that the next hop stays
     * on HTTPS.  Relative redirects are resolved against the current URL;
     * userinfo, fragments, invalid hosts and clear-text schemes are rejected.
     */
    static String resolveHttpsRedirect(URL currentUrl, String location) {
        return resolveHttpsRedirect(currentUrl, location, (String[]) null);
    }

    /**
     * Resolve a redirect and require both HTTPS and an approved destination
     * origin.  Passing no configured bases retains the syntax-only overload's
     * compatibility behavior; callers handling update data should always pass
     * the initial configured source.
     */
    static String resolveHttpsRedirect(URL currentUrl, String location,
                                       String... configuredBases) {
        if (currentUrl == null || location == null || location.isEmpty()
                || !location.equals(location.trim())) {
            throw new IllegalArgumentException("Invalid HTTPS redirect location");
        }
        try {
            URL next = new URL(currentUrl, location);
            if (configuredBases == null || configuredBases.length == 0) {
                requireHttpsUrl(next);
            } else {
                requireAllowedHttpsUrl(next.toExternalForm(), configuredBases);
            }
            return next.toExternalForm();
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid HTTPS redirect location", error);
        }
    }

    static void requireSha256(String sha256) {
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException(
                    "Update SHA-256 must be exactly 64 hexadecimal characters");
        }
    }

    static boolean isValidSha256(String sha256) {
        try {
            requireSha256(sha256);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void requireMd5(String md5) {
        if (md5 == null || !MD5.matcher(md5).matches()) {
            throw new IllegalArgumentException(
                    "Legacy update MD5 must be exactly 32 hexadecimal characters");
        }
    }

    static boolean isValidMd5(String md5) {
        try {
            requireMd5(md5);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isHttps(URL url) {
        return url != null && "https".equalsIgnoreCase(url.getProtocol());
    }

    private static boolean sameOrigin(URI candidate, String configuredBase) {
        if (configuredBase == null || configuredBase.trim().isEmpty()) {
            return false;
        }
        try {
            URI configured = URI.create(configuredBase.trim());
            if (!"https".equalsIgnoreCase(configured.getScheme())
                    || configured.getHost() == null
                    || configured.getRawUserInfo() != null
                    || configured.getRawFragment() != null
                    || configured.getPort() == 0
                    || configured.getPort() > 65535) {
                return false;
            }
            return candidate.getHost().equalsIgnoreCase(configured.getHost())
                    && effectivePort(candidate) == effectivePort(configured);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    /**
     * GitHub may redirect release pages to a small, documented set of asset
     * delivery domains.  Keep this list narrow rather than allowing arbitrary
     * cloud/S3 hosts.
     */
    private static boolean isApprovedGitHubOrigin(String rawHost) {
        if (rawHost == null || rawHost.isEmpty()) {
            return false;
        }
        String host = rawHost.toLowerCase(Locale.ROOT);
        return host.equals("github.com")
                || host.equals("githubusercontent.com")
                || host.endsWith(".githubusercontent.com")
                || host.equals("githubassets.com")
                || host.endsWith(".githubassets.com")
                || host.equals("objects.githubusercontent.com")
                || (host.startsWith("github-production-release-asset-")
                    && host.endsWith(".s3.amazonaws.com"));
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }
}
