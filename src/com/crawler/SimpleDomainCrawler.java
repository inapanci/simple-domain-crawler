package com.crawler;

import com.crawler.util.UrlUtils;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple JDK-only multithreaded domain crawler.
 * Requirements:
 *  - Only standard JDK classes
 *  - Crawl same-domain pages
 *  - Use multithreading
 *  - Output all collected links sorted by label
 */
public class SimpleDomainCrawler {
    private final String startUrl;
    private final int maxThreads;
    private final int crawlLimit;

    private ExecutorService executorService;
    private final UrlUtils urlUtils;

    private final ConcurrentHashMap.KeySetView<String, Boolean> visitedUrls;
    private final ConcurrentHashMap.KeySetView<String, Boolean> collectedLinks;
    private final AtomicInteger activeTasks;
    private volatile boolean crawlingComplete = false;
    private final AtomicLong totalSubmittedTasks;

    // Metric usage
    private final long startTime;

    // Safety against resource consuming websites
    private static final int MAX_COLLECTED_LINKS = 500000;

    public SimpleDomainCrawler(String startUrl, int maxThreads, int crawlLimit) throws MalformedURLException {
        this.startUrl = startUrl;
        this.maxThreads = maxThreads;
        this.crawlLimit = crawlLimit;

        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.collectedLinks = ConcurrentHashMap.newKeySet();
        this.activeTasks = new AtomicInteger(0);
        this.totalSubmittedTasks = new AtomicLong(0);
        this.startTime = System.currentTimeMillis();

        this.urlUtils = new UrlUtils(new URL(startUrl));
    }

    public void startCrawling() {
        System.out.println("Crawling " + startUrl  + " with " + maxThreads + " threads" + " ...");

        this.executorService = Executors.newFixedThreadPool(maxThreads);

        submitTask(startUrl);

        try {
            awaitCompletion();
        } catch (InterruptedException e) {
            System.err.println("Crawler interrupted during completion.");
            Thread.currentThread().interrupt();
        } finally {
            // Cleanup and Report generation
            shutdownExecutor();
            outputResults();
        }
    }

    // Calling CrawlTask for new links
    public void submitTask(String url) {
        // Safety break check
        if (collectedLinks.size() >= MAX_COLLECTED_LINKS) {
            crawlingComplete = true;
            return;
        }
        if (crawlingComplete) { return; }

        String canonical = urlUtils.urlNormalization(url);

        // only submit task if it hasn't been crawled yet AND crawl limit not met
        if (visitedUrls.add(canonical)) {
            long submittedCount = totalSubmittedTasks.incrementAndGet();

            if (submittedCount > crawlLimit) {
                return;
            }

            activeTasks.incrementAndGet();
            executorService.execute(new CrawlTask(this, urlUtils, canonical));
        }
    }

    // called by CrawlTask upon successful fetch (2xx status code)
    public void markSuccessful(String url) {
        String canonical = urlUtils.urlNormalization(url);
        collectedLinks.add(canonical);
    }

    public void taskCompleted() {
        activeTasks.decrementAndGet();
    }

    public void logError(String reason, String url) {
        System.err.println("\n ERROR: " + reason + " -> " + url);
    }

    public void logSkip(String reason, String url) {
        System.out.println("\n SKIP: " + reason + " -> " + url);
    }

    private void awaitCompletion() throws InterruptedException {
        int heartbeatCounter = 0;
        char[] spinner = {'|', '/', '-', '\\'};

        while (activeTasks.get() > 0 && !crawlingComplete) {

            // Active Status Update
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            int uniqueLinks = collectedLinks.size();
            int active = activeTasks.get();
            long submitted = totalSubmittedTasks.get();

            String status = String.format(
                    "\r[%c] Active: %d | Found: %d | Submitted: %d | Time: %ds ",
                    spinner[heartbeatCounter % spinner.length], active, uniqueLinks, submitted, duration
            );
            System.out.print(status);
            heartbeatCounter++;

            // wait and check again
            Thread.sleep(500);
        }
        System.out.println();
    }

    private void shutdownExecutor() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                // wait for currently executing tasks to finish
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void outputResults() {
        System.out.println("\n--- Crawling Finished ---");

        if (collectedLinks.isEmpty()) {
            return;
        }

        System.out.println("\n--- Unique Links Found (Sorted by Label) ---");
        List<String> sortedLinks = new ArrayList<>(collectedLinks);
        Collections.sort(sortedLinks);

        for (String url : sortedLinks) {
            System.out.println(url);
        }
        System.out.println("-----------------------------------");
        System.out.println("Number of links collected: " + collectedLinks.size());
    }

    /**
     * Main entry point for the Simple Domain Crawler application.
     * Takes command line arguments.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java com.crawler.SimpleDomainCrawler <startUrl> [maxThreads] [crawlLimit]");
            System.out.println("Example: java com.crawler.SimpleDomainCrawler https://ecosio.com");
            System.exit(1);
        }

        String startUrl = args[0];

        int maxThreads = 10; // Default nr of threads
        if (args.length > 1) {
            try {
                maxThreads = Integer.parseInt(args[1]);
                if (maxThreads <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid thread number provided. Defaulting to 10 threads.");
                maxThreads = 10;
            }
        }

        int crawlLimit = Integer.MAX_VALUE; // Default to no limit
        if (args.length > 2) {
            try {
                crawlLimit = Integer.parseInt(args[2]);
                if (crawlLimit <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid crawl limit provided. Continuing with no limit default.");
                crawlLimit = Integer.MAX_VALUE;
            }
        }

        try {
            SimpleDomainCrawler crawler = new SimpleDomainCrawler(startUrl, maxThreads, crawlLimit);
            crawler.startCrawling();
        } catch (MalformedURLException e) {
            System.err.println("Error: The provided URL is invalid: " + startUrl);
            System.exit(1);
        }
    }
}
