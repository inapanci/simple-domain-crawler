package com.crawler;

import com.crawler.util.UrlUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Thread logic for crawling a URL
 * This class handles the HTTP connection, content decoding, link extraction.
 */
public class CrawlTask implements Runnable {
    private final SimpleDomainCrawler crawler;
    private final UrlUtils urlUtils;
    private final String canonicalUrl;

    private static final Pattern LINK_PATTERN =
            Pattern.compile("<a\\s+[^>]*href\\s*=\\s*\"(.*?)\"[^>]*>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public CrawlTask(SimpleDomainCrawler crawler, UrlUtils urlUtils, String url) {
        this.crawler = crawler;
        this.urlUtils = urlUtils;
        this.canonicalUrl = url;
    }

    @Override
    public void run() {
        HttpURLConnection httpConn;
        URLConnection connection;

        try {
            URL url = new URL(canonicalUrl);
            connection = url.openConnection();

            // set request properties
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");  // mimic modern browser
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);


            if (!(connection instanceof HttpURLConnection)) {
                httpConn = null;
            } else {
                httpConn = (HttpURLConnection) connection;
                int responseCode = httpConn.getResponseCode();

                // Handing HTTP Status Codes

                if (responseCode >= 300 && responseCode <= 399) {
                    String newUrl = httpConn.getHeaderField("Location");
                    if (newUrl != null) {
                        crawler.logSkip("Redirect " + responseCode, canonicalUrl + " -> " + newUrl);
                        crawler.submitTask(newUrl); // handle redirection url
                    }
                    return;
                }

                if (responseCode == 429 || responseCode >= 500) {
                    // Retry approach for 'too many requests' and server failures
                    long waitTime = 10000;
                    String retryAfterHeader = httpConn.getHeaderField("Retry-After");
                    if (retryAfterHeader != null) {
                        try {
                            waitTime = Long.parseLong(retryAfterHeader) * 1000;
                        } catch (NumberFormatException ignored) {}
                    }

                    String errorType = (responseCode == 429) ? "Rate Limit Error" : "Server Error";
                    crawler.logError(errorType + " (" + responseCode + "). Retrying in " + (waitTime / 1000) + "s", canonicalUrl);

                    Thread.sleep(waitTime);
                    crawler.submitTask(canonicalUrl); // re-submit the task
                    return;
                }

                if (responseCode >= 400) {
                    crawler.logSkip("Client Error " + responseCode, canonicalUrl);
                    return;
                }
            }

            // Content check
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase().contains("text/html")) {
                return;
            }

            // here we are sure link returned a 2xx http code
            crawler.markSuccessful(canonicalUrl);

            String contentEncoding = connection.getHeaderField("Content-Encoding");
            InputStream inputStream = connection.getInputStream();

            if (contentEncoding != null) {
                if (contentEncoding.equalsIgnoreCase("gzip")) {
                    inputStream = new GZIPInputStream(inputStream);
                } else if (contentEncoding.equalsIgnoreCase("deflate")) {
                    inputStream = new InflaterInputStream(inputStream);
                }
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }

            // search for links and filter invalid links
            Matcher matcher = LINK_PATTERN.matcher(content.toString());
            while (matcher.find()) {
                String link = matcher.group(1).trim();

                String normalizedLink = urlUtils.resolveAndFilterLink(link, canonicalUrl);

                if (normalizedLink != null) {
                    crawler.submitTask(normalizedLink);
                }
            }

        } catch (Exception e) {
            crawler.logError("Processing error: " + e.getMessage(), canonicalUrl);
        } finally {
            // notify that this task is completed.
            crawler.taskCompleted();
        }
    }
}