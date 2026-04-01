package com.cryptonex.model;

import com.cryptonex.domain.PaymentMethod;
import com.cryptonex.domain.PaymentOrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = { "provider", "provider_order_id" })
})
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private java.math.BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentOrderStatus status = PaymentOrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @ManyToOne
    private User user;

    @ManyToOne
    private SubscriptionPlan plan; // Nullable

    private String purpose; // e.g., "PLATFORM_SUBSCRIPTION"

    @Column(name = "currency", length = 8)
    private String currency; // "INR" or "USD"

    @Column(name = "provider", length = 32)
    @Enumerated(EnumType.STRING)
    private com.cryptonex.domain.PaymentProvider provider; // STRIPE, RAZORPAY, CASHFREE

    @Column(name = "provider_order_id", length = 128)
    private String providerOrderId; // Cashfree order ID / Stripe session ID

    @Column(name = "provider_payment_link_url", length = 1024)
    private String providerPaymentLinkUrl;

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId; // e.g., stripe payment_intent id, cashfree referenceId

    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
}
