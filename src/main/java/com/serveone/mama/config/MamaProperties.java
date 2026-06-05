package com.serveone.mama.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "mama")
public record MamaProperties(
        Kis kis,
        Dart dart,
        OpenAi openai,
        Watchlist watchlist,
        Executor executor,
        Pipeline pipeline
) {

    public record Kis(
            @NotBlank String appKey,
            @NotBlank String appSecret,
            @NotBlank String accountNo,
            boolean paperTrading,
            @NotBlank String baseUrl,
            @NotBlank String paperBaseUrl,
            String tokenCachePath
    ) {
        public String activeBaseUrl() {
            return paperTrading ? paperBaseUrl : baseUrl;
        }
    }

    public record Dart(@NotBlank String apiKey, @NotBlank String baseUrl) {}

    public record OpenAi(@NotBlank String apiKey, @NotBlank String model) {}

    public record Watchlist(List<String> tickers) {
        public Watchlist {
            tickers = tickers == null ? List.of() : List.copyOf(tickers);
        }
        public boolean contains(String ticker) {
            return ticker != null && tickers.contains(ticker);
        }
    }

    public record Executor(double cashFraction, double minConfidence) {}

    public record Pipeline(
            String signalPhaseCron,
            String executionPhaseCron,
            long transientRetryBackoffMs
    ) {}
}
