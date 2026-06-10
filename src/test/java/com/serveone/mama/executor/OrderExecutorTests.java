package com.serveone.mama.executor;

import com.serveone.mama.config.MamaProperties;
import com.serveone.mama.kis.KisClient;
import com.serveone.mama.kis.OrderResponse;
import com.serveone.mama.signal.Action;
import com.serveone.mama.signal.Signal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderExecutorTests {

    private KisClient kis;
    private OrderExecutor executor;

    @BeforeEach
    void setUp() {
        kis = mock(KisClient.class);
        MamaProperties props = new MamaProperties(
                new MamaProperties.Kis("k", "s", "0-0", true, "https://x", "https://y", null),
                new MamaProperties.Dart("k", "https://x"),
                new MamaProperties.OpenAi("k", "gpt-4o-mini"),
                new MamaProperties.Watchlist(List.of()),
                new MamaProperties.Executor(0.01, 0.6),
                new MamaProperties.Pipeline("0 0 16 * * MON-FRI", "0 5 9 * * MON-FRI", 0L)
        );
        executor = new OrderExecutor(kis, props);
    }

    private static OrderResponse okWithOdno(String odno) {
        return new OrderResponse("0", "APBK0000", "ok",
                new OrderResponse.Output("00950", odno, "100530"));
    }

    @Test
    void execute_holdSignalIsSkipped() {
        OrderExecutor.ExecutionResult result =
                executor.execute(new Signal("005930", Action.HOLD, 0.9, "x"), 1);

        assertThat(result.executed()).isFalse();
        assertThat(result.reason()).isEqualTo("HOLD");
        verifyNoInteractions(kis);
    }

    @Test
    void execute_lowConfidenceIsSkipped() {
        OrderExecutor.ExecutionResult result =
                executor.execute(new Signal("005930", Action.BUY, 0.5, "x"), 1);

        assertThat(result.executed()).isFalse();
        assertThat(result.reason()).contains("0.5");
        verify(kis, never()).placeMarketBuy(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void execute_buySignalCallsPlaceMarketBuy() {
        when(kis.placeMarketBuy("005930", 2)).thenReturn(okWithOdno("ORD-1"));

        OrderExecutor.ExecutionResult result =
                executor.execute(new Signal("005930", Action.BUY, 0.8, "x"), 2);

        assertThat(result.executed()).isTrue();
        assertThat(result.orderNo()).isEqualTo("ORD-1");
        verify(kis).placeMarketBuy("005930", 2);
    }

    @Test
    void execute_sellSignalCallsPlaceMarketSell() {
        when(kis.placeMarketSell("005930", 1)).thenReturn(okWithOdno("ORD-2"));

        OrderExecutor.ExecutionResult result =
                executor.execute(new Signal("005930", Action.SELL, 0.7, "x"), 1);

        assertThat(result.executed()).isTrue();
        assertThat(result.orderNo()).isEqualTo("ORD-2");
        verify(kis).placeMarketSell("005930", 1);
    }

    @Test
    void execute_atExactlyMinConfidenceIsExecuted() {
        when(kis.placeMarketBuy("005930", 1)).thenReturn(okWithOdno("ORD-3"));

        OrderExecutor.ExecutionResult result = executor.execute(
                new Signal("005930", Action.BUY, 0.6, "x"), 1);

        assertThat(result.executed()).isTrue();
    }
}
