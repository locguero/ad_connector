package com.example.iam.ad.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the connector's REST surface. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI adConnectorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AD Connector API")
                .version("v1")
                .description("""
                        Active Directory provisioning/deprovisioning connector. \
                        Operations are routed per domain (QA_ENT, DEV_ENT, AD_ENT) via the \
                        {domain} path segment. Objects are addressed by sAMAccountName by \
                        default; pass refType=OBJECT_GUID (recommended for automation) or \
                        refType=DN to address them differently."""));
    }
}
