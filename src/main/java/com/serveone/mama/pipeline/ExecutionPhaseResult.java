package com.serveone.mama.pipeline;

public record ExecutionPhaseResult(int pending, int winners, int executed, int skipped, int failed) {}
