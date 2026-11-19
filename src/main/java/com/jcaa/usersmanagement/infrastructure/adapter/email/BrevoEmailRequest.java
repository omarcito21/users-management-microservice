package com.jcaa.usersmanagement.infrastructure.adapter.email;

import java.util.List;

record BrevoEmailRequest(
    Contact sender,
    List<Contact> to,
    String subject,
    String htmlContent) {

  record Contact(String email, String name) {}
}
