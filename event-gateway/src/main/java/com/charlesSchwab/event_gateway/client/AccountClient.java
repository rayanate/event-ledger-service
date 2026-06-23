package com.charlesSchwab.event_gateway.client;

import com.charlesSchwab.event_gateway.exception.AccountServiceUnavailableException;
import com.charlesSchwab.event_gateway.model.TransactionRequest;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class AccountClient {
    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    private final RestTemplate rest;
    private final String accountBaseUrl;
    private final Tracer tracer;

    public AccountClient(RestTemplateBuilder builder,
                         @Value("${account-service.url}") String accountBaseUrl,
                         Tracer tracer) {
        // Build from the autoconfigured builder so tracing instrumentation is applied
        this.rest = builder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(3))
                .build();
        this.accountBaseUrl = accountBaseUrl;
        this.tracer = tracer;
    }

    public void applyTransaction(String accountId, TransactionRequest req) {
        String url = accountBaseUrl + "/accounts/" + accountId + "/transactions";
        log.info("account_client.apply accountId={} eventId={}", accountId, req.eventId());
        try {
            HttpHeaders headers = new HttpHeaders();
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                headers.add("X-Trace-Id", currentSpan.context().traceId());
            }
            HttpEntity<TransactionRequest> entity = new HttpEntity<>(req, headers);
            rest.postForEntity(url, entity, Void.class);
            log.info("account_client.apply.ok accountId={} eventId={}", accountId, req.eventId());
        } catch (RestClientException ex) {
            log.error("account_client.apply.failed accountId={} eventId={} error={}",
                    accountId, req.eventId(), ex.getMessage());
            throw new AccountServiceUnavailableException("Account Service request failed", ex);
        }
    }
}
