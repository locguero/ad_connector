package com.example.iam.ad.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.example.iam.ad.AdConnectorModule;

/**
 * Entry point for wiring the connector into the upstream framework:
 * {@code @Import(AdConnectorConfiguration.class)} from any Spring Boot app.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdConnectorProperties.class)
@ComponentScan(basePackageClasses = AdConnectorModule.class)
public class AdConnectorConfiguration {
}
