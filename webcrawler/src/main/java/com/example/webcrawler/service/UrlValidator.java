package com.example.webcrawler.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Validates, normalizes, and filters URLs for crawling.
 */
@Component
public class UrlValidator {

    private static final Set<String> IGNORED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".webp",
            ".css", ".js", ".map", ".json", ".xml",
            ".pdf", ".zip", ".tar", ".gz", ".rar", ".7z",
            ".mp3", ".mp4", ".avi", ".mkv", ".mov",
            ".woff", ".woff2", ".ttf", ".eot"
    ));

    /**
     * Checks if a string is a crawlable HTTP/HTTPS URL with a valid host.
     */
    public boolean isValid(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();

            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }

            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return false;
            }

            // Exclude static media / binary extensions
            String path = uri.getPath();
            if (path != null) {
                String lowerPath = path.toLowerCase(Locale.ROOT);
                for (String ext : IGNORED_EXTENSIONS) {
                    if (lowerPath.endsWith(ext)) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normalizes a URL:
     * - Strips fragments (#anchor)
     * - Lowercases scheme and host
     * - Resolves relative dot-segments (. and ..)
     * - Removes standard default ports (:80, :443)
     */
    public String normalize(String url) {
        if (url == null) {
            return null;
        }

        try {
            String trimmed = url.trim();

            // 1. Strip fragment
            int hashIdx = trimmed.indexOf('#');
            if (hashIdx >= 0) {
                trimmed = trimmed.substring(0, hashIdx);
            }

            URI uri = URI.create(trimmed).normalize();
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "http";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            int port = uri.getPort();

            // Omit default ports
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }

            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }

            String query = uri.getRawQuery();

            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port != -1) {
                sb.append(':').append(port);
            }
            sb.append(path);
            if (query != null && !query.isEmpty()) {
                sb.append('?').append(query);
            }

            String result = sb.toString();
            // Remove trailing slash on root if desired, but keep consistent
            if (result.endsWith("/") && path.equals("/")) {
                // leave as http://host/ or normalize without trailing slash
                result = result.substring(0, result.length() - 1);
            }

            return result;
        } catch (Exception e) {
            return url.trim();
        }
    }

    /**
     * Checks if a URL belongs to the allowed host or domain.
     */
    public boolean isDomainAllowed(String url, String allowedDomain) {
        if (allowedDomain == null || allowedDomain.trim().isEmpty()) {
            return true; // No domain restriction configured
        }

        String targetHost = extractHost(url);
        if (targetHost == null) {
            return false;
        }

        String targetLower = targetHost.toLowerCase(Locale.ROOT);
        String allowedLower = allowedDomain.trim().toLowerCase(Locale.ROOT);

        // Strip port from allowedDomain if present (e.g. localhost:9000 -> localhost)
        int colonIdx = allowedLower.indexOf(':');
        if (colonIdx > 0) {
            allowedLower = allowedLower.substring(0, colonIdx);
        }

        int targetColonIdx = targetLower.indexOf(':');
        if (targetColonIdx > 0) {
            targetLower = targetLower.substring(0, targetColonIdx);
        }

        return targetLower.equals(allowedLower) || targetLower.endsWith("." + allowedLower);
    }

    /**
     * Extracts host name from a URL.
     */
    public String extractHost(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }
}