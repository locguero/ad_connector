package com.example.iam.ad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone bootstrap for running the connector directly (e.g. to browse
 * Swagger UI at /swagger-ui.html). Host frameworks embedding this module as
 * a library should ignore this class and use
 * {@code @Import(AdConnectorConfiguration.class)} instead.
 */
@SpringBootApplication
public class AdConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdConnectorApplication.class, args);
    }
}
