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
 * URL is where a board's webmaster can reach whoever runs this service; the scheduler refuses to start without one, so
 * nothing crawls anonymously on a schedule.
 */
@Component
public class MonitorSettings {

    /** The product token of the User-Agent, the name a board's robots.txt addresses. */
    public static final String PRODUCT = "LexvayMonitor";

    /** An http(s) address of visible ASCII without parentheses, so it can sit inside the User-Agent comment. */
    private static final Pattern CONTACT_URL = Pattern.compile("^https?://[\\x21-\\x27\\x2A-\\x7E]+$");

    private final String contactUrl;
    private final Duration minHostInterval;
    private final String userAgent;

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
        this.userAgent = PRODUCT + "/" + version + (contact.isEmpty() ? "" : " (+" + contact + ")");
    }

    public boolean hasContactUrl() {
        return !contactUrl.isEmpty();
    }

    public Duration minHostInterval() {
        return minHostInterval;
    }

    public String userAgent() {
        return userAgent;
    }
}
