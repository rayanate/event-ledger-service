package com.charlesSchwab.event_gateway;

import com.charlesSchwab.event_gateway.dto.EventRequest;
import com.charlesSchwab.event_gateway.model.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TracePropagationIntegrationTest {

    private static HttpServer mockAccountService;
    private static String mockBaseUrl;
    private static final AtomicReference<String> traceIdHeader = new AtomicReference<>();

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        ensureMockServer();
        registry.add("account-service.url", () -> mockBaseUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void propagatesTraceIdHeaderToAccountService() {
        traceIdHeader.set(null);

        EventRequest body = new EventRequest(
                "evt-trace-001",
                "acct-123",
                TransactionType.CREDIT,
                new BigDecimal("10.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                (JsonNode) null
        );

        ResponseEntity<String> response = restTemplate.postForEntity("/events", body, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(traceIdHeader.get())
                .matches("[0-9a-f]{16,32}");
    }

    @AfterAll
    static void stopMockServer() {
        if (mockAccountService != null) {
            mockAccountService.stop(0);
            mockAccountService = null;
        }
    }

    private static synchronized void ensureMockServer() {
        if (mockAccountService != null) {
            return;
        }
        try {
            mockAccountService = HttpServer.create(new InetSocketAddress(0), 0);
            mockAccountService.createContext("/accounts/acct-123/transactions", new TxnHandler());
            mockAccountService.start();
            int port = mockAccountService.getAddress().getPort();
            mockBaseUrl = "http://localhost:" + port;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start mock account service", e);
        }
    }

    private static class TxnHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            traceIdHeader.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            byte[] body = new byte[0];
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
