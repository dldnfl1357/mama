package com.serveone.mama.signal;

import com.serveone.mama.signal.entity.SignalEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class SignalRepositoryTests {

    private static final Instant T0 = Instant.parse("2026-06-05T07:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-06T00:05:00Z");

    @Autowired
    private SignalRepository repository;

    @Test
    void findExecutable_returnsBuyAndSellAboveThresholdWithNullExecutedAtAndNoError() {
        repository.save(SignalEntity.success("rc-buy-ok", new Signal("005930", Action.BUY, 0.8, "x"), T0));
        repository.save(SignalEntity.success("rc-sell-ok", new Signal("035720", Action.SELL, 0.7, "x"), T0));
        repository.save(SignalEntity.success("rc-hold", new Signal("005930", Action.HOLD, 0.9, "x"), T0));
        repository.save(SignalEntity.success("rc-low", new Signal("005930", Action.BUY, 0.4, "x"), T0));
        repository.save(SignalEntity.failed("rc-err", "005930", "boom", T0));

        SignalEntity alreadyExecuted = SignalEntity.success(
                "rc-done", new Signal("005930", Action.BUY, 0.9, "x"), T0);
        alreadyExecuted.markExecuted("ODNO", 1, T1);
        repository.save(alreadyExecuted);

        List<SignalEntity> result = repository.findExecutable(0.6);

        assertThat(result).extracting(SignalEntity::rceptNo)
                .containsExactlyInAnyOrder("rc-buy-ok", "rc-sell-ok");
    }

    @Test
    void existsById_returnsTrueAfterSave() {
        repository.save(SignalEntity.success("rc1", new Signal("005930", Action.BUY, 0.8, "x"), T0));
        assertThat(repository.existsById("rc1")).isTrue();
        assertThat(repository.existsById("rc-missing")).isFalse();
    }
}
