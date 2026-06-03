package com.serveone.mama.signal;

public record Signal(
        String ticker,
        Action action,
        double confidence,
        String reasoning
) {}
