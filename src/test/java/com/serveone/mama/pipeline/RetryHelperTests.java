package com.serveone.mama.pipeline;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryHelperTests {

    @Test
    void returnsResultWhenFirstCallSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = RetryHelper.withRetry(
                () -> {
                    calls.incrementAndGet();
                    return "ok";
                },
                RuntimeException.class,
                Duration.ZERO
        );

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesOnceWhenFirstCallThrowsRetryableException() {
        AtomicInteger calls = new AtomicInteger();
        String result = RetryHelper.withRetry(
                () -> {
                    if (calls.incrementAndGet() == 1) throw new IllegalStateException("transient");
                    return "ok";
                },
                IllegalStateException.class,
                Duration.ZERO
        );

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void doesNotRetryWhenExceptionIsNotRetryable() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryHelper.withRetry(
                () -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("permanent");
                },
                IllegalStateException.class,
                Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("permanent");

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rethrowsSecondFailureWhenRetryAlsoFails() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryHelper.withRetry(
                () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("never");
                },
                IllegalStateException.class,
                Duration.ZERO
        )).isInstanceOf(IllegalStateException.class);

        assertThat(calls.get()).isEqualTo(2);
    }
}
