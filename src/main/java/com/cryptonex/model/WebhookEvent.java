package com.cryptonex.model;

import com.cryptonex.domain.PaymentProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "provider", "event_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProvider provider;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "event_type", length = 128)
    private String eventType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(nullable = false, length = 32)
    private String status = "PENDING"; // PENDING, PROCESSING, SUCCESS, FAILED, MANUAL_REVIEW

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "created_by", length = 64)
    private String createdBy;
}
