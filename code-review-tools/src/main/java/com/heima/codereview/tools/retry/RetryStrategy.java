package com.heima.codereview.tools.retry;

public enum RetryStrategy {
    IMMEDIATE(3, 0),
    FIXED_DELAY(5, 1000),
    EXPONENTIAL_BACKOFF(3, 1000);

    private final int maxAttempts;
    private final long initialDelayMs;

    RetryStrategy(int maxAttempts, long initialDelayMs) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public long initialDelayMs() {
        return initialDelayMs;
    }
}
