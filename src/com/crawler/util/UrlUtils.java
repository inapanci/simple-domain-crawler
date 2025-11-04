package com.crawler.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for all URL manipulation, normalization, and filtering logic.
 */
public class UrlUtils {

    private final String baseDomain;

    // file extensions to skip during crawling
    private static final Set<String> SKIPPABLE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".svg", ".ico", ".webp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf",
            ".zip", ".rar", ".tar", ".gz", ".7z", ".bz2",
            ".mp3", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".wav", ".ogg",
            ".eps", ".ps", ".ai", ".ttf", ".woff", ".woff2", ".eot",
            ".css", ".js", ".xml", ".json", ".rss", ".atom", ".csv",
            ".exe", ".msi", ".dmg", ".deb", ".rpm", ".bin",
            ".ics"
    ));

    // pattern used to check for common non-HTTP protocols we want to skip immediately
    private static final Pattern SKIP_PROTOCOLS_PATTERN =
            Pattern.compile("^(mailto|tel|javascript|data|ftp):", Pattern.CASE_INSENSITIVE);


    public UrlUtils(URL startUrl) {
        // strip base domain off of the 'www.' for easy comparison
        this.baseDomain = stripWww(startUrl.getHost());
    }

    public String urlNormalization(String urlString) {
        String normalizedUrl = urlString;

        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            String canonicalHost = stripWww(host);

            if (!host.equals(canonicalHost)) {

                // Rebuild the URL without the 'www'
                StringBuilder sb = new StringBuilder();
                sb.append(url.getProtocol()).append("://").append(canonicalHost);

                if (url.getPort() != -1) {
                    sb.append(":").append(url.getPort());
                }

                // get both path and query components)
                sb.append(url.getFile());

                normalizedUrl = sb.toString();
            }

        } catch (MalformedURLException e) {
            // If the URL is malformed, we just proceed with the original string
            // Other filters can catch invalid links
        }

        // remove fragment identifier
        int hashIndex = normalizedUrl.indexOf('#');
        if (hashIndex != -1) {
            normalizedUrl = normalizedUrl.substring(0, hashIndex);
        }

        // remove trailing slash
        normalizedUrl = normalizedUrl.replaceFirst("/$", "");

        return normalizedUrl;
    }


    private boolean isObviousSkip(String link) {
        if (link == null || link.trim().isEmpty()) {
            return true;
        }
        return SKIP_PROTOCOLS_PATTERN.matcher(link).find();
    }

    // Checks if a URL points to a skippable file type (image, document, etc.)
    private boolean isSkippableFile(String url) {
        try {
            URL urlObj = new URL(url);
            String path = urlObj.getPath();

            int dotIndex = path.lastIndexOf('.');
            if (dotIndex > 0) {
                int slashIndex = path.lastIndexOf('/');
                if (dotIndex > slashIndex) {
                    String extension = path.substring(dotIndex).toLowerCase();
                    return SKIPPABLE_EXTENSIONS.contains(extension);
                }
            }
            return false;
        } catch (MalformedURLException e) {
            return true; // If malformed, treat as skippable
        }
    }


    private String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }


    public String resolveAndFilterLink(String link, String baseUrl) {

        // check for non-web protocols
        if (isObviousSkip(link)) {
            return null;
        }

        String absoluteUrl;

        try {
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, link);
            absoluteUrl = resolved.toExternalForm();

            // protocol filter (must be HTTP/HTTPS)
            String protocol = resolved.getProtocol();
            if (!protocol.equalsIgnoreCase("http") && !protocol.equalsIgnoreCase("https")) {
                return null;
            }

            // domain check
            if (!stripWww(resolved.getHost()).equals(this.baseDomain)){
                return null; // skip if external
            }

            // file extension check
            if (isSkippableFile(absoluteUrl)) {
                return null;
            }

            return urlNormalization(absoluteUrl);

        } catch (MalformedURLException e) {
            return null;
        }
    }
}