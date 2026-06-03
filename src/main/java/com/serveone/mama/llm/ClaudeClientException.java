package com.serveone.mama.llm;

public class ClaudeClientException extends RuntimeException {
    public ClaudeClientException(String message) {
        super(message);
    }

    public ClaudeClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
