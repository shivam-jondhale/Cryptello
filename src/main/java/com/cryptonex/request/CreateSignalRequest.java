package com.cryptonex.request;

import com.cryptonex.model.TradeSignal;
import lombok.Data;
import java.util.List;

@Data
public class CreateSignalRequest {
    private String caption;
    private String coin;
    private java.math.BigDecimal entryRangeMin;
    private java.math.BigDecimal entryRangeMax;
    private List<java.math.BigDecimal> takeProfits;
    private java.math.BigDecimal stopLoss;
    private TradeSignal.Direction direction; // LONG/SHORT
    private int leverage;
    private TradeSignal.RiskRating riskRating; // LOW/MEDIUM/HIGH
    private TradeSignal.StrategyType strategyType; // SCALP/SWING
}
