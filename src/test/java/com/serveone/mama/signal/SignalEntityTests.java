package com.serveone.mama.signal;

import com.serveone.mama.signal.entity.SignalEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SignalEntityTests {

    private static final Instant T0 = Instant.parse("2026-06-05T07:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-06T00:05:00Z");

    @Test
    void success_storesAllSignalFieldsAndLeavesExecutionFieldsNull() {
        Signal s = new Signal("005930", Action.BUY, 0.8, "호재");
        SignalEntity e = SignalEntity.success("20260605000001", s, T0);

        assertThat(e.rceptNo()).isEqualTo("20260605000001");
        assertThat(e.ticker()).isEqualTo("005930");
        assertThat(e.action()).isEqualTo(Action.BUY);
        assertThat(e.confidence()).isEqualTo(0.8);
        assertThat(e.reasoning()).isEqualTo("호재");
        assertThat(e.errorMessage()).isNull();
        assertThat(e.generatedAt()).isEqualTo(T0);
        assertThat(e.executedAt()).isNull();
        assertThat(e.orderNo()).isNull();
        assertThat(e.executedQty()).isNull();
    }

    @Test
    void failed_storesErrorMessageAndHoldsActionWithZeroConfidence() {
        SignalEntity e = SignalEntity.failed("20260605000002", "035720", "boom", T0);

        assertThat(e.action()).isEqualTo(Action.HOLD);
        assertThat(e.confidence()).isZero();
        assertThat(e.errorMessage()).isEqualTo("boom");
        assertThat(e.executedAt()).isNull();
    }

    @Test
    void markExecuted_setsOrderNoQtyAndExecutedAt() {
        Signal s = new Signal("005930", Action.BUY, 0.8, "x");
        SignalEntity e = SignalEntity.success("rc1", s, T0);

        e.markExecuted("ODNO123", 5, T1);

        assertThat(e.orderNo()).isEqualTo("ODNO123");
        assertThat(e.executedQty()).isEqualTo(5);
        assertThat(e.executedAt()).isEqualTo(T1);
        assertThat(e.errorMessage()).isNull();
    }

    @Test
    void markFailed_setsErrorAndExecutedAtButNoOrderNo() {
        Signal s = new Signal("005930", Action.BUY, 0.8, "x");
        SignalEntity e = SignalEntity.success("rc1", s, T0);

        e.markFailed("no position", T1);

        assertThat(e.errorMessage()).isEqualTo("no position");
        assertThat(e.executedAt()).isEqualTo(T1);
        assertThat(e.orderNo()).isNull();
    }

    @Test
    void markSuperseded_setsErrorReferencingWinnerAndExecutedAt() {
        Signal s = new Signal("005930", Action.BUY, 0.7, "x");
        SignalEntity e = SignalEntity.success("rc1", s, T0);

        e.markSuperseded("rc2", T1);

        assertThat(e.errorMessage()).isEqualTo("superseded by rc2");
        assertThat(e.executedAt()).isEqualTo(T1);
    }
}
