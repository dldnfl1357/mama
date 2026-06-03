package com.serveone.mama.llm;

import com.serveone.mama.config.MamaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class ClaudeClient {

    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final MamaProperties.Anthropic props;

    public ClaudeClient(RestClient.Builder builder, MamaProperties properties) {
        this.props = properties.anthropic();
        this.restClient = builder.build();
    }

    public String complete(String system, String userMessage, int maxTokens) {
        MessagesRequest request = new MessagesRequest(
                props.model(),
                maxTokens,
                system,
                List.of(MessagesRequest.Message.user(userMessage))
        );

        MessagesResponse response = restClient.post()
                .uri(MESSAGES_URL)
                .header("x-api-key", props.apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MessagesResponse.class);

        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new ClaudeClientException("Claude returned empty content");
        }
        if (response.usage() != null) {
            log.info("claude usage: in={} out={} (stop={})",
                    response.usage().inputTokens(),
                    response.usage().outputTokens(),
                    response.stopReason());
        }

        StringBuilder sb = new StringBuilder();
        for (MessagesResponse.ContentBlock block : response.content()) {
            if ("text".equals(block.type()) && block.text() != null) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(block.text());
            }
        }
        String text = sb.toString();
        if (text.isBlank()) {
            throw new ClaudeClientException("Claude returned no text blocks (stop=" + response.stopReason() + ")");
        }
        return text;
    }
}
