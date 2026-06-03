package com.serveone.mama.kis;

import com.serveone.mama.config.MamaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KisClient {

    private static final String ORDER_CASH_PATH = "/uapi/domestic-stock/v1/trading/order-cash";

    private static final String TR_PAPER_BUY = "VTTC0802U";
    private static final String TR_PAPER_SELL = "VTTC0801U";
    private static final String TR_LIVE_BUY = "TTTC0802U";
    private static final String TR_LIVE_SELL = "TTTC0801U";

    private static final String ORD_DVSN_MARKET = "01";
    private static final String ORD_UNPR_MARKET = "0";

    private final RestClient restClient;
    private final MamaProperties.Kis props;
    private final KisTokenManager tokenManager;

    public KisClient(RestClient.Builder builder, MamaProperties properties, KisTokenManager tokenManager) {
        this.props = properties.kis();
        this.restClient = builder.build();
        this.tokenManager = tokenManager;
    }

    public OrderResponse placeMarketBuy(String ticker, int qty) {
        return placeOrder(ticker, qty, props.paperTrading() ? TR_PAPER_BUY : TR_LIVE_BUY);
    }

    public OrderResponse placeMarketSell(String ticker, int qty) {
        return placeOrder(ticker, qty, props.paperTrading() ? TR_PAPER_SELL : TR_LIVE_SELL);
    }

    private OrderResponse placeOrder(String ticker, int qty, String trId) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker is blank");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive: " + qty);
        }
        String[] parts = props.accountNo().split("-", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new KisException("account-no must be in 'CANO-PRDT' form: " + props.accountNo());
        }

        OrderRequest body = new OrderRequest(
                parts[0],
                parts[1],
                ticker,
                ORD_DVSN_MARKET,
                String.valueOf(qty),
                ORD_UNPR_MARKET
        );

        log.info("KIS order: tr={} ticker={} qty={} paper={}", trId, ticker, qty, props.paperTrading());

        OrderResponse response = restClient.post()
                .uri(props.activeBaseUrl() + ORDER_CASH_PATH)
                .header("authorization", "Bearer " + tokenManager.accessToken())
                .header("appkey", props.appKey())
                .header("appsecret", props.appSecret())
                .header("tr_id", trId)
                .header("custtype", "P")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
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
}
