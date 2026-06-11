package com.serveone.mama;

import com.serveone.mama.config.MamaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan(basePackageClasses = MamaProperties.class)
public class MamaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MamaApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
