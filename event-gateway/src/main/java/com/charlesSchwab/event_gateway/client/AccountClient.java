package com.charlesSchwab.event_gateway.client;

import com.charlesSchwab.event_gateway.exception.AccountServiceUnavailableException;
import com.charlesSchwab.event_gateway.model.TransactionRequest;
import com.charlesSchwab.event_gateway.service.BalanceResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private final Counter applySuccessCounter;
    private final Counter applyFailureCounter;
    private final Counter circuitOpenCounter;
    private final Counter balanceSuccessCounter;
    private final Counter balanceFailureCounter;
    private final Counter balanceCircuitOpenCounter;

    public AccountClient(RestTemplateBuilder builder,
                         @Value("${account-service.url}") String accountBaseUrl,
                         Tracer tracer,
                         MeterRegistry meterRegistry) {
        // Force the plain JDK HttpURLConnection-based factory explicitly instead of letting
        // RestTemplateBuilder auto-detect an HTTP client from the classpath. In tests,
        // wiremock-standalone bundles its own Jetty libraries onto the test classpath, which
        // can cause a different (and differently-behaved) client to be picked than what runs
        // in production -- this keeps behavior identical and avoids a client-specific
        // request-body serialization issue seen under that auto-detection.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        // Build from the autoconfigured builder so tracing instrumentation is still applied
        this.rest = builder.requestFactory(() -> requestFactory).build();
        this.accountBaseUrl = accountBaseUrl;
        this.tracer = tracer;
        this.applySuccessCounter = Counter.builder("gateway.account.apply.success")
                .description("Successful account-service apply calls")
                .register(meterRegistry);
        this.applyFailureCounter = Counter.builder("gateway.account.apply.failure")
                .description("Failed account-service apply calls")
                .register(meterRegistry);
        this.circuitOpenCounter = Counter.builder("gateway.account.apply.circuit_open")
                .description("Short-circuited account-service apply calls")
                .register(meterRegistry);
        this.balanceSuccessCounter = Counter.builder("gateway.account.balance.success")
                .description("Successful account-service balance lookups")
                .register(meterRegistry);
        this.balanceFailureCounter = Counter.builder("gateway.account.balance.failure")
                .description("Failed account-service balance lookups")
                .register(meterRegistry);
        this.balanceCircuitOpenCounter = Counter.builder("gateway.account.balance.circuit_open")
                .description("Short-circuited account-service balance lookups")
                .register(meterRegistry);
    }

    @Retry(name = "accountService", fallbackMethod = "applyTransactionFallback")
    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
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
            applySuccessCounter.increment();
            log.info("account_client.apply.ok accountId={} eventId={}", accountId, req.eventId());
        } catch (RestClientException ex) {
            applyFailureCounter.increment();
            log.error("account_client.apply.failed accountId={} eventId={} error={}",
                    accountId, req.eventId(), ex.getMessage());
            throw new AccountServiceUnavailableException("Account Service request failed", ex);
        }
    }

    @SuppressWarnings("unused")
    private void applyTransactionFallback(String accountId, TransactionRequest req, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            circuitOpenCounter.increment();
            log.warn("account_client.circuit_open accountId={} eventId={}", accountId, req.eventId());
            throw new AccountServiceUnavailableException("Account Service circuit breaker is open", throwable);
        }
        throw new AccountServiceUnavailableException("Account Service request failed", throwable);
    }

    // Account Service is internal-only (per architecture) -- the Gateway proxies balance
    // reads so clients never call it directly. Shares the same circuit breaker/retry
    // instance as the write path, since both are signals of the same downstream's health.
    @Retry(name = "accountService", fallbackMethod = "getBalanceFallback")
    @CircuitBreaker(name = "accountService", fallbackMethod = "getBalanceFallback")
    public BalanceResponse getBalance(String accountId) {
        String url = accountBaseUrl + "/accounts/" + accountId + "/balance";
        log.info("account_client.balance accountId={}", accountId);
        try {
            HttpHeaders headers = new HttpHeaders();
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                headers.add("X-Trace-Id", currentSpan.context().traceId());
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<BalanceResponse> response =
                    rest.exchange(url, HttpMethod.GET, entity, BalanceResponse.class);
            balanceSuccessCounter.increment();
            log.info("account_client.balance.ok accountId={}", accountId);
            return response.getBody();
        } catch (RestClientException ex) {
            balanceFailureCounter.increment();
            log.error("account_client.balance.failed accountId={} error={}", accountId, ex.getMessage());
            throw new AccountServiceUnavailableException("Account Service request failed", ex);
        }
    }

    @SuppressWarnings("unused")
    private BalanceResponse getBalanceFallback(String accountId, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            balanceCircuitOpenCounter.increment();
            log.warn("account_client.balance.circuit_open accountId={}", accountId);
            throw new AccountServiceUnavailableException("Account Service circuit breaker is open", throwable);
        }
        throw new AccountServiceUnavailableException("Account Service request failed", throwable);
    }
}