package com.serveone.mama.dart;

import com.serveone.mama.dart.entity.DisclosureEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureIngestServiceTests {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);
    private static final Instant FIXED_NOW = Instant.parse("2026-06-03T01:23:45Z");

    private DartClient dartClient;
    private DisclosureRepository repository;
    private DisclosureIngestService service;

    @BeforeEach
    void setUp() {
        dartClient = mock(DartClient.class);
        repository = mock(DisclosureRepository.class);
        Clock fixed = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        service = new DisclosureIngestService(dartClient, repository, fixed);
    }

    @Test
    void ingest_fetchesFromDartAndSavesAll() {
        DisclosureItem item = new DisclosureItem(
                "00126380", "삼성전자", "005930", "Y",
                "주요사항보고서", "20260601000001", "삼성전자", "20260601", null);
        DisclosureListResponse response = new DisclosureListResponse(
                "000", "정상", 1, 10, 1, 1, List.of(item));
        when(dartClient.fetchDisclosures(eq(DAY), eq(DAY), anyInt(), anyInt())).thenReturn(response);

        DisclosureIngestService.IngestResult result = service.ingest(DAY, DAY, 1, 10);

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.saved()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DisclosureEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<DisclosureEntity> saved = captor.getValue();
        assertThat(saved).singleElement().satisfies(e -> {
            assertThat(e.rceptNo()).isEqualTo("20260601000001");
            assertThat(e.fetchedAt()).isEqualTo(FIXED_NOW);
        });
    }

    @Test
    void ingest_handlesEmptyList() {
        DisclosureListResponse response = new DisclosureListResponse(
                "000", "정상", 1, 10, 0, 0, null);
        when(dartClient.fetchDisclosures(eq(DAY), eq(DAY), anyInt(), anyInt())).thenReturn(response);

        DisclosureIngestService.IngestResult result = service.ingest(DAY, DAY, 1, 10);

        assertThat(result.fetched()).isZero();
        assertThat(result.saved()).isZero();
        verify(repository).saveAll(List.of());
    }

    @Test
    void ingest_throwsAndSkipsSaveWhenDartReturnsError() {
        DisclosureListResponse error = new DisclosureListResponse(
                "010", "등록되지 않은 키입니다.", null, null, null, null, null);
        when(dartClient.fetchDisclosures(eq(DAY), eq(DAY), anyInt(), anyInt())).thenReturn(error);

        assertThatThrownBy(() -> service.ingest(DAY, DAY, 1, 10))
                .isInstanceOf(DartIngestException.class)
                .hasMessageContaining("010");

        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
