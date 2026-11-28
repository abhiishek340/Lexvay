package com.lexvay.rule.source;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * How the monitor introduces itself and how hard it may knock on one host (RG-2, D-566): the User-Agent
 * {@code LexvayMonitor/<version> (+<contact URL>)} and the least time between two requests to one host. The contact
 * URL is where a board's webmaster can reach whoever runs this service. Without one nothing is fetched: the scheduler
 * refuses to start, a check cannot be requested or queued, a queued check fails before any request, and there is no
 * User-Agent to send. No request is ever anonymous.
 */
@Component
public class MonitorSettings {

    /** The product token of the User-Agent, the name a board's robots.txt addresses. */
    public static final String PRODUCT = "LexvayMonitor";

    /** What a person is told when a fetch is asked for and no contact URL is configured. */
    public static final String MISSING_CONTACT = "Set LEXVAY_MONITOR_CONTACT_URL before fetching";

    /** An http(s) address of visible ASCII without parentheses, so it can sit inside the User-Agent comment. */
    private static final Pattern CONTACT_URL = Pattern.compile("^https?://[\\x21-\\x27\\x2A-\\x7E]+$");

    private final String contactUrl;
    private final Duration minHostInterval;
    private final String version;

    @Autowired
    public MonitorSettings(@Value("${lexvay.rule-sources.contact-url:}") String contactUrl,
                           @Value("${lexvay.rule-sources.min-host-interval-seconds:30}") long minHostIntervalSeconds,
                           BuildProperties build) {
        this(contactUrl, minHostIntervalSeconds, build.getVersion());
    }

    MonitorSettings(String contactUrl, long minHostIntervalSeconds, String version) {
        String contact = contactUrl == null ? "" : contactUrl.strip();
        if (!contact.isEmpty() && !CONTACT_URL.matcher(contact).matches()) {
            throw new IllegalStateException(
                    "LEXVAY_MONITOR_CONTACT_URL must be an http or https address without spaces or parentheses");
        }
        if (minHostIntervalSeconds < 0) {
            throw new IllegalStateException("LEXVAY_SOURCE_MIN_HOST_INTERVAL_SECONDS must not be negative");
        }
        this.contactUrl = contact;
        this.minHostInterval = Duration.ofSeconds(minHostIntervalSeconds);
        this.version = version;
    }

    public boolean hasContactUrl() {
        return !contactUrl.isEmpty();
    }

    public Duration minHostInterval() {
        return minHostInterval;
    }

    /** @throws IllegalStateException when no contact URL is configured: a request is never sent without one */
    public String userAgent() {
        if (contactUrl.isEmpty()) {
            throw new IllegalStateException(MISSING_CONTACT);
        }
        return PRODUCT + "/" + version + " (+" + contactUrl + ")";
    }
}
