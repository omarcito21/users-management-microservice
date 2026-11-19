package com.jcaa.usersmanagement.domain.exception;

public final class EmailSenderException extends DomainException {

  private static final String DEFAULT_MESSAGE = "La notificación por correo no pudo ser enviada.";
  private static final String MESSAGE_WITH_DETAIL =
      "No se pudo enviar el correo a '%s'. Error SMTP: %s";

  public EmailSenderException(final String message) {
    super(message);
  }

  public EmailSenderException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static EmailSenderException becauseSmtpFailed(
      final String destinationEmail, final String smtpError) {
    return new EmailSenderException(
        String.format(MESSAGE_WITH_DETAIL, destinationEmail, smtpError));
  }

  public static EmailSenderException becauseApiFailed(
      final String destinationEmail, final String apiError) {
    return new EmailSenderException(
        String.format("No se pudo enviar el correo a '%s'. Error en la API de Brevo: %s", destinationEmail, apiError));
  }

  public static EmailSenderException becauseSendFailed(final Throwable cause) {
    return new EmailSenderException(DEFAULT_MESSAGE, cause);
  }
}
