package com.serveone.mama.pipeline;

import com.serveone.mama.config.MamaProperties;
import com.serveone.mama.dart.DartIngestException;
import com.serveone.mama.dart.DisclosureIngestService;
import com.serveone.mama.dart.DisclosureItem;
import com.serveone.mama.dart.IngestPage;
import com.serveone.mama.dart.entity.DisclosureEntity;
import com.serveone.mama.kis.KisClient;
import com.serveone.mama.llm.OpenAiClientException;
import com.serveone.mama.signal.Action;
import com.serveone.mama.signal.Signal;
import com.serveone.mama.signal.SignalGenerator;
import com.serveone.mama.signal.SignalRepository;
import com.serveone.mama.signal.entity.SignalEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineRunnerTests {

    private static final Instant FIXED = Instant.parse("2026-06-05T07:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneId.of("Asia/Seoul"));

    private DisclosureIngestService ingestService;
    private SignalGenerator generator;
    private SignalRepository signalRepo;
    private KisClient kisClient;

    @BeforeEach
    void setUp() {
        ingestService = mock(DisclosureIngestService.class);
        generator = mock(SignalGenerator.class);
        signalRepo = mock(SignalRepository.class);
        kisClient = mock(KisClient.class);
    }

    private PipelineRunner runner(MamaProperties.Watchlist watchlist) {
        MamaProperties props = new MamaProperties(
                new MamaProperties.Kis("k", "s", "0-0", true, "https://x", "https://y", null),
                new MamaProperties.Dart("k", "https://x"),
                new MamaProperties.OpenAi("k", "gpt-4o-mini"),
                watchlist,
                new MamaProperties.Executor(0.01, 0.6),
                new MamaProperties.Pipeline("0 0 16 * * MON-FRI", "0 5 9 * * MON-FRI", 0L)
        );
        return new PipelineRunner(ingestService, generator, signalRepo, kisClient, props, CLOCK);
    }

    private static DisclosureEntity entity(String rceptNo, String ticker) {
        return DisclosureEntity.of(new DisclosureItem(
                "corp", "회사", ticker, "Y", "공시", rceptNo, "회사", "20260605", null
        ), FIXED);
    }

    @Nested
    class SignalPhase {

        @Test
        void emptyWatchlistProducesNoSignals() {
            when(ingestService.ingest(any(), any(), eq(1), eq(100)))
                    .thenReturn(new IngestPage(List.of(entity("rc1", "005930")), 1));

            SignalPhaseResult result = runner(new MamaProperties.Watchlist(List.of()))
                    .runSignalPhase();

            assertThat(result.fetched()).isEqualTo(1);
            assertThat(result.candidates()).isZero();
            assertThat(result.succeeded()).isZero();
            assertThat(result.failed()).isZero();
            verify(generator, never()).generate(any());
            verify(signalRepo, never()).save(any());
        }

        @Test
        void skipsCandidatesAlreadyHavingSignal() {
            when(ingestService.ingest(any(), any(), eq(1), eq(100))).thenReturn(
                    new IngestPage(List.of(
                            entity("rc1", "005930"),
                            entity("rc2", "005930")
                    ), 1));
            when(signalRepo.existsById("rc1")).thenReturn(true);
            when(signalRepo.existsById("rc2")).thenReturn(false);
            when(generator.generate(any())).thenReturn(new Signal("005930", Action.BUY, 0.8, "x"));

            SignalPhaseResult result = runner(new MamaProperties.Watchlist(List.of("005930")))
                    .runSignalPhase();

            assertThat(result.candidates()).isEqualTo(1);
            assertThat(result.succeeded()).isEqualTo(1);
            verify(generator, times(1)).generate(any());
        }

        @Test
        void retriesOnceOnTransientOpenAiErrorThenSucceeds() {
            when(ingestService.ingest(any(), any(), eq(1), eq(100))).thenReturn(
                    new IngestPage(List.of(entity("rc1", "005930")), 1));
            when(signalRepo.existsById(anyString())).thenReturn(false);
            when(generator.generate(any()))
                    .thenThrow(new OpenAiClientException("transient"))
                    .thenReturn(new Signal("005930", Action.BUY, 0.8, "x"));

            SignalPhaseResult result = runner(new MamaProperties.Watchlist(List.of("005930")))
                    .runSignalPhase();

            assertThat(result.succeeded()).isEqualTo(1);
            assertThat(result.failed()).isZero();
            verify(generator, times(2)).generate(any());

            ArgumentCaptor<SignalEntity> captor = ArgumentCaptor.forClass(SignalEntity.class);
            verify(signalRepo).save(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo(Action.BUY);
        }

        @Test
        void recordsFailedSignalWhenRetryAlsoFails() {
            when(ingestService.ingest(any(), any(), eq(1), eq(100))).thenReturn(
                    new IngestPage(List.of(entity("rc1", "005930")), 1));
            when(signalRepo.existsById(anyString())).thenReturn(false);
            when(generator.generate(any()))
                    .thenThrow(new OpenAiClientException("first"))
                    .thenThrow(new OpenAiClientException("second"));

            SignalPhaseResult result = runner(new MamaProperties.Watchlist(List.of("005930")))
                    .runSignalPhase();

            assertThat(result.succeeded()).isZero();
            assertThat(result.failed()).isEqualTo(1);

            ArgumentCaptor<SignalEntity> captor = ArgumentCaptor.forClass(SignalEntity.class);
            verify(signalRepo).save(captor.capture());
            assertThat(captor.getValue().action()).isEqualTo(Action.HOLD);
            assertThat(captor.getValue().errorMessage()).contains("second");
        }

        @Test
        void paginatesIngestUntilTotalPageReached() {
            when(ingestService.ingest(any(), any(), eq(1), eq(100))).thenReturn(
                    new IngestPage(List.of(entity("rc1", "005930")), 3));
            when(ingestService.ingest(any(), any(), eq(2), eq(100))).thenReturn(
                    new IngestPage(List.of(entity("rc2", "005930")), 3));
            when(ingestService.ingest(any(), any(), eq(3), eq(100))).thenReturn(
                    new IngestPage(List.of(entity("rc3", "005930")), 3));
            when(signalRepo.existsById(anyString())).thenReturn(false);
            when(generator.generate(any())).thenReturn(new Signal("005930", Action.BUY, 0.8, "x"));

            SignalPhaseResult result = runner(new MamaProperties.Watchlist(List.of("005930")))
                    .runSignalPhase();

            assertThat(result.fetched()).isEqualTo(3);
            assertThat(result.succeeded()).isEqualTo(3);
            verify(ingestService).ingest(any(), any(), eq(1), eq(100));
            verify(ingestService).ingest(any(), any(), eq(2), eq(100));
            verify(ingestService).ingest(any(), any(), eq(3), eq(100));
        }

        @Test
        void dartFetchExceptionAbortsPhase() {
            when(ingestService.ingest(any(), any(), eq(1), eq(100)))
                    .thenThrow(new DartIngestException("dart down"));

            assertThatThrownBy(() ->
                    runner(new MamaProperties.Watchlist(List.of("005930"))).runSignalPhase()
            ).isInstanceOf(DartIngestException.class);

            verify(generator, never()).generate(any());
            verify(signalRepo, never()).save(any());
        }
    }
}
