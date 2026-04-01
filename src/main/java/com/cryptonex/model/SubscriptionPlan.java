package com.cryptonex.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;
    private String description;
    private BigDecimal price;
    private int durationMonths;

    @Enumerated(EnumType.STRING)
    private com.cryptonex.domain.PlanType planType;

    @ManyToOne
    @JoinColumn(name = "trader_id")
    private User trader; // Null for platform plans

    private boolean isActive = true;
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    private com.cryptonex.domain.PaymentProvider providerType = com.cryptonex.domain.PaymentProvider.STRIPE;

    public int getDurationDays() {
        return durationMonths * 30;
    }
}
