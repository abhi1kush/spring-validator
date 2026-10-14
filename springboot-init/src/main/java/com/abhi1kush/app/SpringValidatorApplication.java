package com.abhi1kush.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point. Scans {@code com.abhi1kush.api} for controllers; the validator is
 * provided by the {@code validator-core} auto-configuration.
 */
@SpringBootApplication(scanBasePackages = {"com.abhi1kush.app", "com.abhi1kush.api"})
public class SpringValidatorApplication {

    /** @param args command line arguments */
    public static void main(String[] args) {
        SpringApplication.run(SpringValidatorApplication.class, args);
    }
}
