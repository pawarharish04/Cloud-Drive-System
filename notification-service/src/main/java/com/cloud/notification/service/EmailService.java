package com.cloud.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    public void sendEmail(String to, String subject, String body) {
        // Implement email sending logic here
        // For now, just log it
        log.info("Sending email to: {}, Subject: {}, Body: {}", to, subject, body);
    }
}
