package com.serveone.mama.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mama")
public record MamaProperties(Kis kis, Dart dart, Anthropic anthropic) {

    public record Kis(
            @NotBlank String appKey,
            @NotBlank String appSecret,
            @NotBlank String accountNo,
            boolean paperTrading,
            @NotBlank String baseUrl,
            @NotBlank String paperBaseUrl
    ) {
        public String activeBaseUrl() {
            return paperTrading ? paperBaseUrl : baseUrl;
        }
    }

    public record Dart(@NotBlank String apiKey, @NotBlank String baseUrl) {}

    public record Anthropic(@NotBlank String apiKey, @NotBlank String model) {}
}
