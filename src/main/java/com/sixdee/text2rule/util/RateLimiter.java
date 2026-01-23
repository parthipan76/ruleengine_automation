package com.sixdee.text2rule.util;

import com.sixdee.text2rule.config.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton Rate Limiter utility.
 * Applies rate limiting based on configuration.
 */
public class RateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    private static volatile RateLimiter instance;
    private static final Object lock = new Object();

    private RateLimiter() {
        // Prevent instantiation
    }

    public static RateLimiter getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new RateLimiter();
                }
            }
        }
        return instance;
    }

    /**
     * Apply rate limiting delay if enabled (delay > 0).
     */
    public void apply() {
        long delay = ConfigurationManager.getInstance().getRateLimitDelay();
        if (delay > 0) {
            try {
                // logger.debug("Applying rate limit delay: {}ms", delay); // Optional: verbose
                // logging
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Rate limit delay interrupted", e);
            }
        }
    }
}
