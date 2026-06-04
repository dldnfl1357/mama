package com.serveone.mama.llm;

import com.serveone.mama.config.MamaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class OpenAiClient {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private final RestClient restClient;
    private final MamaProperties.OpenAi props;

    public OpenAiClient(RestClient.Builder builder, MamaProperties properties) {
        this.props = properties.openai();
        this.restClient = builder.build();
    }

    public String complete(String system, String userMessage, int maxTokens) {
        return send(buildRequest(system, userMessage, maxTokens, null));
    }

    public String completeJson(String system, String userMessage, int maxTokens) {
        return send(buildRequest(system, userMessage, maxTokens, ChatRequest.ResponseFormat.jsonObject()));
    }

    private ChatRequest buildRequest(String system, String userMessage, int maxTokens,
                                     ChatRequest.ResponseFormat responseFormat) {
        return new ChatRequest(
                props.model(),
                List.of(
                        ChatRequest.Message.system(system),
                        ChatRequest.Message.user(userMessage)
                ),
                maxTokens,
                responseFormat
        );
    }

    private String send(ChatRequest request) {
        ChatResponse response = restClient.post()
                .uri(CHAT_URL)
                .header("authorization", "Bearer " + props.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new OpenAiClientException("OpenAI returned empty choices");
        }
        if (response.usage() != null) {
            log.info("openai usage: prompt={} completion={} total={}",
                    response.usage().promptTokens(),
                    response.usage().completionTokens(),
                    response.usage().totalTokens());
        }
        ChatResponse.Choice choice = response.choices().get(0);
        if (choice.message() == null || choice.message().content() == null || choice.message().content().isBlank()) {
            throw new OpenAiClientException("OpenAI returned no content (finish=" + choice.finishReason() + ")");
        }
        return choice.message().content();
    }
}
