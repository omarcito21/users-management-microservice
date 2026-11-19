package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "notification.provider", havingValue = "brevo")
public class BrevoEmailSenderAdapter implements EmailSenderPort {

  private static final String SEND_EMAIL_PATH = "/v3/smtp/email";
  private final BrevoConfig config;
  private final RestClient restClient;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;

  public BrevoEmailSenderAdapter(final BrevoConfig config, final RestClient brevoRestClient) {
    this.config = config;
    this.restClient = brevoRestClient;
    this.retry = buildRetry(config);
    this.circuitBreaker = buildCircuitBreaker(config);
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    final BrevoEmailRequest request = buildRequest(destination);
    final Supplier<Void> call =
        () -> {
          restClient.post().uri(SEND_EMAIL_PATH).body(request).retrieve().toBodilessEntity();
          return null;
        };
    try {
      Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call)).get();
    } catch (final RestClientResponseException exception) {
      throw EmailSenderException.becauseApiFailed(
          destination.getDestinationEmail(), "HTTP " + exception.getStatusCode().value());
    } catch (final CallNotPermittedException exception) {
      throw EmailSenderException.becauseApiFailed(
          destination.getDestinationEmail(), "Circuit Breaker abierto");
    } catch (final RestClientException exception) {
      throw EmailSenderException.becauseApiFailed(
          destination.getDestinationEmail(), exception.getClass().getSimpleName());
    }
  }

  private BrevoEmailRequest buildRequest(final EmailDestinationModel destination) {
    return new BrevoEmailRequest(
        new BrevoEmailRequest.Contact(config.fromAddress(), config.fromName()),
        List.of(
            new BrevoEmailRequest.Contact(
                destination.getDestinationEmail(), destination.getDestinationName())),
        destination.getSubject(),
        destination.getBody());
  }

  private static Retry buildRetry(final BrevoConfig config) {
    final IntervalFunction backoff =
        IntervalFunction.ofExponentialBackoff(
            config.retryInitialDelay().toMillis(), config.retryMultiplier());
    final RetryConfig retryConfig =
        RetryConfig.custom()
            .maxAttempts(config.retryMaxAttempts())
            .intervalFunction(backoff)
            .retryOnException(BrevoEmailSenderAdapter::isTransientFailure)
            .build();
    return Retry.of("brevo-email", retryConfig);
  }

  private static CircuitBreaker buildCircuitBreaker(final BrevoConfig config) {
    final CircuitBreakerConfig circuitBreakerConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(config.circuitFailureThreshold())
            .slidingWindowSize(config.circuitSlidingWindowSize())
            .minimumNumberOfCalls(config.circuitMinimumCalls())
            .waitDurationInOpenState(config.circuitOpenDuration())
            .recordException(BrevoEmailSenderAdapter::isTransientFailure)
            .build();
    return CircuitBreaker.of("brevo-email", circuitBreakerConfig);
  }

  private static boolean isTransientFailure(final Throwable exception) {
    return exception instanceof ResourceAccessException
        || exception instanceof HttpServerErrorException;
  }
}
