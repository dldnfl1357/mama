package com.serveone.mama.kis;

import com.serveone.mama.config.MamaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
public class KisTokenManager {

    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final Duration REFRESH_BUFFER = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final MamaProperties.Kis props;
    private final Clock clock;

    private String cachedToken;
    private Instant expiresAt;

    public KisTokenManager(RestClient.Builder builder, MamaProperties properties, Clock clock) {
        this.props = properties.kis();
        this.restClient = builder.build();
        this.clock = clock;
    }

    public synchronized String accessToken() {
        Instant now = Instant.now(clock);
        if (cachedToken != null && expiresAt != null && now.isBefore(expiresAt.minus(REFRESH_BUFFER))) {
            return cachedToken;
        }
        TokenResponse response = restClient.post()
                .uri(props.activeBaseUrl() + TOKEN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", props.appKey(),
                        "appsecret", props.appSecret()
                ))
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new KisException("KIS token issuance returned empty body");
        }
        cachedToken = response.accessToken();
        expiresAt = now.plusSeconds(response.expiresIn());
        log.info("KIS token refreshed (expires in {}s, paper={})", response.expiresIn(), props.paperTrading());
        return cachedToken;
    }

    public synchronized void invalidate() {
        cachedToken = null;
        expiresAt = null;
    }
}
