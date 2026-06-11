package com.serveone.mama.pipeline;

import com.serveone.mama.config.MamaProperties;
import com.serveone.mama.dart.DisclosureIngestService;
import com.serveone.mama.dart.IngestPage;
import com.serveone.mama.dart.entity.DisclosureEntity;
import com.serveone.mama.kis.BalanceResponse;
import com.serveone.mama.kis.KisClient;
import com.serveone.mama.kis.KisException;
import com.serveone.mama.kis.OrderResponse;
import com.serveone.mama.llm.OpenAiClientException;
import com.serveone.mama.signal.Action;
import com.serveone.mama.signal.Signal;
import com.serveone.mama.signal.SignalGenerator;
import com.serveone.mama.signal.SignalRepository;
import com.serveone.mama.signal.entity.SignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PipelineRunner {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int PAGE_SIZE = 100;

    private final DisclosureIngestService ingestService;
    private final SignalGenerator generator;
    private final SignalRepository signalRepo;
    private final KisClient kisClient;
    private final MamaProperties.Watchlist watchlist;
    private final MamaProperties.Executor executor;
    private final Duration retryBackoff;
    private final Clock clock;

    public PipelineRunner(
            DisclosureIngestService ingestService,
            SignalGenerator generator,
            SignalRepository signalRepo,
            KisClient kisClient,
            MamaProperties properties,
            Clock clock
    ) {
        this.ingestService = ingestService;
        this.generator = generator;
        this.signalRepo = signalRepo;
        this.kisClient = kisClient;
        this.watchlist = properties.watchlist();
        this.executor = properties.executor();
        this.retryBackoff = Duration.ofMillis(properties.pipeline().transientRetryBackoffMs());
        this.clock = clock;
    }

    @Transactional
    public SignalPhaseResult runSignalPhase() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        log.info("=== Phase A (signal) start: today={} ===", today);

        List<DisclosureEntity> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            IngestPage page = ingestService.ingest(today, today, pageNo, PAGE_SIZE);
            all.addAll(page.entities());
            if (pageNo >= page.totalPage()) break;
            pageNo++;
        }

        List<DisclosureEntity> candidates = all.stream()
                .filter(e -> e.stockCode() != null && watchlist.contains(e.stockCode()))
                .filter(e -> !signalRepo.existsById(e.rceptNo()))
                .toList();

        int succeeded = 0;
        int failed = 0;
        Instant now = Instant.now(clock);
        for (DisclosureEntity entity : candidates) {
            try {
                Signal signal = RetryHelper.withRetry(
                        () -> generator.generate(entity),
                        OpenAiClientException.class,
                        retryBackoff
                );
                signalRepo.save(SignalEntity.success(entity.rceptNo(), signal, now));
                succeeded++;
            } catch (RuntimeException e) {
                log.warn("generate failed for rcept={}: {}", entity.rceptNo(), e.getMessage());
                signalRepo.save(SignalEntity.failed(
                        entity.rceptNo(), entity.stockCode(), e.getMessage(), now));
                failed++;
            }
        }

        log.info("=== Phase A complete: fetched={} candidates={} succeeded={} failed={} ===",
                all.size(), candidates.size(), succeeded, failed);
        return new SignalPhaseResult(all.size(), candidates.size(), succeeded, failed);
    }

    @Transactional
    public ExecutionPhaseResult runExecutionPhase() {
        log.info("=== Phase B (execution) start ===");
        List<SignalEntity> pending = signalRepo.findExecutable(executor.minConfidence());
        if (pending.isEmpty()) {
            log.info("=== Phase B complete: no pending signals ===");
            return new ExecutionPhaseResult(0, 0, 0, 0, 0);
        }

        Instant now = Instant.now(clock);
        Map<String, List<SignalEntity>> byTicker = pending.stream()
                .collect(Collectors.groupingBy(SignalEntity::ticker));

        List<SignalEntity> winners = new ArrayList<>();
        for (Map.Entry<String, List<SignalEntity>> entry : byTicker.entrySet()) {
            List<SignalEntity> group = new ArrayList<>(entry.getValue());
            group.sort(Comparator.comparingDouble(SignalEntity::confidence).reversed());
            SignalEntity winner = group.get(0);
            winners.add(winner);
            for (int i = 1; i < group.size(); i++) {
                group.get(i).markSuperseded(winner.rceptNo(), now);
                signalRepo.save(group.get(i));
            }
        }

        BalanceResponse balance = kisClient.inquireBalance();
        long cash = balance.deposit();
        Map<String, Integer> holdings = balance.holdingsByTicker();

        int executed = 0;
        int skipped = 0;
        int failed = 0;
        for (SignalEntity winner : winners) {
            try {
                long quote = RetryHelper.withRetry(
                        () -> kisClient.inquireQuote(winner.ticker()).currentPrice(),
                        KisException.class,
                        retryBackoff
                );
                long target = (long) Math.floor((cash * executor.cashFraction()) / (double) quote);

                int qty;
                if (winner.action() == Action.BUY) {
                    if (target < 1) {
                        winner.markFailed("qty=0 (cash=" + cash + " price=" + quote + ")", now);
                        signalRepo.save(winner);
                        skipped++;
                        continue;
                    }
                    qty = (int) Math.min(target, Integer.MAX_VALUE);
                } else { // SELL
                    int held = holdings.getOrDefault(winner.ticker(), 0);
                    qty = (int) Math.min(target, held);
                    if (qty < 1) {
                        winner.markFailed("no position", now);
                        signalRepo.save(winner);
                        skipped++;
                        continue;
                    }
                }

                final int finalQty = qty;
                OrderResponse resp = RetryHelper.withRetry(
                        () -> winner.action() == Action.BUY
                                ? kisClient.placeMarketBuy(winner.ticker(), finalQty)
                                : kisClient.placeMarketSell(winner.ticker(), finalQty),
                        KisException.class,
                        retryBackoff
                );
                String odno = resp.output() != null ? resp.output().orderNo() : null;
                winner.markExecuted(odno, finalQty, now);
                signalRepo.save(winner);
                executed++;
            } catch (KisException e) {
                log.warn("execute failed for ticker={} rcept={}: {}",
                        winner.ticker(), winner.rceptNo(), e.getMessage());
                winner.markFailed(e.getMessage(), now);
                signalRepo.save(winner);
                failed++;
            }
        }

        log.info("=== Phase B complete: pending={} winners={} executed={} skipped={} failed={} ===",
                pending.size(), winners.size(), executed, skipped, failed);
        return new ExecutionPhaseResult(pending.size(), winners.size(), executed, skipped, failed);
    }
}
