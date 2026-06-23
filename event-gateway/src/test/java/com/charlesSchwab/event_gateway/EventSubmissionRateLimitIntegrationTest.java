package com.charlesSchwab.event_gateway;

import com.charlesSchwab.event_gateway.dto.EventRequest;
import com.charlesSchwab.event_gateway.model.TransactionType;
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
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventSubmissionRateLimitIntegrationTest {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        if (!wireMock.isRunning()) {
            wireMock.start();
        }
        // Static WireMock helpers default to localhost:8080 unless explicitly pointed at the dynamic port.
        WireMock.configureFor(wireMock.port());
        registry.add("account-service.url", wireMock::baseUrl);
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
    void rapidSubmissionsReturnAtLeastOne429() {
        stubFor(post(urlPathMatching("/accounts/[^/]+/transactions"))
                .willReturn(aResponse().withStatus(200)));

        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            responses.add(submit("evt-rate-" + i));
        }

        long tooManyRequestsCount = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                .count();

        assertThat(tooManyRequestsCount).isGreaterThan(0);
    }

    private ResponseEntity<String> submit(String eventId) {
        EventRequest body = new EventRequest(
                eventId,
                "acct-rate-1",
                TransactionType.CREDIT,
                new BigDecimal("10.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                null
        );
        return restTemplate.postForEntity("/events", body, String.class);
    }
}

