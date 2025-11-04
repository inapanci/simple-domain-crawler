# simple-domain-crawler
Simple Multithreaded Domain Crawler

This is a command-line web crawling application written in Java. It is designed to collect unique, in-domain URLs starting from a given URL using a fixed-size thread pool.

Features
JDK Only (No Libraries): Built using only the standard Java Development Kit (JDK), with no external third-party libraries.

Multithreaded: Uses multithreading (a configured thread pool size) to crawl multiple pages concurrently.

In-Domain Crawling: Only collects and crawls links that belong to the same base domain as the start URL.

Link Filtering: It skips common non-page resources through: 

1. Protocol Skip: Rejects mailto:, tel:, etc.

2. File Skip: Checks the URL extension to skip content links like .pdf, .jpg, .zip, .css, and .js, etc.

Crawl Limit: A maximum number of pages to crawl is set in order to avoid a crawler that runs forever in websites like for example Wikipedia.

Progress Report: Displays a status update during runtime so the user does not think the application is stuck for websites with a long crawltime.

Sorted Output: Prints the final collection of unique links to the console, sorted by their label.

Usage

The application can be executed via the command line and requires a start URL. Thread pool size and crawl limit are optional arguments.

java com.crawler.SimpleDomainCrawler https://ecosio.com 
OR
java com.crawler.SimpleDomainCrawler https://panci-electronic.com 10 500

Dependencies
JDK 17 or newer.

Build and Run

Compile the Java files from the project root directory (src):

javac com/crawler/*.java com/crawler/util/*.java


Execute the application:

java com.crawler.SimpleDomainCrawler <url> [maxThreads] [crawlLimit]

Example:
java com.crawler.SimpleDomainCrawler https://ecosio.com 
java com.crawler.SimpleDomainCrawler https://ecosio.com 5
java com.crawler.SimpleDomainCrawler https://ecosio.com 10 500


Output Example

The application will print a live status line while running, followed by the final report:

Crawling example.com with x threads ...
[/] Active: 2 | Found: 55 | Submitted: 50 | Time: 12s 

--- Crawling Finished ---

--- Unique Links Found (Sorted by Label) ---
[https://example.com/about](https://example.com/about)
[https://example.com/contact](https://example.com/contact)
[https://example.com/index](https://example.com/index)
...
-----------------------------------
Number of links collected: 55
