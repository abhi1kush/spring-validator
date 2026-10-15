package com.abhi1kush.app;

import com.abhi1kush.validator.spring.EnableJsonRequestValidator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point. Scans {@code com.abhi1kush.api} for controllers and explicitly opts in to the
 * validator with {@link EnableJsonRequestValidator} (validator-core never activates on its own).
 */
@EnableJsonRequestValidator
@SpringBootApplication(scanBasePackages = {"com.abhi1kush.app", "com.abhi1kush.api"})
public class SpringValidatorApplication {

    /** @param args command line arguments */
    public static void main(String[] args) {
        SpringApplication.run(SpringValidatorApplication.class, args);
    }
}
