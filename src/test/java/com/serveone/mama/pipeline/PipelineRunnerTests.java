package com.serveone.mama.pipeline;

import com.serveone.mama.config.MamaProperties;
import com.serveone.mama.dart.DartIngestException;
import com.serveone.mama.dart.DisclosureIngestService;
import com.serveone.mama.dart.DisclosureItem;
import com.serveone.mama.dart.IngestPage;
import com.serveone.mama.dart.entity.DisclosureEntity;
import com.serveone.mama.kis.BalanceResponse;
import com.serveone.mama.kis.KisClient;
import com.serveone.mama.kis.QuoteResponse;
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

    @Nested
    class ExecutionPhase {

        private SignalEntity buySignal(String rceptNo, String ticker, double confidence) {
            return SignalEntity.success(rceptNo, new Signal(ticker, Action.BUY, confidence, "x"), FIXED);
        }

        private SignalEntity sellSignal(String rceptNo, String ticker, double confidence) {
            return SignalEntity.success(rceptNo, new Signal(ticker, Action.SELL, confidence, "x"), FIXED);
        }

        private void stubBalance(long deposit, java.util.Map<String, Integer> holdings) {
            BalanceResponse balance = mock(BalanceResponse.class);
            when(balance.deposit()).thenReturn(deposit);
            when(balance.holdingsByTicker()).thenReturn(holdings);
            when(kisClient.inquireBalance()).thenReturn(balance);
        }

        private void stubQuote(String ticker, long price) {
            QuoteResponse quote = mock(QuoteResponse.class);
            when(quote.currentPrice()).thenReturn(price);
            when(kisClient.inquireQuote(ticker)).thenReturn(quote);
        }

        @Test
        void emptyPendingShortCircuitsToZeroResult() {
            when(signalRepo.findExecutable(0.6)).thenReturn(List.of());

            ExecutionPhaseResult result = runner(new MamaProperties.Watchlist(List.of()))
                    .runExecutionPhase();

            assertThat(result.pending()).isZero();
            assertThat(result.winners()).isZero();
            verify(kisClient, never()).inquireBalance();
        }

        @Test
        void groupsByTickerKeepingHighestConfidenceAndMarksOthersSuperseded() {
            SignalEntity low = buySignal("rc-low", "005930", 0.7);
            SignalEntity high = buySignal("rc-high", "005930", 0.9);
            SignalEntity other = buySignal("rc-other", "035720", 0.8);
            when(signalRepo.findExecutable(0.6)).thenReturn(List.of(low, high, other));
            stubBalance(10_000_000L, java.util.Map.of());
            stubQuote("005930", 50_000L);
            stubQuote("035720", 100_000L);
            when(kisClient.placeMarketBuy(any(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(new com.serveone.mama.kis.OrderResponse(
                            "0", "X", "ok", new com.serveone.mama.kis.OrderResponse.Output("o", "ODNO", "0900")));

            ExecutionPhaseResult result = runner(new MamaProperties.Watchlist(List.of()))
                    .runExecutionPhase();

            assertThat(result.pending()).isEqualTo(3);
            assertThat(result.winners()).isEqualTo(2);
            assertThat(low.errorMessage()).isEqualTo("superseded by rc-high");
            assertThat(low.executedAt()).isNotNull();
        }

        @Test
        void buyWithZeroTargetQtyIsMarkedFailed() {
            SignalEntity buy = buySignal("rc1", "005930", 0.8);
            when(signalRepo.findExecutable(0.6)).thenReturn(List.of(buy));
            // cash=1000, fraction=0.01, price=50000 → target = floor(10 / 50000) = 0
            stubBalance(1_000L, java.util.Map.of());
            stubQuote("005930", 50_000L);

            ExecutionPhaseResult result = runner(new MamaProperties.Watchlist(List.of()))
                    .runExecutionPhase();

            assertThat(result.skipped()).isEqualTo(1);
            assertThat(buy.errorMessage()).contains("qty=0");
            verify(kisClient, never()).placeMarketBuy(any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        void sellWithNoHoldingIsMarkedFailed() {
            SignalEntity sell = sellSignal("rc1", "005930", 0.8);
            when(signalRepo.findExecutable(0.6)).thenReturn(List.of(sell));
            stubBalance(10_000_000L, java.util.Map.of());
            stubQuote("005930", 50_000L);

            ExecutionPhaseResult result = runner(new MamaProperties.Watchlist(List.of()))
                    .runExecutionPhase();

            assertThat(result.skipped()).isEqualTo(1);
            assertThat(sell.errorMessage()).contains("no position");
            verify(kisClient, never()).placeMarketSell(any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        void buyPlacesMarketOrderAndMarksExecuted() {
            SignalEntity buy = buySignal("rc1", "005930", 0.8);
            when(signalRepo.findExecutable(0.6)).thenReturn(List.of(buy));
            // cash=10M, fraction=0.01, price=50000 → target = 2
            stubBalance(10_000_000L, java.util.Map.of());
            stubQuote("005930", 50_000L);
            when(kisClient.placeMarketBuy("005930", 2)).thenReturn(
                    new com.serveone.mama.kis.OrderResponse(
                            "0", "X", "ok",
                            new com.serveone.mama.kis.OrderResponse.Output("o", "ODNO-1", "0900")));

            ExecutionPhaseResult result = runner(new MamaProperties.Watchlist(List.of()))
                    .runExecutionPhase();

            assertThat(result.executed()).isEqualTo(1);
            assertThat(buy.orderNo()).isEqualTo("ODNO-1");
            assertThat(buy.executedQty()).isEqualTo(2);
            assertThat(buy.executedAt()).isNotNull();
            verify(kisClient).placeMarketBuy("005930", 2);
        }

        @Test
        void kisExceptionOnOrderIsRetriedOnceThenMarkedFailed() {
            SignalEntity buy = buySignal("rc1", "005930", 0.8);
            when(signalRepo.findExecutable(0.6)).thenReturn(List.of(buy));
            stubBalance(10_000_000L, java.util.Map.of());
            stubQuote("005930", 50_000L);
            when(kisClient.placeMarketBuy("005930", 2))
                    .thenThrow(new com.serveone.mama.kis.KisException("transient"))
                    .thenThrow(new com.serveone.mama.kis.KisException("still bad"));

            ExecutionPhaseResult result = runner(new MamaProperties.Watchlist(List.of()))
                    .runExecutionPhase();

            assertThat(result.failed()).isEqualTo(1);
            assertThat(buy.errorMessage()).contains("still bad");
            verify(kisClient, times(2)).placeMarketBuy("005930", 2);
        }

        @Test
        void inquireBalanceFailureAbortsPhase() {
            SignalEntity buy = buySignal("rc1", "005930", 0.8);
            when(signalRepo.findExecutable(0.6)).thenReturn(List.of(buy));
            when(kisClient.inquireBalance())
                    .thenThrow(new com.serveone.mama.kis.KisException("balance down"));

            assertThatThrownBy(() -> runner(new MamaProperties.Watchlist(List.of())).runExecutionPhase())
                    .isInstanceOf(com.serveone.mama.kis.KisException.class);

            verify(kisClient, never()).placeMarketBuy(any(), org.mockito.ArgumentMatchers.anyInt());
        }
    }
}
