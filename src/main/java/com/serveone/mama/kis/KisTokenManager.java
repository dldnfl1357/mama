package com.serveone.mama.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.serveone.mama.config.MamaProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private String cachedToken;
    private Instant expiresAt;

    public KisTokenManager(RestClient.Builder builder, MamaProperties properties, Clock clock) {
        this.props = properties.kis();
        this.restClient = builder.build();
        this.clock = clock;
    }

    @PostConstruct
    void loadFromDisk() {
        Path path = cachePath();
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            CachedToken loaded = mapper.readValue(path.toFile(), CachedToken.class);
            if (loaded.paperTrading() != props.paperTrading()) {
                log.info("KIS token cache mode mismatch (cached paper={}, current paper={}); ignoring",
                        loaded.paperTrading(), props.paperTrading());
                return;
            }
            Instant now = Instant.now(clock);
            if (loaded.expiresAt() == null || now.isAfter(loaded.expiresAt().minus(REFRESH_BUFFER))) {
                log.info("KIS token cache expired (expires_at={}); will refresh on first use", loaded.expiresAt());
                return;
            }
            this.cachedToken = loaded.accessToken();
            this.expiresAt = loaded.expiresAt();
            log.info("KIS token loaded from {} (expires_at={})", path, expiresAt);
        } catch (IOException e) {
            log.warn("Failed to load KIS token cache from {}: {}", path, e.getMessage());
        }
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
        saveToDisk();
        return cachedToken;
    }

    public synchronized void invalidate() {
        cachedToken = null;
        expiresAt = null;
        Path path = cachePath();
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete KIS token cache {}: {}", path, e.getMessage());
            }
        }
    }

    private void saveToDisk() {
        Path path = cachePath();
        if (path == null) {
            return;
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            CachedToken toSave = new CachedToken(cachedToken, expiresAt, props.paperTrading());
            mapper.writeValue(path.toFile(), toSave);
            log.debug("KIS token persisted to {}", path);
        } catch (IOException e) {
            log.warn("Failed to persist KIS token to {}: {}", path, e.getMessage());
        }
    }

    private Path cachePath() {
        String configured = props.tokenCachePath();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return Path.of(configured);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CachedToken(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_at") Instant expiresAt,
            @JsonProperty("paper_trading") boolean paperTrading
    ) {}
}
