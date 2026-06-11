package com.serveone.mama.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!pipeline")
public class PipelineScheduler {

    private final PipelineRunner runner;

    public PipelineScheduler(PipelineRunner runner) {
        this.runner = runner;
    }

    @Scheduled(cron = "${mama.pipeline.signal-phase-cron}", zone = "Asia/Seoul")
    public void runSignalPhase() {
        try {
            runner.runSignalPhase();
        } catch (RuntimeException e) {
            log.error("scheduled signal phase failed", e);
        }
    }

    @Scheduled(cron = "${mama.pipeline.execution-phase-cron}", zone = "Asia/Seoul")
    public void runExecutionPhase() {
        try {
            runner.runExecutionPhase();
        } catch (RuntimeException e) {
            log.error("scheduled execution phase failed", e);
        }
    }
}
