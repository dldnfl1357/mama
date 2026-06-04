package com.serveone.mama.kis;

import com.serveone.mama.config.MamaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("smoke")
public class KisSmokeRunner implements ApplicationRunner {

    private static final String TICKER = "005930";
    private static final int QTY = 1;
    private static final long INITIAL_LIMIT_PRICE = 30000L;
    private static final long MODIFIED_LIMIT_PRICE = 31000L;
    private static final long INTER_CALL_PAUSE_MS = 1500L;

    private final KisTokenManager tokenManager;
    private final KisClient kisClient;
    private final MamaProperties.Kis props;
    private final ConfigurableApplicationContext context;

    public KisSmokeRunner(KisTokenManager tokenManager, KisClient kisClient, MamaProperties properties,
                          ConfigurableApplicationContext context) {
        this.tokenManager = tokenManager;
        this.kisClient = kisClient;
        this.props = properties.kis();
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        final int[] exitCode = {0};
        try {
            execute();
        } catch (RuntimeException e) {
            log.error("=== KIS smoke FAILED ===", e);
            exitCode[0] = 1;
        }
        System.exit(SpringApplication.exit(context, () -> exitCode[0]));
    }

    private void execute() throws InterruptedException {
        if (!props.paperTrading()) {
            throw new IllegalStateException(
                    "Smoke runner refuses to run in live mode. Set KIS_PAPER_TRADING=true.");
        }
        log.info("=== KIS smoke start ===");
        log.info("ticker={} qty={} initial_price={} modified_price={} account={}",
                TICKER, QTY, INITIAL_LIMIT_PRICE, MODIFIED_LIMIT_PRICE, mask(props.accountNo()));

        log.info("[1/6] Issuing access token");
        String token = tokenManager.accessToken();
        log.info("[1/6] OK (token length={})", token.length());

        Thread.sleep(INTER_CALL_PAUSE_MS);

        log.info("[2/6] Quote (current price) for {}", TICKER);
        try {
            String quote = kisClient.inquireQuote(TICKER);
            log.info("[2/6] OK quote response received (len={})", quote != null ? quote.length() : 0);
        } catch (RuntimeException e) {
            log.warn("[2/6] quote failed: {}", e.getMessage());
        }

        Thread.sleep(INTER_CALL_PAUSE_MS);

        log.info("[3/6] Balance inquiry");
        try {
            String balance = kisClient.inquireBalance();
            log.info("[3/6] OK balance response received (len={})", balance != null ? balance.length() : 0);
        } catch (RuntimeException e) {
            log.warn("[3/6] balance failed: {}", e.getMessage());
        }

        Thread.sleep(INTER_CALL_PAUSE_MS);

        log.info("[4/6] Placing limit buy: ticker={} qty={} price={}", TICKER, QTY, INITIAL_LIMIT_PRICE);
        OrderResponse buyResp;
        try {
            buyResp = kisClient.placeLimitBuy(TICKER, QTY, INITIAL_LIMIT_PRICE);
        } catch (KisException e) {
            log.warn("[4/6] limit buy failed: {}", e.getMessage());
            log.info("=== KIS smoke complete (orders unavailable; quote/balance results above) ===");
            return;
        }
        if (buyResp.output() == null || buyResp.output().orderNo() == null) {
            throw new IllegalStateException("Buy response missing odno: " + buyResp);
        }
        String orgNo = buyResp.output().orgNo();
        String odno = buyResp.output().orderNo();
        log.info("[4/6] OK orgNo={} odno={} ord_tmd={}", orgNo, odno, buyResp.output().orderTime());

        Thread.sleep(INTER_CALL_PAUSE_MS);

        log.info("[5/6] Modifying order: odno={} -> new_price={}", odno, MODIFIED_LIMIT_PRICE);
        OrderResponse modifyResp;
        try {
            modifyResp = kisClient.modifyOrder(orgNo, odno, MODIFIED_LIMIT_PRICE);
            log.info("[5/6] OK modified odno={} ord_tmd={}",
                    modifyResp.output() != null ? modifyResp.output().orderNo() : "?",
                    modifyResp.output() != null ? modifyResp.output().orderTime() : "?");
        } catch (KisException e) {
            log.warn("[5/6] modify failed (continuing to cancel original): {}", e.getMessage());
            modifyResp = null;
        }

        Thread.sleep(INTER_CALL_PAUSE_MS);

        String cancelOrgNo = modifyResp != null && modifyResp.output() != null && modifyResp.output().orderNo() != null
                ? modifyResp.output().orgNo() : orgNo;
        String cancelOdno = modifyResp != null && modifyResp.output() != null && modifyResp.output().orderNo() != null
                ? modifyResp.output().orderNo() : odno;
        log.info("[6/6] Cancelling order: orgNo={} odno={}", cancelOrgNo, cancelOdno);
        OrderResponse cancelResp = kisClient.cancelOrder(cancelOrgNo, cancelOdno);
        log.info("[6/6] OK cancelled odno={} ord_tmd={}",
                cancelResp.output() != null ? cancelResp.output().orderNo() : "?",
                cancelResp.output() != null ? cancelResp.output().orderTime() : "?");

        log.info("=== KIS smoke complete ===");
    }

    private static String mask(String accountNo) {
        if (accountNo == null || accountNo.length() < 4) {
            return "****";
        }
        return "****" + accountNo.substring(accountNo.length() - 4);
    }
}
