package com.cryptonex.repository;

import com.cryptonex.domain.WalletTransactionType;
import com.cryptonex.model.Wallet;
import com.cryptonex.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction,Long> {

    List<WalletTransaction> findByWalletOrderByDateDesc(Wallet wallet);

}
