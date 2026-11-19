package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BrevoEmailSenderAdapterTest {

  private static final String API_URL = "https://api.brevo.com/v3/smtp/email";
  private static final EmailDestinationModel DESTINATION =
      new EmailDestinationModel(
          "user@example.com", "Usuario de prueba", "Alta", "<p>Bienvenido</p>");

  @Test
  void shouldSendEmailWithBrevoApiPayloadAndAuthenticationHeader() {
    final RestClient.Builder builder = RestClient.builder()
        .baseUrl("https://api.brevo.com")
        .defaultHeader("api-key", "xkeysib-test-key");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(API_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("api-key", "xkeysib-test-key"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json(
            """
            {
              "sender": {"email": "sender@example.com", "name": "User Service"},
              "to": [{"email": "user@example.com", "name": "Usuario de prueba"}],
              "subject": "Alta",
              "htmlContent": "<p>Bienvenido</p>"
            }
            """))
        .andRespond(withSuccess());

    final BrevoEmailSenderAdapter adapter = new BrevoEmailSenderAdapter(config(3, 10, 5), builder.build());
    assertDoesNotThrow(() -> adapter.send(DESTINATION));
    server.verify();
  }

  @Test
  void shouldRetryTransientServerFailuresAndThenSucceed() {
    final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(API_URL)).andRespond(withServerError());
    server.expect(requestTo(API_URL)).andRespond(withSuccess());

    final BrevoEmailSenderAdapter adapter = new BrevoEmailSenderAdapter(config(2, 10, 5), builder.build());
    assertDoesNotThrow(() -> adapter.send(DESTINATION));
    server.verify();
  }

  @Test
  void shouldNotRetryPermanentClientErrors() {
    final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(API_URL))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{}"));

    final BrevoEmailSenderAdapter adapter = new BrevoEmailSenderAdapter(config(3, 10, 5), builder.build());
    final EmailSenderException error = assertThrows(EmailSenderException.class, () -> adapter.send(DESTINATION));
    assertThat(error.getMessage()).contains("HTTP 400");
    server.verify();
  }

  @Test
  void shouldOpenCircuitAfterConfiguredTransientFailures() {
    final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(API_URL)).andRespond(withServerError());
    server.expect(requestTo(API_URL)).andRespond(withServerError());

    final BrevoEmailSenderAdapter adapter = new BrevoEmailSenderAdapter(config(1, 2, 2), builder.build());
    assertThrows(EmailSenderException.class, () -> adapter.send(DESTINATION));
    assertThrows(EmailSenderException.class, () -> adapter.send(DESTINATION));
    final EmailSenderException circuitError =
        assertThrows(EmailSenderException.class, () -> adapter.send(DESTINATION));
    assertThat(circuitError.getMessage()).contains("Circuit Breaker abierto");
    server.verify();
  }

  private static BrevoConfig config(
      final int attempts, final int windowSize, final int minimumCalls) {
    return new BrevoConfig(
        "https://api.brevo.com",
        "xkeysib-test-key",
        "sender@example.com",
        "User Service",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        attempts,
        Duration.ofMillis(1),
        2.0,
        50,
        windowSize,
        minimumCalls,
        Duration.ofSeconds(30));
  }
}
