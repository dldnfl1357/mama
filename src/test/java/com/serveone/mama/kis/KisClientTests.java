package com.serveone.mama.kis;

import com.serveone.mama.config.MamaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisClientTests {

    private static final String PAPER_BASE = "https://openapivts.koreainvestment.com:29443";
    private static final String LIVE_BASE = "https://openapi.koreainvestment.com:9443";
    private static final String ORDER_URL_PAPER = PAPER_BASE + "/uapi/domestic-stock/v1/trading/order-cash";
    private static final String ORDER_URL_LIVE = LIVE_BASE + "/uapi/domestic-stock/v1/trading/order-cash";
    private static final String HASHKEY_URL_PAPER = PAPER_BASE + "/uapi/hashkey";
    private static final String HASHKEY_URL_LIVE = LIVE_BASE + "/uapi/hashkey";
    private static final String HASHKEY_BODY = """
            {"BODY":{},"HASH":"hash-stub"}
            """;

    private static final String SUCCESS_BODY = """
            {
              "rt_cd": "0",
              "msg_cd": "APBK0000",
              "msg1": "주문 전송 완료",
              "output": {
                "KRX_FWDG_ORD_ORGNO": "00950",
                "ODNO": "0000123456",
                "ORD_TMD": "100530"
              }
            }
            """;

    private KisClient buildClient(boolean paper, KisTokenManager tokenManager, RestClient.Builder builder) {
        MamaProperties props = new MamaProperties(
                new MamaProperties.Kis("app-key", "app-secret", "12345678-01",
                        paper, LIVE_BASE, PAPER_BASE, null),
                new MamaProperties.Dart("d", "https://x"),
                new MamaProperties.OpenAi("a", "gpt-4o-mini")
        );
        return new KisClient(builder, props, tokenManager);
    }

    @Test
    void placeMarketBuy_inPaperModeUsesPaperTrIdAndPaperBaseUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisTokenManager token = mock(KisTokenManager.class);
        when(token.accessToken()).thenReturn("TOK");

        KisClient client = buildClient(true, token, builder);

        server.expect(requestTo(HASHKEY_URL_PAPER))
                .andRespond(withSuccess(HASHKEY_BODY, APPLICATION_JSON));
        server.expect(requestTo(ORDER_URL_PAPER))
                .andExpect(method(POST))
                .andExpect(header("authorization", "Bearer TOK"))
                .andExpect(header("appkey", "app-key"))
                .andExpect(header("appsecret", "app-secret"))
                .andExpect(header("tr_id", "VTTC0802U"))
                .andExpect(header("custtype", "P"))
                .andExpect(header("hashkey", "hash-stub"))
                .andExpect(jsonPath("$.CANO").value("12345678"))
                .andExpect(jsonPath("$.ACNT_PRDT_CD").value("01"))
                .andExpect(jsonPath("$.PDNO").value("005930"))
                .andExpect(jsonPath("$.ORD_DVSN").value("01"))
                .andExpect(jsonPath("$.ORD_QTY").value("3"))
                .andExpect(jsonPath("$.ORD_UNPR").value("0"))
                .andRespond(withSuccess(SUCCESS_BODY, APPLICATION_JSON));

        OrderResponse response = client.placeMarketBuy("005930", 3);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.output().orderNo()).isEqualTo("0000123456");
        server.verify();
    }

    @Test
    void placeMarketSell_inPaperModeUsesPaperSellTrId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisTokenManager token = mock(KisTokenManager.class);
        when(token.accessToken()).thenReturn("TOK");

        KisClient client = buildClient(true, token, builder);

        server.expect(requestTo(HASHKEY_URL_PAPER))
                .andRespond(withSuccess(HASHKEY_BODY, APPLICATION_JSON));
        server.expect(requestTo(ORDER_URL_PAPER))
                .andExpect(header("tr_id", "VTTC0801U"))
                .andRespond(withSuccess(SUCCESS_BODY, APPLICATION_JSON));

        client.placeMarketSell("005930", 1);
        server.verify();
    }

    @Test
    void placeMarketBuy_inLiveModeUsesLiveTrIdAndLiveBaseUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisTokenManager token = mock(KisTokenManager.class);
        when(token.accessToken()).thenReturn("TOK");

        KisClient client = buildClient(false, token, builder);

        server.expect(requestTo(HASHKEY_URL_LIVE))
                .andRespond(withSuccess(HASHKEY_BODY, APPLICATION_JSON));
        server.expect(requestTo(ORDER_URL_LIVE))
                .andExpect(header("tr_id", "TTTC0802U"))
                .andRespond(withSuccess(SUCCESS_BODY, APPLICATION_JSON));

        client.placeMarketBuy("005930", 1);
        server.verify();
    }

    @Test
    void placeOrder_throwsWhenKisRejects() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisTokenManager token = mock(KisTokenManager.class);
        when(token.accessToken()).thenReturn("TOK");

        KisClient client = buildClient(true, token, builder);

        server.expect(requestTo(HASHKEY_URL_PAPER))
                .andRespond(withSuccess(HASHKEY_BODY, APPLICATION_JSON));
        server.expect(requestTo(ORDER_URL_PAPER))
                .andRespond(withSuccess("""
                        {"rt_cd":"1","msg_cd":"APBK1000","msg1":"주문가능금액 부족"}
                        """, APPLICATION_JSON));

        assertThatThrownBy(() -> client.placeMarketBuy("005930", 1))
                .isInstanceOf(KisException.class)
                .hasMessageContaining("rt_cd=1")
                .hasMessageContaining("주문가능금액 부족");
    }

    @Test
    void placeOrder_validatesArguments() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        KisTokenManager token = mock(KisTokenManager.class);
        KisClient client = buildClient(true, token, builder);

        assertThatThrownBy(() -> client.placeMarketBuy("", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
        assertThatThrownBy(() -> client.placeMarketBuy("005930", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("qty");
    }
}
