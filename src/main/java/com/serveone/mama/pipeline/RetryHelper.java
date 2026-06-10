package com.serveone.mama.pipeline;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
public final class RetryHelper {

    private RetryHelper() {}

    public static <T> T withRetry(
            Supplier<T> call,
            Class<? extends Exception> retryable,
            Duration backoff
    ) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            if (!retryable.isInstance(e)) throw e;
            log.warn("retrying after {}ms: {}", backoff.toMillis(), e.getMessage());
            try {
                Thread.sleep(backoff.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
            return call.get();
        }
    }
}
