package com.cryptonex.service;

import com.cryptonex.domain.WalletTransactionType;
import com.cryptonex.model.Wallet;
import com.cryptonex.model.WalletTransaction;

import java.util.List;

public interface WalletTransactionService {
    WalletTransaction createTransaction(Wallet wallet,
            WalletTransactionType type,
            String transferId,
            String purpose,
            java.math.BigDecimal amount);

    List<WalletTransaction> getTransactions(Wallet wallet, WalletTransactionType type);

}
