package com.serveone.mama.executor;

import com.serveone.mama.kis.KisClient;
import com.serveone.mama.kis.OrderResponse;
import com.serveone.mama.signal.Action;
import com.serveone.mama.signal.Signal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderExecutor {

    static final double MIN_CONFIDENCE = 0.6;

    private final KisClient kis;

    public OrderExecutor(KisClient kis) {
        this.kis = kis;
    }

    public ExecutionResult execute(Signal signal, int qty) {
        if (signal.action() == Action.HOLD) {
            return ExecutionResult.skipped("HOLD");
        }
        if (signal.confidence() < MIN_CONFIDENCE) {
            return ExecutionResult.skipped(
                    "confidence " + signal.confidence() + " < " + MIN_CONFIDENCE);
        }
        OrderResponse response = switch (signal.action()) {
            case BUY -> kis.placeMarketBuy(signal.ticker(), qty);
            case SELL -> kis.placeMarketSell(signal.ticker(), qty);
            case HOLD -> throw new IllegalStateException("unreachable");
        };
        String orderNo = response.output() != null ? response.output().orderNo() : null;
        log.info("executed {} {} qty={} odno={}", signal.action(), signal.ticker(), qty, orderNo);
        return ExecutionResult.executed(orderNo);
    }

    public record ExecutionResult(boolean executed, String reason, String orderNo) {
        public static ExecutionResult skipped(String reason) {
            return new ExecutionResult(false, reason, null);
        }

        public static ExecutionResult executed(String orderNo) {
            return new ExecutionResult(true, null, orderNo);
        }
    }
}
