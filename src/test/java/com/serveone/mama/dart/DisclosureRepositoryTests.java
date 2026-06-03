package com.serveone.mama.dart;

import com.serveone.mama.dart.entity.DisclosureEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class DisclosureRepositoryTests {

    @Autowired
    private DisclosureRepository repository;

    @Test
    void savesAndLoadsDisclosureByRceptNo() {
        DisclosureItem item = new DisclosureItem(
                "00126380", "삼성전자", "005930", "Y",
                "주요사항보고서", "20260601000001", "삼성전자", "20260601", null);

        DisclosureEntity saved = repository.save(
                DisclosureEntity.of(item, Instant.parse("2026-06-03T00:00:00Z")));

        assertThat(repository.findById(saved.rceptNo()))
                .isPresent()
                .hasValueSatisfying(found -> {
                    assertThat(found.corpName()).isEqualTo("삼성전자");
                    assertThat(found.rceptDt()).isEqualTo(LocalDate.of(2026, 6, 1));
                });
    }

    @Test
    void resavingSameRceptNoUpdatesInPlace() {
        DisclosureItem first = new DisclosureItem(
                "00126380", "삼성전자", "005930", "Y",
                "초안", "20260601000001", "삼성전자", "20260601", null);
        DisclosureItem corrected = new DisclosureItem(
                "00126380", "삼성전자", "005930", "Y",
                "정정안", "20260601000001", "삼성전자", "20260601", null);

        repository.save(DisclosureEntity.of(first, Instant.parse("2026-06-03T00:00:00Z")));
        repository.save(DisclosureEntity.of(corrected, Instant.parse("2026-06-03T01:00:00Z")));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById("20260601000001"))
                .hasValueSatisfying(found -> assertThat(found.reportNm()).isEqualTo("정정안"));
    }
}
