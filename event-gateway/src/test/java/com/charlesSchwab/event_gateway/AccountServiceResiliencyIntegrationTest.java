package com.charlesSchwab.event_gateway;

import com.charlesSchwab.event_gateway.dto.EventRequest;
import com.charlesSchwab.event_gateway.model.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountServiceResiliencyIntegrationTest {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        if (!wireMock.isRunning()) {
            wireMock.start();
        }
        // The static stubFor/verify helpers below talk to whatever port WireMock.configureFor
        // points them at -- by default that's localhost:8080, NOT this server's dynamic port.
        // Without this, stubFor() silently calls a different (possibly nonexistent) server.
        WireMock.configureFor(wireMock.port());
        registry.add("account-service.url", wireMock::baseUrl);

        // Keep retries deterministic for request-count assertions.
        registry.add("resilience4j.retry.instances.accountService.max-attempts", () -> "1");
        registry.add("resilience4j.circuitbreaker.instances.accountService.minimum-number-of-calls", () -> "2");
        registry.add("resilience4j.circuitbreaker.instances.accountService.sliding-window-size", () -> "2");
        registry.add("resilience4j.circuitbreaker.instances.accountService.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state", () -> "60s");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetServer() {
        wireMock.resetAll();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @Test
    void repeatedDownstreamFailuresReturn503AndOpenCircuitBreaker() {
        stubFor(post(urlEqualTo("/accounts/acct-123/transactions"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        ResponseEntity<String> first = submit("evt-cb-1");
        ResponseEntity<String> second = submit("evt-cb-2");
        ResponseEntity<String> third = submit("evt-cb-3");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(third.getBody()).contains("ACCOUNT_SERVICE_UNAVAILABLE");

        // First two calls hit downstream, then circuit opens and short-circuits the third.
        verify(2, postRequestedFor(urlEqualTo("/accounts/acct-123/transactions")));
        verify(postRequestedFor(urlEqualTo("/accounts/acct-123/transactions"))
                .withHeader("X-Trace-Id", matching("[0-9a-f]{16,32}"))
                .withRequestBody(matching(".*\\\"eventId\\\"\\s*:\\s*\\\"evt-cb-1\\\".*")));
    }

    private ResponseEntity<String> submit(String eventId) {
        EventRequest body = new EventRequest(
                eventId,
                "acct-123",
                TransactionType.CREDIT,
                new BigDecimal("10.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                null
        );
        return restTemplate.postForEntity("/events", body, String.class);
    }
}