package com.cloud.notification.consumer;

import com.cloud.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileEventConsumer {

    private final EmailService emailService;

    // This method would be annotated with @KafkaListener or @RabbitListener in a real implementation
    public void consumeFileUploadedEvent(String message) {
        log.info("Received file uploaded event: {}", message);
        emailService.sendEmail("user@example.com", "File Uploaded", "Your file has been uploaded successfully.");
    }
}
