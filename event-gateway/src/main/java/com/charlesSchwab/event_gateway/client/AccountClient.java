package com.charlesSchwab.event_gateway.client;

import com.charlesSchwab.account_service.dto.TransactionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class AccountClient {
    private final RestTemplate rest;
    private final String accountBaseUrl;

    public AccountClient(RestTemplateBuilder builder,
                         @Value("${account-service.url}") String accountBaseUrl) {
        // Build from the autoconfigured builder so tracing instrumentation is applied
        this.rest = builder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(3))
                .build();
        this.accountBaseUrl = accountBaseUrl;
    }

    public void applyTransaction(String accountId, TransactionRequest req) {
        String url = accountBaseUrl + "/accounts/" + accountId + "/transactions";
        rest.postForEntity(url, req, Void.class);
    }
}
