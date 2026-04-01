package com.cryptonex.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "user_trades")
public class UserTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "trade_signal_id")
    private TradeSignal tradeSignal; // Nullable

    private String coin; // e.g., BTCUSDT

    @Enumerated(EnumType.STRING)
    private TradeSignal.Direction direction;

    private java.math.BigDecimal entryPrice;
    private LocalDateTime entryTime;

    private java.math.BigDecimal exitPrice; // Nullable until closed
    private LocalDateTime exitTime; // Nullable until closed

    private java.math.BigDecimal pnl; // Nullable until closed

    @Enumerated(EnumType.STRING)
    private TradeStatus status = TradeStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (entryTime == null) {
            entryTime = LocalDateTime.now();
        }
    }

    public enum TradeStatus {
        OPEN, CLOSED_TP, CLOSED_SL, CLOSED_MANUAL
    }
}
