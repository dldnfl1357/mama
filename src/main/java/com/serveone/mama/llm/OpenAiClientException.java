package com.serveone.mama.llm;

public class OpenAiClientException extends RuntimeException {
    public OpenAiClientException(String message) {
        super(message);
    }

    public OpenAiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
