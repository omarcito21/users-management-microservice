package com.jcaa.usersmanagement.infrastructure.adapter.email;

import java.time.Duration;
import java.util.Objects;

public record BrevoConfig(
    String baseUrl,
    String apiKey,
    String fromAddress,
    String fromName,
    Duration connectTimeout,
    Duration readTimeout,
    int retryMaxAttempts,
    Duration retryInitialDelay,
    double retryMultiplier,
    int circuitFailureThreshold,
    int circuitSlidingWindowSize,
    int circuitMinimumCalls,
    Duration circuitOpenDuration) {

  public BrevoConfig {
    requireText(baseUrl, "BREVO_BASE_URL");
    requireText(apiKey, "BREVO_API_KEY");
    requireText(fromAddress, "BREVO_FROM_ADDRESS");
    if (!apiKey.startsWith("xkeysib-")) {
      throw new IllegalArgumentException(
          "BREVO_API_KEY debe ser una clave API REST de Brevo con prefijo 'xkeysib-'.");
    }
    fromName = fromName == null || fromName.isBlank() ? "Gestion de Usuarios" : fromName;
    connectTimeout = requirePositive(connectTimeout, "BREVO_CONNECT_TIMEOUT");
    readTimeout = requirePositive(readTimeout, "BREVO_READ_TIMEOUT");
    retryInitialDelay = requirePositive(retryInitialDelay, "BREVO_RETRY_INITIAL_DELAY");
    circuitOpenDuration = requirePositive(circuitOpenDuration, "BREVO_CIRCUIT_OPEN_DURATION");
    if (retryMaxAttempts < 1) {
      throw new IllegalArgumentException("BREVO_RETRY_MAX_ATTEMPTS debe ser al menos 1.");
    }
    if (retryMultiplier < 1.0) {
      throw new IllegalArgumentException("BREVO_RETRY_MULTIPLIER debe ser al menos 1.0.");
    }
    if (circuitFailureThreshold < 1 || circuitFailureThreshold > 100) {
      throw new IllegalArgumentException("BREVO_CIRCUIT_FAILURE_THRESHOLD debe estar entre 1 y 100.");
    }
    if (circuitSlidingWindowSize < 2
        || circuitMinimumCalls < 1
        || circuitMinimumCalls > circuitSlidingWindowSize) {
      throw new IllegalArgumentException("La ventana y el mínimo de llamadas del Circuit Breaker no son válidos.");
    }
  }

  private static void requireText(final String value, final String propertyName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("La configuración requerida '" + propertyName + "' no puede estar vacía.");
    }
  }

  private static Duration requirePositive(final Duration value, final String propertyName) {
    Objects.requireNonNull(value, propertyName + " es obligatorio.");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(propertyName + " debe ser mayor que cero.");
    }
    return value;
  }
}
