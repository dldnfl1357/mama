package com.serveone.mama.dart.entity;

import com.serveone.mama.dart.DisclosureItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DisclosureEntityTests {

    private static final Instant FETCHED_AT = Instant.parse("2026-06-03T01:00:00Z");

    @Test
    void of_mapsAllFieldsAndParsesRceptDt() {
        DisclosureItem item = new DisclosureItem(
                "00126380", "삼성전자", "005930", "Y",
                "주요사항보고서", "20260601000001", "삼성전자", "20260601", "기타");

        DisclosureEntity entity = DisclosureEntity.of(item, FETCHED_AT);

        assertThat(entity.rceptNo()).isEqualTo("20260601000001");
        assertThat(entity.corpCode()).isEqualTo("00126380");
        assertThat(entity.corpName()).isEqualTo("삼성전자");
        assertThat(entity.stockCode()).isEqualTo("005930");
        assertThat(entity.corpCls()).isEqualTo("Y");
        assertThat(entity.reportNm()).isEqualTo("주요사항보고서");
        assertThat(entity.flrNm()).isEqualTo("삼성전자");
        assertThat(entity.rceptDt()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(entity.rm()).isEqualTo("기타");
        assertThat(entity.fetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void of_normalizesBlankStockCodeAndRmToNull() {
        DisclosureItem item = new DisclosureItem(
                "00100000", "비상장사", "", "N",
                "감사보고서", "20260601000002", "이사회", "20260601", "");

        DisclosureEntity entity = DisclosureEntity.of(item, FETCHED_AT);

        assertThat(entity.stockCode()).isNull();
        assertThat(entity.rm()).isNull();
    }
}
