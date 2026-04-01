package com.cryptonex.service;

import com.cryptonex.model.User;
import com.cryptonex.model.Withdrawal;

import lombok.With;

import java.util.List;

public interface WithdrawalService {

    Withdrawal requestWithdrawal(java.math.BigDecimal amount, User user);

    Withdrawal procedWithdrawal(Long withdrawalId, boolean accept) throws Exception;

    List<Withdrawal> getUsersWithdrawalHistory(User user);

    List<Withdrawal> getAllWithdrawalRequest();
}
