package com.serveone.mama.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("pipeline")
public class PipelineCliRunner implements ApplicationRunner {

    private final PipelineRunner runner;
    private final ConfigurableApplicationContext context;

    public PipelineCliRunner(PipelineRunner runner, ConfigurableApplicationContext context) {
        this.runner = runner;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> phaseArgs = args.getOptionValues("phase");
        if (phaseArgs == null || phaseArgs.isEmpty()) {
            log.error("Missing required argument: --phase=signal|execute");
            System.exit(SpringApplication.exit(context, () -> 1));
            return;
        }
        String phase = phaseArgs.get(0);
        final int code = executePhase(phase);
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private int executePhase(String phase) {
        try {
            switch (phase) {
                case "signal" -> runner.runSignalPhase();
                case "execute" -> runner.runExecutionPhase();
                default -> {
                    log.error("Unknown phase: {}. Expected 'signal' or 'execute'.", phase);
                    return 1;
                }
            }
            return 0;
        } catch (RuntimeException e) {
            log.error("CLI phase {} failed", phase, e);
            return 1;
        }
    }
}
