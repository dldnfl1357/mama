package com.serveone.mama.pipeline;

public record SignalPhaseResult(int fetched, int candidates, int succeeded, int failed) {}
