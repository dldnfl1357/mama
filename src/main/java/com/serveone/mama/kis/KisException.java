package com.serveone.mama.kis;

public class KisException extends RuntimeException {
    public KisException(String message) {
        super(message);
    }

    public KisException(String message, Throwable cause) {
        super(message, cause);
    }
}
