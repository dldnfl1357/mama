package com.serveone.mama.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.serveone.mama.config.MamaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KisClient {

    private static final String ORDER_CASH_PATH = "/uapi/domestic-stock/v1/trading/order-cash";
    private static final String ORDER_RVSECNCL_PATH = "/uapi/domestic-stock/v1/trading/order-rvsecncl";
    private static final String HASHKEY_PATH = "/uapi/hashkey";
    private static final String QUOTE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String BALANCE_PATH = "/uapi/domestic-stock/v1/trading/inquire-balance";

    private static final String TR_PAPER_BUY = "VTTC0802U";
    private static final String TR_PAPER_SELL = "VTTC0801U";
    private static final String TR_LIVE_BUY = "TTTC0802U";
    private static final String TR_LIVE_SELL = "TTTC0801U";
    private static final String TR_PAPER_RVSECNCL = "VTTC0803U";
    private static final String TR_LIVE_RVSECNCL = "TTTC0803U";
    private static final String TR_QUOTE = "FHKST01010100";
    private static final String TR_PAPER_BALANCE = "VTTC8434R";
    private static final String TR_LIVE_BALANCE = "TTTC8434R";

    private static final String ORD_DVSN_LIMIT = "00";
    private static final String ORD_DVSN_MARKET = "01";
    private static final String ORD_UNPR_MARKET = "0";
    private static final String RVSE_CNCL_MODIFY = "01";
    private static final String RVSE_CNCL_CANCEL = "02";

    private final RestClient restClient;
    private final MamaProperties.Kis props;
    private final KisTokenManager tokenManager;

    public KisClient(RestClient.Builder builder, MamaProperties properties, KisTokenManager tokenManager) {
        this.props = properties.kis();
        this.restClient = builder.build();
        this.tokenManager = tokenManager;
    }

    public OrderResponse placeMarketBuy(String ticker, int qty) {
        return placeOrder(ticker, qty, ORD_DVSN_MARKET, ORD_UNPR_MARKET,
                props.paperTrading() ? TR_PAPER_BUY : TR_LIVE_BUY);
    }

    public OrderResponse placeMarketSell(String ticker, int qty) {
        return placeOrder(ticker, qty, ORD_DVSN_MARKET, ORD_UNPR_MARKET,
                props.paperTrading() ? TR_PAPER_SELL : TR_LIVE_SELL);
    }

    public OrderResponse placeLimitBuy(String ticker, int qty, long unitPrice) {
        if (unitPrice <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive: " + unitPrice);
        }
        return placeOrder(ticker, qty, ORD_DVSN_LIMIT, String.valueOf(unitPrice),
                props.paperTrading() ? TR_PAPER_BUY : TR_LIVE_BUY);
    }

    public OrderResponse cancelOrder(String krxFwdgOrdOrgno, String orderNo) {
        return reviseOrCancel(krxFwdgOrdOrgno, orderNo, RVSE_CNCL_CANCEL, ORD_UNPR_MARKET);
    }

    public QuoteResponse inquireQuote(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker is blank");
        }
        log.info("KIS quote: tr={} ticker={}", TR_QUOTE, ticker);
        QuoteResponse response = restClient.get()
                .uri(props.activeBaseUrl() + QUOTE_PATH
                        + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + ticker)
                .header("authorization", "Bearer " + tokenManager.accessToken())
                .header("appkey", props.appKey())
                .header("appsecret", props.appSecret())
                .header("tr_id", TR_QUOTE)
                .header("custtype", "P")
                .retrieve()
                .onStatus(status -> status.isError(), (req, resp) -> {})
                .body(QuoteResponse.class);
        if (response == null) {
            throw new KisException("KIS quote returned null body");
        }
        if (!response.isSuccess()) {
            throw new KisException(
                    "KIS quote failed: rt_cd=" + response.rtCd() + " msg_cd=" + response.msgCd() + " msg=" + response.msg());
        }
        return response;
    }

    public BalanceResponse inquireBalance() {
        String[] parts = splitAccount();
        String trId = props.paperTrading() ? TR_PAPER_BALANCE : TR_LIVE_BALANCE;
        log.info("KIS balance: tr={} paper={}", trId, props.paperTrading());
        BalanceResponse response = restClient.get()
                .uri(props.activeBaseUrl() + BALANCE_PATH
                        + "?CANO=" + parts[0]
                        + "&ACNT_PRDT_CD=" + parts[1]
                        + "&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100=")
                .header("authorization", "Bearer " + tokenManager.accessToken())
                .header("appkey", props.appKey())
                .header("appsecret", props.appSecret())
                .header("tr_id", trId)
                .header("custtype", "P")
                .retrieve()
                .onStatus(status -> status.isError(), (req, resp) -> {})
                .body(BalanceResponse.class);
        if (response == null) {
            throw new KisException("KIS balance returned null body");
        }
        if (!response.isSuccess()) {
            throw new KisException(
                    "KIS balance failed: rt_cd=" + response.rtCd() + " msg_cd=" + response.msgCd() + " msg=" + response.msg());
        }
        return response;
    }

    public OrderResponse modifyOrder(String krxFwdgOrdOrgno, String orderNo, long newUnitPrice) {
        if (newUnitPrice <= 0) {
            throw new IllegalArgumentException("newUnitPrice must be positive: " + newUnitPrice);
        }
        return reviseOrCancel(krxFwdgOrdOrgno, orderNo, RVSE_CNCL_MODIFY, String.valueOf(newUnitPrice));
    }

    private OrderResponse placeOrder(String ticker, int qty, String ordDvsn, String ordUnpr, String trId) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker is blank");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive: " + qty);
        }
        String[] parts = splitAccount();

        OrderRequest body = new OrderRequest(
                parts[0],
                parts[1],
                ticker,
                ordDvsn,
                String.valueOf(qty),
                ordUnpr
        );

        String hashKey = computeHashKey(body);
        log.info("KIS order: tr={} ticker={} qty={} dvsn={} unpr={} paper={}",
                trId, ticker, qty, ordDvsn, ordUnpr, props.paperTrading());

        OrderResponse response = restClient.post()
                .uri(props.activeBaseUrl() + ORDER_CASH_PATH)
                .header("authorization", "Bearer " + tokenManager.accessToken())
                .header("appkey", props.appKey())
                .header("appsecret", props.appSecret())
                .header("tr_id", trId)
                .header("custtype", "P")
                .header("hashkey", hashKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (req, resp) -> {})
                .body(OrderResponse.class);

        if (response == null) {
            throw new KisException("KIS order returned null body");
        }
        if (!response.isSuccess()) {
            throw new KisException(
                    "KIS order failed: rt_cd=" + response.rtCd() + " msg_cd=" + response.msgCd() + " msg=" + response.msg());
        }
        log.info("KIS order ok: odno={} ord_tmd={}",
                response.output() != null ? response.output().orderNo() : "?",
                response.output() != null ? response.output().orderTime() : "?");
        return response;
    }

    private OrderResponse reviseOrCancel(String krxFwdgOrdOrgno, String orderNo, String rvseCnclCd, String ordUnpr) {
        if (krxFwdgOrdOrgno == null || krxFwdgOrdOrgno.isBlank()) {
            throw new IllegalArgumentException("krxFwdgOrdOrgno is blank");
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo is blank");
        }
        String[] parts = splitAccount();
        String trId = props.paperTrading() ? TR_PAPER_RVSECNCL : TR_LIVE_RVSECNCL;

        CancelRequest body = new CancelRequest(
                parts[0],
                parts[1],
                krxFwdgOrdOrgno,
                orderNo,
                ORD_DVSN_LIMIT,
                rvseCnclCd,
                "0",
                ordUnpr,
                "Y"
        );

        String hashKey = computeHashKey(body);
        log.info("KIS rvsecncl: tr={} odno={} cd={} unpr={} paper={}",
                trId, orderNo, rvseCnclCd, ordUnpr, props.paperTrading());

        OrderResponse response = restClient.post()
                .uri(props.activeBaseUrl() + ORDER_RVSECNCL_PATH)
                .header("authorization", "Bearer " + tokenManager.accessToken())
                .header("appkey", props.appKey())
                .header("appsecret", props.appSecret())
                .header("tr_id", trId)
                .header("custtype", "P")
                .header("hashkey", hashKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (req, resp) -> {})
                .body(OrderResponse.class);

        if (response == null) {
            throw new KisException("KIS rvsecncl returned null body");
        }
        if (!response.isSuccess()) {
            throw new KisException(
                    "KIS rvsecncl failed: rt_cd=" + response.rtCd() + " msg_cd=" + response.msgCd() + " msg=" + response.msg());
        }
        log.info("KIS rvsecncl ok: odno={} ord_tmd={}",
                response.output() != null ? response.output().orderNo() : "?",
                response.output() != null ? response.output().orderTime() : "?");
        return response;
    }

    private String[] splitAccount() {
        String[] parts = props.accountNo().split("-", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new KisException("account-no must be in 'CANO-PRDT' form: " + props.accountNo());
        }
        return parts;
    }

    private String computeHashKey(Object body) {
        HashKeyResponse resp = restClient.post()
                .uri(props.activeBaseUrl() + HASHKEY_PATH)
                .header("appkey", props.appKey())
                .header("appsecret", props.appSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (req, resp2) -> {})
                .body(HashKeyResponse.class);
        if (resp == null || resp.hash() == null || resp.hash().isBlank()) {
            throw new KisException("KIS hashkey response missing HASH: " + resp);
        }
        return resp.hash();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HashKeyResponse(
            @JsonProperty("HASH") String hash
    ) {}
}
