package com.cryptonex.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "trade_signals")
public class TradeSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne
    @JoinColumn(name = "post_id")
    private Post post;

    private String coin; // e.g., BTCUSDT

    private java.math.BigDecimal entryRangeMin;
    private java.math.BigDecimal entryRangeMax;

    @ElementCollection
    private List<java.math.BigDecimal> takeProfits;

    private java.math.BigDecimal stopLoss;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private Integer leverage; // Nullable for spot

    @Enumerated(EnumType.STRING)
    private RiskRating riskRating;

    @Enumerated(EnumType.STRING)
    private StrategyType strategyType;

    @Enumerated(EnumType.STRING)
    private SignalStatus status = SignalStatus.ACTIVE;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        openedAt = LocalDateTime.now();
    }

    public enum Direction {
        LONG, SHORT
    }

    public enum RiskRating {
        LOW, MEDIUM, HIGH, EXTREME
    }

    public enum StrategyType {
        SCALP, INTRADAY, SWING, POSITION
    }

    public enum SignalStatus {
        ACTIVE, HIT_TP, HIT_SL, CLOSED_MANUALLY
    }
}
