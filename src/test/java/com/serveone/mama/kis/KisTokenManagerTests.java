package com.serveone.mama.kis;

import com.serveone.mama.config.MamaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisTokenManagerTests {

    private static final String PAPER_BASE = "https://openapivts.koreainvestment.com:29443";

    private MockRestServiceServer mockServer;
    private MutableClock clock;
    private KisTokenManager manager;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-06-04T00:00:00Z"));
        MamaProperties props = new MamaProperties(
                new MamaProperties.Kis("app-key", "app-secret", "00000000-00",
                        true, "https://live", PAPER_BASE, null),
                new MamaProperties.Dart("d", "https://x"),
                new MamaProperties.OpenAi("a", "gpt-4o-mini"),
                new MamaProperties.Watchlist(List.of()),
                new MamaProperties.Executor(0.01, 0.6),
                new MamaProperties.Pipeline("0 0 16 * * MON-FRI", "0 5 9 * * MON-FRI", 0L)
        );
        manager = new KisTokenManager(builder, props, clock);
    }

    @Test
    void accessToken_issuesTokenAndCachesIt() {
        mockServer.expect(requestTo(PAPER_BASE + "/oauth2/tokenP"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andExpect(jsonPath("$.appkey").value("app-key"))
                .andExpect(jsonPath("$.appsecret").value("app-secret"))
                .andRespond(withSuccess("""
                        {"access_token":"TOK-1","token_type":"Bearer","expires_in":86400}
                        """, APPLICATION_JSON));

        assertThat(manager.accessToken()).isEqualTo("TOK-1");
        assertThat(manager.accessToken()).isEqualTo("TOK-1");

        mockServer.verify();
    }

    @Test
    void accessToken_refreshesAfterExpiryWindow() {
        mockServer.expect(requestTo(PAPER_BASE + "/oauth2/tokenP"))
                .andRespond(withSuccess("""
                        {"access_token":"TOK-1","token_type":"Bearer","expires_in":600}
                        """, APPLICATION_JSON));
        mockServer.expect(requestTo(PAPER_BASE + "/oauth2/tokenP"))
                .andRespond(withSuccess("""
                        {"access_token":"TOK-2","token_type":"Bearer","expires_in":600}
                        """, APPLICATION_JSON));

        assertThat(manager.accessToken()).isEqualTo("TOK-1");
        // expires in 600s, refresh buffer 5min — advance past the (expiry - buffer) point.
        clock.advance(Duration.ofSeconds(310));
        assertThat(manager.accessToken()).isEqualTo("TOK-2");

        mockServer.verify();
    }

    @Test
    void accessToken_throwsWhenIssuanceReturnsEmpty() {
        mockServer.expect(requestTo(PAPER_BASE + "/oauth2/tokenP"))
                .andRespond(withSuccess("""
                        {"access_token":"","token_type":"Bearer","expires_in":86400}
                        """, APPLICATION_JSON));

        assertThatThrownBy(() -> manager.accessToken())
                .isInstanceOf(KisException.class)
                .hasMessageContaining("empty");
    }

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            this.now = this.now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
