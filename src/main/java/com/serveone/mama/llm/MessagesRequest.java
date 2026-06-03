package com.serveone.mama.llm;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MessagesRequest(
        String model,
        int maxTokens,
        String system,
        List<Message> messages
) {
    public record Message(String role, String content) {
        public static Message user(String content) {
            return new Message("user", content);
        }
    }
}
