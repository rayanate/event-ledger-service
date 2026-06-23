package com.charlesSchwab.event_gateway;

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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountBalanceProxyIntegrationTest {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        if (!wireMock.isRunning()) {
            wireMock.start();
        }
        // stubFor()/verify() are static helpers that default to localhost:8080 unless told
        // otherwise -- point them at this server's actual (dynamic) port.
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
    void balanceProxiesSuccessfullyWhenAccountServiceIsUp() {
        stubFor(get(urlEqualTo("/accounts/acct-bal-1/balance"))
                .willReturn(okJson("{\"accountId\":\"acct-bal-1\",\"balance\":42.50}")));

        ResponseEntity<String> response =
                restTemplate.getForEntity("/accounts/acct-bal-1/balance", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("42.5");
    }

    @Test
    void balanceReturns503WhenAccountServiceIsUnreachable() {
        stubFor(get(urlEqualTo("/accounts/acct-bal-2/balance"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        ResponseEntity<String> response =
                restTemplate.getForEntity("/accounts/acct-bal-2/balance", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("ACCOUNT_SERVICE_UNAVAILABLE");
    }
}