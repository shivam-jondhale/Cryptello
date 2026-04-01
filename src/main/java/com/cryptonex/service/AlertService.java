package com.cryptonex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    @Autowired
    private EmailService emailService;

    @Value("${spring.mail.username}")
    private String adminEmail; // Send alerts to the sender email itself for now, or a configured admin email

    public void logAlert(String message) {
        logger.error("[ALERT] {}", message);
    }

    public void sendCriticalAlert(String subject, String message) {
        logger.error("[CRITICAL ALERT] {}: {}", subject, message);
        try {
            // In a real app, you'd send to a dedicated admin list
            emailService.sendEmail(adminEmail, "CRITICAL ALERT: " + subject, message);
        } catch (Exception e) {
            logger.error("Failed to send alert email: {}", e.getMessage());
        }
    }
}
