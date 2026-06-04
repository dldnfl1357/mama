package com.serveone.mama.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatRequest(
        String model,
        List<Message> messages,
        Integer maxTokens,
        ResponseFormat responseFormat
) {
    public record Message(String role, String content) {
        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    public record ResponseFormat(String type) {
        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object");
        }
    }
}
