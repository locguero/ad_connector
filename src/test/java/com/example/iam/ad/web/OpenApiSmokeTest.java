package com.example.iam.ad.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the full application (connection pools are lazy, so no directory is
 * needed) and verifies the OpenAPI contract is served and covers the three
 * REST surfaces.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSmokeTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesOpenApiDocumentWithConnectorEndpoints() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("AD Connector API"), "should carry the configured API title");
        assertTrue(body.contains("/api/ad/{domain}/users"), "should document user endpoints");
        assertTrue(body.contains("/api/ad/{domain}/groups"), "should document group endpoints");
        assertTrue(body.contains("/api/ad/{domain}/objects/move"), "should document OU/move endpoints");
    }

    @Test
    void servesSwaggerUi() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
